# Zify 外部 LLM API 调用技术方案

> Zify 需要调用 OpenAI、Anthropic、DeepSeek、智谱等多个 LLM API，这些调用慢（单次 5-30 秒）且不稳定（网络抖动、限流、服务降级）。
> 本方案从线程管理、超时、重试、容错四个维度给出完整设计。

---

## 一、整体架构

```
Controller (SSE)
    ↓
EngineService
    ↓
LlmClient（统一封装）
    ├── OpenAiProvider    → OpenAI / DeepSeek / 智谱（兼容 OpenAI 协议）
    ├── AnthropicProvider → Anthropic
    └── OllamaProvider    → Ollama（本地模型）
```

调用链路：

```
用户发消息 → EngineController (SSE) → ReactEngine → LlmClient.chat()
                                                          ↓
                                                   选 Provider → HTTP 请求 → LLM API
                                                          ↓
                                                   超时 / 重试 / 降级
```

所有外部 LLM 调用统一收敛到 `engine` 模块的 `infrastructure/client/` 下，其他模块不直接调用 LLM API。

---

## 二、线程管理

### 2.1 选型：Virtual Threads + RestClient

Java 21 的 Virtual Threads 天然适合 IO 密集场景——LLM 调用大部分时间都在等网络响应，虚拟线程在等待期间不占用平台线程，一个 JVM 可以同时挂起上万个虚拟线程。

HTTP 客户端选择 Spring Boot 4.0 的 **RestClient**（同步 API + 虚拟线程），不用 WebClient（响应式 API 复杂度高，收益不大）。

### 2.2 配置

```yaml
# application.yml
spring:
  threads:
    virtual:
      enabled: true   # 开启虚拟线程，Tomcat 请求处理、@Async 都走虚拟线程
```

```java
@Configuration
public class LlmClientConfig {

    @Bean
    public RestClient llmRestClient() {
        HttpClient httpClient = HttpClient.newBuilder()
                .executor(Executors.newVirtualThreadPerTaskExecutor())  // 虚拟线程执行器
                .connectTimeout(Duration.ofSeconds(10))                 // 连接超时
                .build();

        return RestClient.builder()
                .httpAdapter(new JdkClientHttpAdapter(httpClient))
                .build();
    }
}
```

### 2.3 流式响应

Agent 的 ReAct 循环中，LLM 响应是流式返回的（SSE）。用 SseEmitter 挂在虚拟线程上：

```java
@GetMapping(value = "/api/engine/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
public SseEmitter chatStream(@RequestBody ChatRequest request) {
    SseEmitter emitter = new SseEmitter(120_000L);  // 2 分钟超时

    // 虚拟线程中执行，不阻塞 Tomcat 线程
    Thread.startVirtualThread(() -> {
        try {
            engineFacade.chatStream(request, chunk -> {
                emitter.send(SseEmitter.event().data(chunk));
            });
            emitter.complete();
        } catch (Exception e) {
            emitter.completeWithError(e);
        }
    });

    return emitter;
}
```

### 2.4 为什么不用 WebClient

| | RestClient + Virtual Threads | WebClient (Reactive) |
|--|--|--|
| 代码风格 | 同步，顺序写，好读好调 | 响应式，链式调用，调试难 |
| 线程模型 | 虚拟线程，等待时不占平台线程 | 事件循环，不占线程但代码复杂 |
| 一个人维护 | ✅ 简单直观 | ❌ 学习成本高，排错难 |
| 性能 | 50 人绑绰有余，千人也没问题 | 高并发下略有优势 |

**结论**：一个人开发，同步代码比响应式代码维护成本低得多，Virtual Threads 已经解决了线程占用问题。

---

## 三、超时策略

### 3.1 三级超时

| 超时类型 | 时长 | 说明 |
|---------|------|------|
| 连接超时 | 10 秒 | TCP 握手，国内访问海外 API 可能慢，给 10 秒 |
| 读取超时（首 token） | 30 秒 | 从发出请求到收到第一个 token，模型思考阶段可能较久 |
| 读取超时（总时长） | 120 秒 | 流式场景从发出请求到最后一个 token，SseEmitter 超时同步设为 120 秒 |

### 3.2 按场景差异化

不同调用场景对超时的容忍度不同：

