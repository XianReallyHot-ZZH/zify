# Zify 外部 LLM API 调用技术方案

> Zify 需要调用 OpenAI、Anthropic、DeepSeek、智谱、Ollama 等模型服务。外部 LLM API 调用通常耗时 5-30 秒，并且会遇到网络抖动、限流、超时、服务端错误等问题。
> 本方案从线程管理、超时、重试、容错四个维度定义一期可落地方案。

---

## 一、适用范围

本文覆盖以下外部模型调用：

- Agent 对话中的流式 Chat Completion。
- 工作流 LLM 节点的非流式或流式调用。
- 知识库文档向量化的 Embedding 调用。
- 模型管理页面的 Provider 连通性测试。

不覆盖：

- HTTP 工具调用。
- MCP Server 调用。
- 工作流 HTTP Request 节点调用。

这些外部 HTTP 调用后续按类似原则单独定义。

---

## 二、模块边界

外部模型 Provider 调用统一归口到 `model` 模块，不放在 `engine` 模块。

原因：

- `engine` 需要调用 LLM 做 Agent ReAct。
- `workflow` 需要调用 LLM 执行 LLM 节点。
- `knowledge` 需要调用 Embedding 模型做向量化。
- `model` 本身需要做 Provider 连通性测试。

因此模型调用能力属于 `model` 模块的基础能力，其他模块通过 `ModelFacade` 调用。

```text
zify-engine / zify-workflow / zify-knowledge（通过 Maven 依赖 zify-model）
        ↓
zify-model 子模块内 com.zify.model.api.ModelFacade
        ↓
zify-model 子模块内 com.zify.model.domain.ModelService
        ↓
zify-model 子模块内 com.zify.model.infrastructure.client.LlmGateway
        ├── OpenAiCompatibleProviderClient  -> OpenAI / DeepSeek / 智谱 / Ollama
        └── AnthropicProviderClient         -> Anthropic
```

硬性规则：

- 只有 `zify-model` 子模块内的 `infrastructure/client/` 可以直接访问外部 LLM API。Maven 编译时边界确保其他子模块无法访问这些类。
- `engine`、`workflow`、`knowledge` 禁止直接创建 OpenAI / Anthropic / DeepSeek HTTP Client。
- `engine`、`workflow`、`knowledge` 只能通过 `ModelFacade` 发起模型调用。
- Provider API Key 只在 `model` 模块内读取和使用，不返回给其他模块。

---

## 三、线程管理

### 3.1 选型

一期使用：

- Java 21 Virtual Threads。
- Spring `RestClient` 同步 HTTP API。
- Spring 管理的虚拟线程执行器。

不使用 WebClient。原因是 Zify 一期由一个人开发，同步代码更容易调试和维护；Virtual Threads 已经能解决阻塞式 IO 的线程占用问题。

> **一致性说明（P1 起）**：LLM 流式对话调用采用 Spring AI（见 `docs-prompt/prompt-04`）。Spring AI 的 `stream()` 内部为 reactive（WebClient）传输，此时在虚拟线程上用阻塞桥接（`CountDownLatch.await`）适配 `TextStreamSink`，取消上游 = `dispose` 订阅。因此本节「不用 WebClient、用 RestClient 同步」**仅适用于 Spring AI 之外的直接外部调用**（HTTP 工具、MCP、Provider 连通性测试等）。§4 超时 / §5 重试 / 并发保护 / §七 异常分类 对两类调用都适用。

### 3.2 基础配置

```yaml
spring:
  threads:
    virtual:
      enabled: true
```

为 LLM 流式任务单独声明 Spring 管理的虚拟线程执行器，禁止在 Controller 中直接 `Thread.startVirtualThread()`。

```java
@Configuration
// 位于 zify-model 子模块的 com.zify.model.config 包下
public class LlmThreadConfig {

    @Bean(destroyMethod = "close")
    public ExecutorService llmTaskExecutor() {
        return Executors.newVirtualThreadPerTaskExecutor();
    }
}
```

### 3.3 RestClient 配置

```java
@Configuration
public class LlmRestClientConfig {

    @Bean
    public RestClient llmRestClient(RestClient.Builder builder) {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();

        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(Duration.ofSeconds(45));

        return builder
                .requestFactory(requestFactory)
                .build();
    }
}
```

说明：

- `connectTimeout` 只控制 TCP/TLS 建连超时。
- `readTimeout` 控制一次读取等待时间，可作为 chunk 间 idle timeout 的底层兜底。
- 首 token 超时和总调用超时不能只靠 RestClient 配置，需要由调用执行器按 deadline 显式控制。

### 3.4 SSE 流式响应线程模型

Controller 只创建 `SseEmitter` 并把任务交给 Service，不直接调用 LLM。Zify 一期流式端点位于 `chat` 模块（依赖方向 `chat → engine`，持久化归 `chat`），实际契约为 `GET /api/chat/stream?messageId=...`（见 `06-zify-code-organization.md` §11.7）；下方为线程模型示意片段。

```java
@GetMapping(value = "/api/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
public SseEmitter chatStream(@RequestParam String messageId) {
    SseEmitter emitter = new SseEmitter(120_000L);
    chatService.startChatStream(messageId, emitter);
    return emitter;
}
```

Service 中提交虚拟线程任务，并绑定取消逻辑（`chat` 加载历史后调 `EngineFacade` 执行编排，`engine` 再调 `ModelFacade.chatStream`）：

```java
@Service
@RequiredArgsConstructor
public class ChatService {

    private final ExecutorService llmTaskExecutor;
    private final EngineFacade engineFacade;

    public void startChatStream(String messageId, SseEmitter emitter) {
        Future<?> future = llmTaskExecutor.submit(() -> runTurn(messageId, emitter));

        emitter.onCompletion(() -> future.cancel(true));
        emitter.onTimeout(() -> future.cancel(true));
        emitter.onError(error -> future.cancel(true));
    }
}
```

硬性规则：

- Controller 不直接开线程。
- 用户断开、SSE 超时、SSE 发送失败时，必须取消上游 LLM 调用。
- 流式调用的 Provider Client 必须定期检查 `Thread.currentThread().isInterrupted()`，发现中断后停止读取响应流。

### 3.5 并发控制

Virtual Threads 不能替代下游并发保护。每个 Provider 必须设置最大并发，避免把限流和连接压力直接打到模型服务。

```yaml
zify:
  llm:
    provider-defaults:
      max-concurrent: 20
      acquire-timeout: 2s
```

调用 Provider 前必须先获取并发许可：

```java
public final class ProviderBulkhead {
    private final Semaphore semaphore;
    private final Duration acquireTimeout;

    public <T> T execute(Callable<T> task) {
        boolean acquired;
        try {
            acquired = semaphore.tryAcquire(acquireTimeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new LlmCancelledException("Interrupted while waiting for LLM provider permit", e);
        }
        if (!acquired) {
            throw new LlmBusyException("LLM provider is busy");
        }
        try {
            return task.call();
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new LlmException("LLM provider call failed", e);
        } finally {
            semaphore.release();
        }
    }
}
```

---

## 四、超时策略

### 4.1 超时类型

| 超时类型 | 默认值 | 适用场景 | 说明 |
|---|---:|---|---|
| 连接超时 | 10 秒 | 所有调用 | TCP/TLS 建连超时 |
| 首 token 超时 | 30 秒 | 流式 Chat | 从发出请求到收到第一个 chunk |
| idle 超时 | 45 秒 | 流式 Chat | 两个 chunk 之间的最大等待时间 |
| 总调用超时 | 120 秒 | Agent 对话 | 从发起调用到结束的总 deadline |
| 单次非流式超时 | 60 秒 | Embedding / 非流式 LLM | 单次 HTTP 请求总时长 |

### 4.2 按场景配置

```yaml
zify:
  llm:
    timeout:
      chat-stream:
        connect: 10s
        first-token: 30s
        idle: 45s
        total: 120s
      workflow-llm:
        connect: 10s
        first-token: 60s
        idle: 60s
        total: 180s
      embedding:
        connect: 10s
        request: 60s
      provider-test:
        connect: 5s
        request: 15s
```

### 4.3 执行规则