| 场景 | 首 token 超时 | 总超时 | 说明 |
|------|-------------|--------|------|
| Agent 对话（流式） | 30 秒 | 120 秒 | 用户在等，但能看到 token 逐个出来 |
| 工作流节点（LLM 节点） | 60 秒 | 180 秒 | 工作流中 LLM 可能做复杂任务，给更长时间 |
| Embedding 向量化 | 30 秒 | 60 秒 | 通常很快 |
| 模型连通性测试 | 10 秒 | 15 秒 | 只测能不能通，快速失败 |

### 3.3 实现

通过给不同场景的 RestClient 设置不同超时：

```java
@Configuration
public class LlmClientConfig {

    // Agent 对话用
    @Bean("chatRestClient")
    public RestClient chatRestClient() {
        HttpClient httpClient = HttpClient.newBuilder()
                .executor(Executors.newVirtualThreadPerTaskExecutor())
                .connectTimeout(Duration.ofSeconds(10))
                .build();

        return RestClient.builder()
                .httpAdapter(new JdkClientHttpAdapter(httpClient))
                .requestInterceptor((request, body, execution) -> {
                    // 读取超时通过 JDK HttpClient 的 response timeout 控制
                    return execution.execute(request, body);
                })
                .build();
    }

    // 工作流 LLM 节点用（超时更长）
    @Bean("workflowRestClient")
    public RestClient workflowRestClient() {
        // 类似配置，超时参数不同
    }
}
```

---

## 四、重试策略

### 4.1 什么时候重试

| 错误类型 | 是否重试 | 说明 |
|---------|---------|------|
| 429 Too Many Requests | ✅ 重试 | 限流，等一段时间后重试 |
| 500 / 502 / 503 服务器错误 | ✅ 重试 | 对方服务临时故障 |
| 网络超时 | ✅ 重试 | 网络抖动 |
| 401 / 403 认证错误 | ❌ 不重试 | API Key 无效，重试没用 |
| 400 Bad Request | ❌ 不重试 | 请求本身有问题，重试没用 |
| 流式响应已开始发送 | ❌ 不重试 | 已经给用户发了部分内容，重试会导致内容重复 |

### 4.2 重试参数

| 参数 | 值 | 说明 |
|------|-----|------|
| 最大重试次数 | 2 | 加上首次请求，最多 3 次 |
| 重试间隔 | 指数退避 | 第 1 次 1 秒，第 2 次 2 秒 |
| 429 特殊处理 | 读取 `Retry-After` 头 | 如果对方返回了等待时间，按那个时间等 |

### 4.3 实现

用 Spring Retry（Spring Boot 4 内置），不引入 Resilience4j：

```java
@Service
@RequiredArgsConstructor
public class LlmClient {

    private final RestClient chatRestClient;

    /**
     * 非流式调用（工作流 LLM 节点、Embedding 等）
     */
    @Retryable(
        retryFor = {LlmRetryableException.class},
        maxAttempts = 3,
        backoff = @Backoff(delay = 1000, multiplier = 2)
    )
    public LlmResponse chat(LlmRequest request) {
        try {
            return chatRestClient.post()
                    .uri(request.getUrl())
                    .header("Authorization", "Bearer " + request.getApiKey())
                    .body(request.getBody())
                    .retrieve()
                    .body(LlmResponse.class);
        } catch (HttpClientErrorException e) {
            if (e.getStatusCode().value() == 429) {
                throw new LlmRetryableException("Rate limited", e);
            }
            if (e.getStatusCode().is5xxServerError()) {
                throw new LlmRetryableException("Server error: " + e.getStatusCode(), e);
            }
            throw new LlmException("Client error: " + e.getStatusCode(), e);  // 4xx 不重试
        } catch (ResourceAccessException e) {
            throw new LlmRetryableException("Timeout or network error", e);  // 超时重试
        }
    }

    /**
     * 流式调用（Agent 对话）
     * 流式调用不自动重试，由调用方（ReactEngine）在 ReAct 循环中处理
     */
    public void chatStream(LlmRequest request, Consumer<String> onChunk) {
        // 流式请求通过 SSE 接收，逐 chunk 回调
        // 不加 @Retryable，因为流一旦开始就不能重试
    }
}
```

### 4.4 流式调用的容错

流式调用不能自动重试（已经开始发内容了），由上层 ReactEngine 处理：

```
ReactEngine 的 ReAct 循环：
  第 1 轮调用 LLM → 流式中断（网络错误）
  → 记录错误日志
  → 把已收到的部分内容 + 错误信息返回给用户
  → 用户可以点击"继续"触发下一轮
```

---

## 五、容错策略

### 5.1 降级：Provider 故障自动切换

当主 Provider 连续失败时，自动切换到备用 Provider。适用于配置了负载均衡的场景。

```java
@Service
@RequiredArgsConstructor
public class LlmClient {

    private final ModelFacade modelFacade;

    public LlmResponse chat(LlmRequest request) {
        List<ProviderConfig> providers = modelFacade.getAvailableProviders(request.getModelName());

        for (ProviderConfig provider : providers) {
            try {
                return callProvider(provider, request);
            } catch (LlmRetryableException e) {
                log.warn("Provider {} failed: {}", provider.getName(), e.getMessage());
                continue;  // 试下一个 Provider
            }
        }
        throw new LlmException("All providers failed");
    }
}
```

### 5.2 熔断：快速失败保护系统

当某个 Provider 连续失败 N 次后，短时间内不再请求它，避免拖慢整个系统。

一期用简单的内存计数器实现，不引入额外框架：

```java
@Component
public class ProviderCircuitBreaker {

    private final ConcurrentHashMap<String, AtomicIntegerFieldState> failureCounts = new ConcurrentHashMap<>();
    private static final int FAILURE_THRESHOLD = 5;    // 连续失败 5 次触发熔断
    private static final Duration COOLDOWN = Duration.ofMinutes(1);  // 熔断冷却 1 分钟

    public boolean isAvailable(String providerId) {
        AtomicIntegerFieldState state = failureCounts.get(providerId);
        if (state == null) return true;
        if (state.failures >= FAILURE_THRESHOLD) {
            return Duration.between(state.lastFailureTime, Instant.now()).compareTo(COOLDOWN) > 0;
        }
        return true;
    }

    public void recordSuccess(String providerId) {
        failureCounts.remove(providerId);  // 成功一次就清除计数
    }

    public void recordFailure(String providerId) {
        failureCounts.compute(providerId, (k, state) -> {
            if (state == null) return new State(1, Instant.now());
            return new State(state.failures + 1, Instant.now());
        });
    }
}
```

### 5.3 超时兜底

即使重试全部失败，也要保证不无限等待：

```
用户发消息
  → SseEmitter 超时 120 秒
  → 单次 LLM 调用超时 30 秒
  → 重试最多 2 次
  → 总耗时上限 = 120 秒（由 SseEmitter 兜底）

超过 120 秒后，SseEmitter 自动断开，用户看到"响应超时，请重试"
```

---

## 六、异常体系

```
LlmException                    # 所有 LLM 调用异常的基类（RuntimeException）
├── LlmRetryableException       # 可重试的异常（429、5xx、超时）
└── LlmNonRetryableException    # 不可重试的异常（401、400）
```

异常统一在 `engine` 模块的 `infrastructure/client/` 下定义，不扩散到其他模块。

---

## 七、监控与日志

每次 LLM 调用记录以下信息（slf4j + JSON 格式）：

```json
{
  "event": "llm_call",
  "provider": "openai",
  "model": "gpt-4o",
  "action": "chat",
  "inputTokens": 1200,
  "outputTokens": 350,
  "durationMs": 3200,
  "status": "success",
  "retries": 0
}
```

失败时额外记录：

```json
{
  "event": "llm_call",
  "provider": "openai",
  "model": "gpt-4o",
  "action": "chat",
  "status": "failed",
  "errorCode": 429,
  "errorMessage": "Rate limited",
  "retries": 2,
  "durationMs": 6500
}
```

一期用日志文件即可，二期需要时可接入 Langfuse 等可观测平台。

---

## 八、总结

| 维度 | 方案 | 核心组件 |
|------|------|---------|
| 线程管理 | Virtual Threads + RestClient（同步） | JDK HttpClient + 虚拟线程执行器 |
| 超时 | 三级超时（连接 10s / 首token 30s / 总时长 120s），按场景差异化 | JDK HttpClient 超时配置 + SseEmitter 超时兜底 |
| 重试 | Spring Retry，指数退避，最多 2 次；429/5xx 重试，4xx 不重试；流式不自动重试 | `@Retryable` + 自定义 `LlmRetryableException` |
| 容错 | Provider 故障自动切换 + 内存熔断计数器 + SseEmitter 超时兜底 | `ProviderCircuitBreaker` + Provider 列表轮询 |