- 每次调用都必须创建 `deadline = now + totalTimeout`。
- 每次重试前都要检查剩余时间，剩余时间不足时直接失败，不再重试。
- 流式调用在收到第一个 chunk 前触发首 token 超时，可以重试。
- 流式调用已收到第一个 chunk 后触发 idle 超时或总超时，不自动重试，只向前端发送错误事件并结束流。
- `SseEmitter` 超时只是前端连接兜底，不是上游 LLM 调用超时。上游调用必须有自己的 deadline 和取消逻辑。

---

## 五、重试策略

### 5.1 不使用 `@Retryable`

一期不使用 `@Retryable` 做 LLM 调用重试。

原因：

- 需要读取 Provider 返回的 `Retry-After`。
- 需要加入 jitter，避免多个请求同时重试。
- 需要遵守总 deadline。
- 流式调用需要区分“首 chunk 前”和“首 chunk 后”。

因此使用显式 retry wrapper。

### 5.2 可重试条件

| 错误类型 | 是否重试 | 说明 |
|---|---|---|
| 429 Too Many Requests | 是 | 优先使用 `Retry-After` |
| 500 / 502 / 503 / 504 | 是 | Provider 临时错误 |
| 连接超时 | 是 | 还没有产生输出 |
| 首 token 超时 | 是 | 流式还没给用户发送内容 |
| idle 超时 | 否 | 流式通常已经开始输出 |
| 400 / 401 / 403 / 404 / 422 | 否 | 请求参数、认证或资源配置错误 |
| context length exceeded | 否 | 输入过长，重试无效 |
| 用户取消 / SSE 断开 | 否 | 主动取消，不重试 |

### 5.3 重试参数

| 参数 | 默认值 |
|---|---:|
| 最大重试次数 | 2 |
| 最大总尝试次数 | 3 |
| 初始退避 | 1 秒 |
| 退避倍率 | 2 |
| jitter | 20% |
| 单次最大等待 | 10 秒 |
| `Retry-After` 最大采纳值 | 30 秒 |

### 5.4 显式 retry wrapper

```java
public LlmResponse executeWithRetry(LlmCall call, LlmCallPolicy policy) {
    int attempt = 0;
    Instant deadline = Instant.now().plus(policy.totalTimeout());
    LlmException lastError = null;

    while (attempt < policy.maxAttempts()) {
        attempt++;
        Duration remaining = Duration.between(Instant.now(), deadline);
        if (remaining.isNegative() || remaining.isZero()) {
            throw new LlmTimeoutException("LLM call deadline exceeded", lastError);
        }

        try {
            return call.execute(remaining);
        } catch (LlmException e) {
            lastError = e;
            if (!e.isRetryable() || attempt >= policy.maxAttempts()) {
                throw e;
            }

            Duration delay = retryDelay(e, attempt, policy);
            if (delay.compareTo(Duration.between(Instant.now(), deadline)) >= 0) {
                throw new LlmTimeoutException("No time left for retry", e);
            }
            sleep(delay);
        }
    }

    throw lastError;
}
```

### 5.5 流式调用重试

流式调用只允许在“尚未发送任何 chunk 给前端”时重试。

```text
情况 A：连接失败 / 首 token 超时 / 429 / 5xx，且未发送 chunk
  -> 可以按重试策略重试

情况 B：已经发送至少一个 chunk 后发生断流 / idle 超时 / Provider 错误
  -> 不重试
  -> 向前端发送 error event
  -> 结束 SSE
```

前端收到 error event 后，由用户决定是否点击“继续”发起新一轮请求。

---

## 六、容错策略

### 6.1 Provider fallback

一期不做自动负载均衡，也不默认做 Provider 自动切换。

只有当用户在 Agent、工作流节点或模型配置中显式配置 fallback Provider 时，才允许 fallback。

```yaml
zify:
  llm:
    fallback:
      enabled: false
```

规则：

- 未显式启用 fallback：主 Provider 失败后直接返回错误。
- 显式启用 fallback：按用户配置顺序尝试备用 Provider。
- fallback 只在未开始流式输出前发生。
- fallback Provider 必须明确指定模型名，不能只按原模型名猜测。
- fallback 发生时必须记录日志，包含原 Provider、fallback Provider、失败原因。

### 6.2 熔断

一期采用进程内轻量熔断，不引入 Resilience4j。该熔断只保护单实例；未来多实例部署时，再改为 Redis 或集中式状态。

状态：

```text
CLOSED     正常调用
OPEN       熔断中，直接跳过该 Provider
HALF_OPEN  冷却后允许一个探测请求
```

默认参数：

```yaml
zify:
  llm:
    circuit-breaker:
      failure-threshold: 5
      open-duration: 60s
```

规则：

- 连续 5 次可重试失败后进入 `OPEN`。
- `OPEN` 持续 60 秒。
- 60 秒后进入 `HALF_OPEN`，只允许 1 个探测请求。
- 探测成功：回到 `CLOSED`，清空失败计数。
- 探测失败：回到 `OPEN`，重新冷却。
- 401 / 403 这类配置错误不计入熔断失败次数，应该标记 Provider 配置异常。

### 6.3 用户可见错误

非流式调用失败时，返回统一业务异常：

```json
{
  "code": "LLM_CALL_FAILED",
  "message": "模型调用失败，请稍后重试",
  "data": {
    "provider": "openai",
    "model": "gpt-4o",
    "retryable": true
  }
}
```

流式调用失败时：

- 未发送任何 chunk：可以重试；重试耗尽后发送 `error` event。
- 已发送部分 chunk：发送 `error` event，然后关闭 SSE。

```json
{
  "event": "error",
  "code": "LLM_STREAM_INTERRUPTED",
  "message": "模型响应中断，可以继续对话重试",
  "retryable": false
}
```

---

## 七、异常分类

异常统一定义在 `zify-model` 子模块的 `infrastructure/client/exception/` 下。

```text
LlmException
├── LlmRetryableException          # 429、5xx、连接失败、首 token 超时
├── LlmNonRetryableException       # 400、401、403、参数错误、上下文过长
├── LlmTimeoutException            # 总 deadline 超时
├── LlmBusyException               # Provider 并发已满
├── LlmCircuitOpenException        # Provider 熔断中
└── LlmCancelledException          # 用户断开或任务取消
```

规则：

- 异常中不能包含 API Key。
- 异常必须包含 `providerId`、`modelName`、`scenario`、`retryable`。
- 上层模块只依赖 `ModelFacade` 返回的结果或业务异常，不直接依赖 Provider SDK 异常。

---

## 八、日志与监控

每次调用必须记录结构化日志。

成功：

```json
{
  "event": "llm_call",
  "traceId": "xxx",
  "scenario": "chat_stream",
  "provider": "openai",
  "model": "gpt-4o",
  "attempt": 1,
  "status": "success",
  "firstTokenMs": 1200,
  "durationMs": 8500,
  "inputTokens": 1200,
  "outputTokens": 350
}
```

失败：

```json
{
  "event": "llm_call",
  "traceId": "xxx",
  "scenario": "chat_stream",
  "provider": "openai",
  "model": "gpt-4o",
  "attempt": 3,
  "status": "failed",
  "errorType": "rate_limited",
  "httpStatus": 429,
  "retryable": true,
  "durationMs": 6500
}
```

取消：

```json
{
  "event": "llm_call",
  "traceId": "xxx",
  "scenario": "chat_stream",
  "provider": "openai",
  "model": "gpt-4o",
  "status": "cancelled",
  "reason": "sse_disconnected"
}
```

一期只写日志；二期需要时接入 Langfuse、Micrometer、Prometheus。

---

## 九、总结

| 维度 | 一期方案 | 关键规则 |
|---|---|---|
| 线程管理 | Virtual Threads + RestClient + Spring 管理的 Executor | Controller 不开线程；SSE 断开必须取消上游；每 Provider 设置并发上限 |
| 超时 | 连接超时、首 token 超时、idle 超时、总 deadline 分开控制 | SseEmitter 超时不能代替上游调用超时 |
| 重试 | 显式 retry wrapper | 支持 `Retry-After`、jitter、总 deadline；流式只在首 chunk 前重试 |
| 容错 | 显式 fallback + 轻量熔断 + 结构化错误 | 一期不做默认 Provider 自动切换；熔断为进程内状态 |
