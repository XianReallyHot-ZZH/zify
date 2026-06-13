# Prompt 04：核心对话闭环（P1）全栈实施

你是 Zify 项目的 AI 编码助手。现在要实施 P1（核心对话闭环 MVP）的全部前后端功能：用户能选 Agent、发消息、看到流式回复、历史会话保留。

## 一、项目背景

Zify 是模块化单体 AI 应用。P1 涉及四个后端模块的增量/新建：`zify-model`（新增流式 Chat 网关）、`zify-agent`（新建）、`zify-engine`（新建）、`zify-chat`（新建），以及 `zify-web` 前端对话页与 Agent 管理页。

后端技术栈：Java 21、Spring Boot 4.0、**Spring AI 2.0（本次首次实装）**、MyBatis-Plus、MySQL 8.x、Flyway、Redis（P1 对话闭环**不使用** Redis）。

前端技术栈：React 19 + TypeScript + Vite 8 + Ant Design 6 + Zustand 5 + Axios + 原生 EventSource（SSE）。

P0（模型管理）已完成：`model_provider` / `model` 表、Provider/Model CRUD、连通性测试（用 RestClient）。`ModelFacade` 目前只有 `listAvailableModels`。

你必须先阅读：

```text
CLAUDE.md
glm-docs/06-zify-code-organization.md          # 代码组织（新模块走四层结构）
glm-docs/07-zify-LLM-api-calling.md            # LLM 调用（超时/重试/熔断/线程）
glm-docs/10-zify-database-spec.md              # 数据库规范
glm-docs/11-zify-core-data-model.md            # 核心数据模型
docs-prd/phase-P1/01-data-model.md             # P1 表设计（agent/conversation/message）
docs-prd/phase-P1/02-functional-spec.md        # P1 功能/接口/前端/上下文管理
docs-prd/model-manager/01-data-model.md        # model 表结构（P1 给它加 context_window）
```

再阅读并复用已有代码，不要重复造轮子：

```text
zify-common：BaseEntity、IdGenerator、Result、ErrorCode（枚举）、BusinessException、
  PageRequest、PageResult、CursorPageRequest、CursorPageResult、SecretEncryptor、
  MaskUtils、AsyncExecutorConfig、ExternalCallException 体系、LlmProperties
zify-model：ModelFacade、ModelFacadeImpl、ModelService、ModelEntity、ModelProviderEntity、
  各 Mapper/Converter、domain/handler/*TestHandler（RestClient 调用范式）、config/ModelModuleConfig
zify-web：api/request.ts（apiGet/apiPost/apiPut/apiDelete + ApiError）、api/modelApi.ts（类型+API 范式）、
  types/api.ts（ApiResponse/OffsetPageResponse/CursorPageResponse/opaque CursorPageQuery）、
  shared/hooks/useCursorPagination.ts、shared/hooks/useOffsetPagination.ts、shared/ui/*、
  shared/utils/*、stores/appStore.ts、app/router.tsx（/ 已指向 ChatPage）
```

## 二、硬性约束

1. 跨模块调用只走目标模块 `api` Facade；Controller 只调本模块 Service；Entity/Mapper/Service 禁止跨模块。
2. HTTP Request/Response 不进 domain；Facade 不返回 Entity 或 MyBatis-Plus Page 对象。
3. **事务内禁止调用 LLM / 外部 API**。事务只包数据库写。
4. MySQL 表必含 `id`(CHAR(36))/`created_at`(DATETIME(3))/`updated_at`/`is_deleted`(TINYINT)；软删唯一约束用 generated column，禁止 `UNIQUE(field,is_deleted)`；关联 ID 建索引；不建物理外键。
5. 大表（`message`、`conversation` 列表）用 Keyset 分页，禁止 OFFSET；列表接口不返回大字段。
6. 所有外部调用有超时；**SSE 断连/超时必须取消上游 LLM**；API Key 全链路不记录、不返回、加密存储。
7. 异常用 `BusinessException` + `ErrorCode` 枚举（**新错误码直接加到 `zify-common` 的 `ErrorCode` 枚举**，不新建模块错误码枚举）。
8. **SSE 流式端点位于 `zify-chat` 模块**（依赖方向 `chat → engine`，持久化归 chat），不在 engine；engine 是纯编排 Facade，不读写任何数据库表。
9. 发送消息（POST）与建立流（GET SSE）分两步，受 `EventSource` GET-only 约束；query string 只传 `messageId`。
10. LLM 调用用 **Spring AI 2.0**（程序化构造 ChatModel，不走自动配置）；Spring AI 的 `stream()` 是 reactive（`Flux<ChatResponse>`），在虚拟线程上用阻塞桥接适配 `TextStreamSink`，**取消 = dispose 订阅**。此桥接取代 `glm-docs/07` §3.1「不用 WebClient」一条（Spring AI 内部为 reactive 传输），但 §4 超时、§5 重试、并发保护、§七 异常分类仍照常适用。
11. 前端：相对 import（`../../`）不用 `@/`；类型用 `type` 不用 `interface`；页面局部状态用 `useState`；Store 不发 HTTP；SSE 经 `chatApi.ts` 创建、`features/chat/hooks/useChatStream.ts` 处理。
12. 不引入技术栈以外依赖（Spring AI 已在父 POM BOM 中）。

## 三、模块代码结构

**`zify-model`（增量，沿用其既有扁平结构：`api` / `controller` / `domain` / `infrastructure` / `config`）**

```text
com.zify.model/
├── api/
│   ├── dto/chat/                  # 新增
│   │   ├── ChatCompletionCommand.java
│   │   ├── ChatMessage.java
│   │   ├── ChatOptions.java
│   │   ├── ChatCompletionResult.java
│   │   └── TokenUsage.java
│   ├── ModelFacade.java           # 新增 chatStream 方法
│   └── ModelFacadeImpl.java       # 新增 chatStream 实现
├── config/
│   ├── ModelModuleConfig.java     # 既有
│   └── LlmTimeoutProperties.java  # 新增（zify.llm.timeout.chat-stream.*）
└── infrastructure/
    └── client/                    # 新增
        ├── LlmChatClient.java
        ├── OpenAiChatClient.java
        ├── AnthropicChatClient.java
        ├── LlmChatGateway.java
        ├── ProviderBulkhead.java
        ├── LlmRetryWrapper.java
        └── exception/
            ├── LlmException.java
            ├── LlmRetryableException.java
            ├── LlmNonRetryableException.java
            ├── LlmTimeoutException.java
            ├── LlmBusyException.java
            └── LlmCancelledException.java
```

**`zify-agent` / `zify-engine` / `zify-chat`（新建，遵循 doc 06 四层结构）**

```text
com.zify.{agent|engine|chat}/
├── api/
│   ├── {Module}Facade.java
│   └── dto/...                    # 跨模块 DTO/Command/Result
├── adapter/
│   ├── web/
│   │   ├── {Module}Controller.java
│   │   └── request|response/      # 仅 chat/agent 需要
│   └── sse/                       # 仅 chat
│       └── ChatStreamSseController.java
├── domain/
│   ├── {Module}Service.java
│   └── ... (engine: ContextManager; chat: ChatStreamService)
└── infrastructure/
    ├── entity/  mapper/  converter/
    ├── facade/{Module}FacadeImpl.java
    └── ... (engine 无 entity/mapper)
```

> `zify-common` 新增 `TextStreamSink`（放 `com.zify.common.web`）。

---

## 四、实施任务清单

> 严格按顺序，每个任务结束运行构建验证通过再进下一个。后端 `mvn package -DskipTests`，前端 `cd zify-web && npm run build`。

---

### 任务 1：model 模块 Spring AI 接入骨架

#### 目标
引入 Spring AI、关闭其自动配置、加虚拟线程执行器与超时配置、给 model 表加 `context_window`。

#### 需要做

**1.1 zify-model/pom.xml**：在 `zify-common` 依赖之外新增（版本由父 POM `spring-ai-bom` 管理）：

```xml
<dependency>
    <groupId>org.springframework.ai</groupId>
    <artifactId>spring-ai-starter-model-openai</artifactId>
</dependency>
<dependency>
    <groupId>org.springframework.ai</groupId>
    <artifactId>spring-ai-starter-model-anthropic</artifactId>
</dependency>
```

> 若 Spring AI 2.0.0 的 artifactId 与上述不同，以 `spring-ai-bom` 2.0.0 中实际可用名为准（如 `spring-ai-openai`/`spring-ai-anthropic`）。

**1.2 关闭 Spring AI 自动配置**：因为 Provider 配置在 DB、需程序化构造，不能让 starter 按 `spring.ai.openai.*` 自动建 Bean。在 `zify-app/src/main/resources/application.yml` 顶层加：

```yaml
spring:
  threads:
    virtual:
      enabled: true
  autoconfigure:
    exclude:
      - org.springframework.ai.model.openai.autoconfigure.OpenAiChatAutoConfiguration
      - org.springframework.ai.model.anthropic.autoconfigure.AnthropicChatAutoConfiguration
```

> 自动配置类名以实际 Spring AI 2.0 包路径为准；若 exclude 类名不存在导致启动失败，改为在 `@SpringBootApplication(exclude = {...})` 上排除，或仅引入非 starter 的核心 artifact。

**1.3 LlmTimeoutProperties**（`com.zify.model.config.LlmTimeoutProperties`，`@ConfigurationProperties("zify.llm.timeout")`）：绑定 `chat-stream.connect/first-token/idle/total`（Duration）。在 `application.yml` 加：

```yaml
zify:
  llm:
    timeout:
      chat-stream:
        connect: 10s
        first-token: 30s
        idle: 45s
        total: 120s
```

**1.4 虚拟线程执行器**：在 `zify-common` 的 `AsyncExecutorConfig` 追加一个虚拟线程 Bean（供 chat 提交对话轮次用）：

```java
@Bean(name = "llmTaskExecutor", destroyMethod = "close")
public ExecutorService llmTaskExecutor() {
    return Executors.newVirtualThreadPerTaskExecutor();
}
```

> 放 common 而非 model：与其余执行器集中、便于 chat 注入（对 doc 07 §3.2「放 model」的微调）。

**1.5 V5 迁移 + ModelEntity 字段**：新建 `zify-app/src/main/resources/db/migration/V5__model__add_context_window.sql`：

```sql
ALTER TABLE `model`
    ADD COLUMN `context_window` INT NULL COMMENT '模型上下文窗口大小（token），NULL 时用全局默认值';
```

`ModelEntity` 加 `private Integer contextWindow;`（驼峰自动映射）。`ModelResponse` 加 `contextWindow`（可空）。`ModelConverter` 透传该字段。模型 CRUD 暂不强制 UI 改造（NULL → 全局默认兜底）。

#### 验收标准
- `mvn package -DskipTests` 通过。
- 应用能正常启动（Spring AI 自动配置已排除，不因缺少 `spring.ai.openai.api-key` 报错）。

---

### 任务 2：TextStreamSink + model chat DTO

#### 目标
定义流式回调与 chat 调用的入参/出参。

#### 需要做

**2.1** `zify-common`：`com.zify.common.web.TextStreamSink`

```java
@FunctionalInterface
public interface TextStreamSink {
    void onDelta(String delta);
}
```

**2.2** `com.zify.model.api.dto.chat`：

```java
// ChatCompletionCommand
private String modelId;
private List<ChatMessage> messages;     // 含 system + 历史 + 本轮 user
private ChatOptions options;            // 可空

// ChatMessage
private String role;     // USER / ASSISTANT / SYSTEM
private String content;

// ChatOptions（均可空，空则用 model.default_params）
private Double temperature;
private Integer maxTokens;
private Double topP;

// TokenUsage
private Integer promptTokens;
private Integer completionTokens;
private Integer totalTokens;

// ChatCompletionResult
private String content;          // 累计全文
private String finishReason;     // STOP / LENGTH / TIMEOUT / CANCELLED ...
private TokenUsage usage;
```

规则：纯 POJO（`@Data` 或手写 getter/setter，与项目风格一致）；不 import 任何 Entity。

#### 验收标准
- `mvn package -DskipTests` 通过。

---

### 任务 3：LLM Provider Client（Spring AI 流式）

#### 目标
用 Spring AI 程序化构造 ChatModel 并流式调用，桥接到 `TextStreamSink`；定义异常体系。

#### 需要做

**3.1 异常体系** `infrastructure/client/exception/`：`LlmException extends RuntimeException`（含 `providerId/modelName/scenario/retryable`，**不含 API Key**），子类：`LlmRetryableException`、`LlmNonRetryableException`、`LlmTimeoutException`、`LlmBusyException`、`LlmCancelledException`。

**3.2 `LlmChatClient` 接口**：

```java
public interface LlmChatClient {
    /** 流式调用：token 经 sink 回调，返回最终结果。阻塞当前（虚拟）线程直到流结束。 */
    ChatCompletionResult streamChat(ChatCallContext ctx, TextStreamSink sink);
}
```

`ChatCallContext`（client 包内 record/class）：`providerType, baseUrl, apiKey(明文), modelName, extraConfig, messages, options, deadline(Instant)`。

**3.3 `OpenAiChatClient`**（处理 `OPENAI` / `OPENAI_COMPATIBLE`）：
- 用 Spring AI 程序化构造：`OpenAiApi.builder().baseUrl(baseUrl).apiKey(apiKey).build()` → `OpenAiChatModel.builder().openAiApi(api).defaultOptions(OpenAiChatOptions.builder().model(modelName).temperature(...).maxTokens(...).build()).build()`。
- options 为空时回退 `model.default_params`（由 gateway 传入已合并的 options）。
- 流式桥接（**核心模式**）：

```java
StringBuilder acc = new StringBuilder();
AtomicReference<TokenUsage> usageRef = new AtomicReference<>();
CountDownLatch done = new CountDownLatch(1);
AtomicReference<Throwable> err = new AtomicReference<>();

Flux<ChatResponse> flux = chatModel.stream(new Prompt(messages, chatOptions));
Disposable disposable = flux.subscribe(
    resp -> {
        String delta = resp.getResult().getOutput().getText();
        if (delta != null && !delta.isEmpty()) { acc.append(delta); sink.onDelta(delta); }
        if (resp.getMetadata() != null && resp.getMetadata().getUsage() != null) {
            usageRef.set(toTokenUsage(resp.getMetadata().getUsage()));
        }
    },
    e -> { err.set(e); done.countDown(); },
    done::countDown
);
try {
    if (!done.await(Duration.between(Instant.now(), ctx.deadline()))) {
        disposable.dispose();
        throw new LlmTimeoutException(...);
    }
} catch (InterruptedException e) {
    disposable.dispose();
    Thread.currentThread().interrupt();
    throw new LlmCancelledException(...);
}
if (err.get() != null) throw wrap(err.get());
return new ChatCompletionResult(acc.toString(), "STOP", usageRef.get());
```

> API 形态以 Spring AI 2.0 实际为准（`ChatResponse.getResult().getOutput().getText()`、`getMetadata().getUsage()`）。关键不变量：订阅后用 `CountDownLatch` 在虚拟线程上阻塞等完成；中断/超时 → `dispose` 取消上游。

**3.4 `AnthropicChatClient`**（处理 `ANTHROPIC`）：构造 `AnthropicApi` + `AnthropicChatModel`（`apiKey`、必要时带 `anthropic-version` 来自 `extraConfig`），流式桥接同上。

#### 验收标准
- `mvn package -DskipTests` 通过。
- 能用一段临时代码（可放测试或 main 临时调用，验证后删除）对一个真实 Provider 配置发起流式调用，看到逐 token 输出。

---

### 任务 4：LlmChatGateway + ModelFacade.chatStream

#### 目标
编排：解析模型→供应商→解密 Key→选 Client→并发保护→重试→超时，并暴露到 Facade。

#### 需要做

**4.1 `ProviderBulkhead`**（`infrastructure/client/`）：按 Provider 的 `Semaphore`（`zify.llm.provider-defaults.max-concurrent=20`、`acquire-timeout=2s`）。`<T> T execute(Callable<T>)`：`tryAcquire`（超时抛 `LlmBusyException`）→ 执行 → `finally release`。每个 Provider 一个实例（按 providerId 缓存，用 `ConcurrentHashMap`）。

**4.2 `LlmRetryWrapper`**：显式重试（不用 `@Retryable`）。最大 3 次尝试、初始退避 1s、倍率 2、jitter 20%、尊重总 deadline；可重试条件：429/5xx/连接失败/首 token 超时（`LlmRetryableException`）；不可重试：400/401/403/422/上下文过长（`LlmNonRetryableException`）。**流式只在「未通过 sink 发送任何 delta 前」重试**——记录 `sink` 是否已回调过，已发 delta 后失败直接抛（由上层发 `run_error`）。

**4.3 `LlmChatGateway`**（`@Component`，注入 `ModelMapper`、`ModelProviderMapper`、`SecretEncryptor`、各 `LlmChatClient`、`LlmTimeoutProperties`）：

```java
public ChatCompletionResult chatStream(ChatCompletionCommand command, TextStreamSink sink) {
    // 1. 解析 modelId → ModelEntity（不存在抛 LlmNonRetryableException，scenario=resolve_model）
    // 2. 解析 providerId → ModelProviderEntity（不存在/已删/inactive 抛非可重试）
    // 3. 解密 apiKey（SecretEncryptor.decrypt），明文只在本方法与 client 内存在
    // 4. 合并 options：command.options 覆盖 model.default_params（null 用默认）
    // 5. 选 client：providerType ∈ {OPENAI, OPENAI_COMPATIBLE} → OpenAiChatClient；ANTHROPIC → AnthropicChatClient
    // 6. deadline = now + chat-stream.total
    // 7. bulkhead.execute(() -> retryWrapper.withRetry(() -> client.streamChat(ctx, sink)))
    //    传给 client 一个「是否已发 delta」的判断，供重试决策
}
```

日志：成功/失败/取消各打结构化日志（event=llm_call, scenario=chat_stream, provider, model, attempt, status, durationMs），**禁止输出 apiKey**。

**4.4 `ModelFacade` 新增方法**：

```java
ChatCompletionResult chatStream(ChatCompletionCommand command, TextStreamSink sink);
```

`ModelFacadeImpl` 注入 `LlmChatGateway`，直接转发。

#### 验收标准
- `mvn package -DskipTests` 通过。
- `chatStream` 全程不输出 apiKey；解密仅在本模块内；超时/重试/并发保护按 doc 07 生效。

---

### 任务 5：agent 迁移 + Entity + Mapper + Converter

#### 目标
建 `agent` 表与数据层。

#### 需要做

**5.1 迁移** `V3__agent__create_agent_table.sql`：DDL 严格按 `docs-prd/phase-P1/01-data-model.md` §3.1（含 `system_prompt` MEDIUMTEXT、`model_id` CHAR(36) NULL、`workflow_id` CHAR(36) NULL、`active_name` generated column、索引 `uk_agent_active_name` / `idx_agent_type_deleted_created_id` / `idx_agent_model_deleted_id` / `idx_agent_created_at`）。

**5.2** `AgentEntity extends BaseEntity`（`@TableName("agent")`）：`createdBy/updatedBy/name/description/agentType/status/systemPrompt(String)/modelId/workflowId`。`system_prompt` 对应 `systemPrompt`（驼峰映射）。

**5.3** `AgentMapper extends BaseMapper<AgentEntity>`。

**5.4** `AgentConverter`（静态工具）：`toResponse(entity, modelName?)`、`toSummary(entity)`、`toConfigDTO(entity)`、`toEntity(CreateAgentRequest)`、`updateEntity(entity, UpdateAgentRequest)`。

#### 验收标准
- `mvn package -DskipTests` 通过；表与字段、索引与 PRD 一致。

---

### 任务 6：agent DTO + AgentService + AgentFacade

#### 目标
Agent CRUD、校验、Facade。

#### 需要做

**6.1 错误码**：在 `zify-common` 的 `ErrorCode` 枚举追加：`AGENT_NAME_DUPLICATE`、`AGENT_TYPE_INVALID`、`AGENT_TYPE_IMMUTABLE`、`AGENT_INACTIVE`（`AGENT_NOT_FOUND`、`MODEL_UNAVAILABLE` 已存在）。

**6.2 DTO**（`com.zify.agent.api.dto`）：

```java
// CreateAgentRequest
@NotBlank name; @Size(max=512) description; @NotBlank agentType; systemPrompt; @NotBlank modelId;
// UpdateAgentRequest
name; description; systemPrompt; modelId; status;   // agentType 不出现=不可改
// UpdateAgentStatusRequest  @NotBlank status;   // ACTIVE/INACTIVE
// AgentListQuery extends PageRequest   name; agentType; status;
// AgentResponse（详情，含 systemPrompt）
id,name,description,agentType,status,systemPrompt,modelId,modelName,createdAt,updatedAt;
// AgentSummary（列表卡片）
id,name,description,agentType,status,modelName,lastConversationAt,createdAt;
// AgentConfigDTO（Facade 用：给 engine/chat）
id,name,agentType,status,systemPrompt,modelId;
```

**6.3 `AgentService`**（`@Service`，注入 `AgentMapper`、`ModelFacade`）：

```java
PageResult<AgentResponse> listAgents(AgentListQuery q)   // name 模糊 like 'kw%'（禁前导%）；type/status 等值；OFFSET
AgentResponse getAgent(String id)
AgentResponse createAgent(CreateAgentRequest r)
AgentResponse updateAgent(String id, UpdateAgentRequest r)
void deleteAgent(String id)            // 软删，不级联删会话
void updateStatus(String id, String s)
```

业务规则：
- `agentType` 只接受 `REACT`（创建校验 `AGENT_TYPE_INVALID`；更新带 agentType 且与原值不同 → `AGENT_TYPE_IMMUTABLE`）。
- `name` 未删除唯一（违反 → `AGENT_NAME_DUPLICATE`）。
- `modelId` 必须可用：`ModelFacade.listAvailableModels("LLM")` 含该 id（否则 `MODEL_UNAVAILABLE`）。
- 列表 `lastConversationAt` 暂可为空（chat 模块未建表前；接好后由列表 JOIN/子查询补，或留空）。
- `workflow_id` P1 恒写 NULL。

**6.4 `AgentFacade`**（`api/AgentFacade`）：

```java
AgentConfigDTO getAgentConfig(String agentId);   // 不存在/已删抛 AGENT_NOT_FOUND
```

`AgentFacadeImpl`（`infrastructure/facade/`）转发到 `AgentService`。

#### 验收标准
- `mvn package -DskipTests` 通过；跨模块只经 Facade。

---

### 任务 7：agent Controller

#### 需要做
`com.zify.agent.adapter.web.AgentController`，`@RequestMapping("/api/agents")`：

```text
POST   /api/agents            @Valid CreateAgentRequest           -> Result<AgentResponse>
GET    /api/agents            AgentListQuery(query)               -> Result<PageResult<AgentResponse>>
GET    /api/agents/{id}                                           -> Result<AgentResponse>
PUT    /api/agents/{id}       @Valid UpdateAgentRequest           -> Result<AgentResponse>
DELETE /api/agents/{id}                                           -> Result<Void>
PUT    /api/agents/{id}/status @Valid UpdateAgentStatusRequest    -> Result<Void>
```

#### 验收标准
- Controller 无业务逻辑；返回 `Result<T>`；`mvn package -DskipTests` 通过。

---

### 任务 8：engine DTO + EngineFacade.runChatTurn（核心，暂不含 compaction）

#### 目标
纯编排：取 Agent 配置 → 组装 Prompt → 调 `ModelFacade.chatStream` → 经 sink 流出。**本任务不实现摘要压缩**（summary 字段原样透传，不截断不压缩），先把端到端跑通。

#### 需要做

**8.1 DTO**（`com.zify.engine.api.dto`）：

```java
// ChatMessage  { role; content; }
// ChatTurnCommand  { agentId; List<ChatMessage> history; assistantMessageId; String summary; String summaryCoveredMessageId; }
// ChatTurnResult  { content; finishReason; TokenUsage usage; String newSummary; String newSummaryCoveredMessageId; }
// TokenUsage  { promptTokens; completionTokens; totalTokens; }
```

> `summary` / `summaryCoveredMessageId` 可空（当前会话 running summary）；`newSummary` / `newSummaryCoveredMessageId` 仅压缩时非空（本任务恒为 null）。

**8.2 `EngineService.runChatTurn`**（`@Service`，注入 `AgentFacade`、`ModelFacade`）：

```java
public ChatTurnResult runChatTurn(ChatTurnCommand cmd, TextStreamSink sink) {
    AgentConfigDTO agent = agentFacade.getAgentConfig(cmd.agentId);          // 校验存在
    // 组装 messages：
    //   若 cmd.summary 非空 → 追加一条 {role=SYSTEM, content=cmd.summary}（或合并进 systemPrompt，二选一，注释说明）
    //   再追加 agent.systemPrompt（若有）作为 {role=SYSTEM}
    //   再追加 cmd.history
    //   history 末尾即本轮 user 输入（已由 chat 落库并加载）
    // （本任务不做 token 预算/截断/压缩）
    ChatCompletionCommand mc = new ChatCompletionCommand(agent.modelId, messages, null);
    ChatCompletionResult r = modelFacade.chatStream(mc, sink);
    return new ChatTurnResult(r.content, r.finishReason, r.usage, null, null); // newSummary 恒 null
}
```

> 失败直接抛 `LlmException`，由 chat 决定发 `run_error`。engine 不 catch 吞错。

**8.3 `EngineFacade`**（`api/EngineFacade`）：`ChatTurnResult runChatTurn(ChatTurnCommand cmd, TextStreamSink sink);` → `EngineFacadeImpl`（`infrastructure/facade/`）转发。

#### 验收标准
- `mvn package -DskipTests` 通过；engine 不依赖任何 Mapper/Entity。

---

### 任务 9：chat 迁移 + Entity + Mapper + Converter

#### 目标
建 `conversation`（含 summary 列）与 `message` 表。

#### 需要做

**9.1 迁移** `V4__chat__create_conversation_message.sql`：两张表 DDL 严格按 `01-data-model.md` §3.2 / §3.3。注意：
- `conversation` 含 `title/agent_id/status/message_count/last_message_at(NOT NULL)/summary_text(MEDIUMTEXT NULL)/summary_covered_message_id(CHAR(36) NULL)`，索引 `idx_conv_deleted_lastmsg_id` / `idx_conv_agent_deleted_created_id` / `idx_conv_created_at`。
- `message` 含 `conversation_id/role/content(LONGTEXT)/metadata(JSON NULL)`，索引 `idx_msg_conv_deleted_created_id` / `idx_msg_created_at`；无 `created_by/updated_by`，无 `status`。

**9.2 Entity**：`ConversationEntity extends BaseEntity`（含 `createdBy/updatedBy/title/agentId/status/messageCount/lastMessageAt(LocalDateTime)/summaryText(String)/summaryCoveredMessageId`）；`MessageEntity extends BaseEntity`（`conversationId/role/content/metadata(Map<String,Object>, JacksonTypeHandler, @TableName(autoResultMap=true))`）。

**9.3 Mapper**：`ConversationMapper`、`MessageMapper`（均 `extends BaseMapper<...>`）。

**9.4 Converter**：`ConversationConverter`、`MessageConverter`（静态；`toResponse`/`toSummary` 等）。

#### 验收标准
- `mvn package -DskipTests` 通过。

---

### 任务 10：chat DTO + ConversationService

#### 目标
会话 CRUD + Keyset 列表。

#### 需要做

**10.1 错误码**：`ErrorCode` 追加 `CONVERSATION_NOT_FOUND`、`CONVERSATION_NOT_ACTIVE`、`MESSAGE_CONTENT_EMPTY`、`MESSAGE_TOO_LONG`、`CHAT_TURN_FAILED`。

**10.2 DTO**（`com.zify.chat.api.dto`）：

```java
// CreateConversationRequest  @NotBlank agentId;
// ConversationResponse  id,title,agentId,agentName,status,messageCount,lastMessageAt,createdAt,updatedAt;
// ConversationSummaryResponse  id,title,agentName,messageCount,lastMessageAt;   // 列表用（轻量，无 content）
// SendMessageRequest  @NotBlank content;
// MessageResponse  id,role,content,metadata(Map|null),createdAt;
```

**10.3 `ConversationService`**（`@Service`，注入 `ConversationMapper`、`MessageMapper`、`AgentFacade`）：

```java
ConversationResponse create(CreateConversationRequest r)   // AgentFacade.getAgentConfig 校验 REACT+ACTIVE；title=agent.name；last_message_at=now；message_count=0
CursorPageResult<ConversationSummaryResponse> list(String agentId, String titleLike, CursorPageRequest q)  // 按 last_message_at DESC,id DESC Keyset；返回 CursorPageResult（双字段 nextCursorId/nextCursorCreatedAt）
ConversationResponse get(String id)
void delete(String id)   // @Transactional：软删 conversation + 其下全部 message
```

> 列表不取 content；`agentName` 经 AgentFacade 或冗余——P1 用 `AgentFacade.getAgentConfig` 缓存名（会话量小可接受）。

#### 验收标准
- `mvn package -DskipTests` 通过；Keyset 查询命中 `idx_conv_deleted_lastmsg_id`。

---

### 任务 11：MessageService（发送 + 历史 + 游标编解码 + 单条拦截）

#### 目标
落库用户消息、更新会话计数/时间、消息历史 Keyset、opaque 游标编解码、单条超限拦截。

#### 需要做
`com.zify.chat.domain.MessageService`（`@Service`，注入 `MessageMapper`、`ConversationMapper`、`AgentFacade`、`ChatContextProperties`）：

```java
// POST：发送用户消息（短事务，不调 LLM）
@Transactional
public SendMessageResult send(String conversationId, SendMessageRequest r)
CursorPageResult<MessageResponse> listHistory(String conversationId, CursorPageRequest q)
```

`send` 规则：
- 会话存在且 `ACTIVE`（否则 `CONVERSATION_NOT_FOUND`/`CONVERSATION_NOT_ACTIVE`）；Agent `ACTIVE`+`REACT`+`modelId` 可用。
- `content` 去空白后非空（`MESSAGE_CONTENT_EMPTY`）；按近似 token 估算，超 `zify.chat.context.max-input-tokens` → `MESSAGE_TOO_LONG`（**单条拦截**）。
- INSERT `USER` 消息；`conversation.message_count+1`、`last_message_at=now`（同一事务）。
- 返回 `{ userMessageId, createdAt }`。

`listHistory` 规则：按 `created_at DESC, id DESC` Keyset（命中 `idx_msg_conv_deleted_created_id`）；返回 `content`+`metadata`。

**opaque 游标编解码**（放 `com.zify.chat.domain.CursorCodec` 或 Converter 静态方法）：
- 编码：`nextCursor = Base64Url( ISO-8601(created_at) + "#" + id )`；无更多数据时返回 `null`。
- 解码：将入参 `cursor` 拆为 `cursorCreatedAt` + `cursorId`，填入 Keyset 条件。
- Controller 层调用编解码，`CursorPageResult`（common）不改。

**ChatContextProperties**（`com.zify.chat.domain.ChatContextProperties`，`@ConfigurationProperties("zify.chat.context")`）绑定 `default-window/budget-threshold/compaction-batch/max-input-tokens/summary-overhead-tokens`。`application.yml`：

```yaml
zify:
  chat:
    context:
      default-window: 128000
      budget-threshold: 0.75
      compaction-batch: 6
      max-input-tokens: 30000
      summary-overhead-tokens: 2000
```

**token 估算**：`com.zify.chat.domain.TokenEstimator`（或 engine 内，本任务先放 chat 给 send 用）近似实现 `int estimate(String)`（如 `长度` 对 CJK 偏保守 + 拉丁 `/4` 的混合启发式），注释说明为近似。

#### 验收标准
- `mvn package -DskipTests` 通过；事务未包任何外部调用；单条超限返回 `MESSAGE_TOO_LONG`。

---

### 任务 12：chat Controller（会话 + 消息，opaque 游标）

#### 需要做
`com.zify.chat.adapter.web.ConversationController`（`/api/chat/conversations`）：

```text
POST   /api/chat/conversations                 CreateConversationRequest -> Result<ConversationResponse>
GET    /api/chat/conversations?cursor=&limit=&agentId=&title=  -> Result<CursorPageRespDTO>   # Controller 把 CursorPageResult 编码为 {records,nextCursor,hasMore}
GET    /api/chat/conversations/{id}            -> Result<ConversationResponse>
DELETE /api/chat/conversations/{id}            -> Result<Void>
```

`com.zify.chat.adapter.web.MessageController`（消息归属在会话路径下）：

```text
POST   /api/chat/conversations/{id}/messages   SendMessageRequest -> Result<{userMessageId,createdAt}>
GET    /api/chat/conversations/{id}/messages?cursor=&limit= -> Result<CursorPageRespDTO>   # 含 content+metadata
```

> Controller 层做 `CursorPageResult ↔ {records,nextCursor,hasMore}` 的编解码；`nextCursor` 为 opaque 字符串。

#### 验收标准
- `mvn package -DskipTests` 通过；列表接口不返回无关大字段（会话列表无 content）。

---

### 任务 13：chat SSE Controller + ChatStreamService（两步流式，核心）

#### 目标
打通 `GET /api/chat/stream?messageId=` 的完整流式生成与落库（暂不含 compaction）。

#### 需要做

**13.1 `ChatStreamService`**（`com.zify.chat.domain`，`@Service`，注入 `MessageMapper`、`ConversationMapper`、`AgentFacade`、`EngineFacade`、`@Qualifier("llmTaskExecutor") ExecutorService`）：

```java
public void startChatStream(String userMessageId, SseEmitter emitter) {
    Future<?> future = llmTaskExecutor.submit(() -> runTurn(userMessageId, emitter));
    emitter.onCompletion(() -> future.cancel(true));
    emitter.onTimeout(() -> future.cancel(true));
    emitter.onError(e -> future.cancel(true));
}
```

`runTurn(userMessageId, emitter)`（**不在事务内调 LLM**）：
1. 查 `userMessageId` → 得 `conversationId`；校验会话 `ACTIVE`、Agent 可用。
2. 从 `conversation` 读 `summaryText`/`summaryCoveredMessageId`。
3. 加载活窗口历史：`message where conversation_id=? and is_deleted=0 and (summary_covered_message_id 为空 或 created_at > 该消息时间)` 按 `created_at ASC`，含本轮 user 消息；映射为 `List<engine.ChatMessage>`。（若 summary_covered 为空则加载全部最近，限制条数如最近 100。）
4. 生成 `assistantMessageId = IdGenerator.uuid()`。
5. 构造 `ChatTurnCommand{agentId, history, assistantMessageId, summary, summaryCoveredMessageId}`，sink = 把 delta 包装成 SSE `message_delta` 事件发给 emitter（`emitter.send(SseEmitter.event().name("message_delta").data({conversationId,assistantMessageId,delta}))`）。累计全文 `StringBuilder`。
6. 调 `engineFacade.runChatTurn(cmd, sink)` → 得 `ChatTurnResult`。
7. 成功：**短事务** INSERT `ASSISTANT` 消息（id=assistantMessageId, role=ASSISTANT, content=全文, metadata={modelId,modelName,providerType,tokens,finishReason,durationMs}）；`conversation.message_count+1`、`last_message_at=now`；发 `done` 事件 `{conversationId,assistantMessageId}`。
8. 失败（engine 抛 `LlmException`）：**不落库** ASSISTANT；发 `run_error` `{message, retryable}`。

**取消语义**：`future.cancel(true)` → 中断虚拟线程 → engine 内 `done.await()` 抛 `InterruptedException` → model `dispose` 上游。
- 若中断时已发过 `message_delta`（`StringBuilder` 非空）：落库已生成部分文本（`finishReason=CANCELLED`），发 `done`。
- 若未发过 delta：不落库，不发 `run_error`（视为用户主动放弃）。

**13.2 `ChatStreamSseController`**（`com.zify.chat.adapter.sse`）：

```java
@GetMapping(value = "/api/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
public SseEmitter stream(@RequestParam String messageId) {
    SseEmitter emitter = new SseEmitter(120_000L);  // 连接兜底，非上游 deadline
    chatStreamService.startChatStream(messageId, emitter);
    return emitter;
}
```

#### 验收标准
- `mvn package -DskipTests` 通过。
- 端到端：用 curl/前端发消息，SSE 收到 `message_delta` 序列 + `done`；点中断能取消；刷新后历史完整。

---

### 任务 14：上下文管理（摘要压缩 + 截断兜底）

#### 目标
在 engine 接入预算/截断/摘要压缩，在 chat 接入 summary 落库。核心闭环已验证后再做。

#### 需要做

**14.1 `com.zify.engine.domain.ContextManager`**（注入 `ModelFacade`、`ChatContextProperties`、`TokenEstimator`）：把 `EngineService.runChatTurn` 改为先经 `ContextManager`：

```
budget = (agent.model.contextWindow ?: zify.chat.context.default-window) − estimate(system) − maxTokens − summary-overhead
活窗口 = cmd.history（已由 chat 按 summary_covered 截取过）
if estimate(summary + system + 活窗口) > budget × threshold:
    取活窗口最旧 K=compaction-batch 条（绝不含本轮 user，即保留末尾至少最后一条）
    用 ModelFacade.chatStream（收集型 sink：StringBuilder，不接 emitter）对「旧 summary + K 条」生成新摘要
    newSummary = 结果；newCoveredId = 第 K 条的 messageId（由 chat 传入或按序号定位）
    活窗口 = 剩余最近条
压缩后仍超预算 → 从最旧起直接丢弃活窗口（不摘要），直到塞下（尾部截断兜底）
最终 messages = [system:agent.systemPrompt] + (summary? system:summary) + 活窗口
返回 {messages, newSummary, newSummaryCoveredMessageId}
```

> 摘要调用同样走 bulkhead/retry/超时；不在事务内。`history` 每条需带 `messageId` 供定位 `newCoveredId`——给 `engine.ChatMessage` 加可选 `messageId` 字段（chat 组装时填）。

**14.2 `EngineService.runChatTurn`** 用 ContextManager 产出 `messages` + 可选 `newSummary`，调 `ModelFacade.chatStream`，把 `newSummary`/`newSummaryCoveredMessageId` 填进 `ChatTurnResult`。

**14.3 chat 落库 summary**：`ChatStreamService.runTurn` 第 7 步成功落库时，若 `ChatTurnResult.newSummary != null`，同事务 `UPDATE conversation SET summary_text=?, summary_covered_message_id=? WHERE id=?`（幂等：可加 `AND (summary_covered_message_id IS NULL OR summary_covered_message_id = 旧值)`）。

**14.4 活窗口加载配合**：任务 13 第 3 步的「活窗口」改为：`summary_covered_message_id` 之后的消息（含本轮 user）；首条会话 summary 为空时加载全部最近。

#### 验收标准
- `mvn package -DskipTests` 通过。
- 构造一个长会话（连续发多条使历史超阈值），观察 DB `conversation.summary_text` 被写入、`summary_covered_message_id` 推进，后续轮次历史变短、对话仍连贯；中断/失败路径不受影响。

---

### 任务 15：前端类型 + API 层

#### 需要做
`zify-web/src/types/agent.ts`、`zify-web/src/types/chat.ts`（含 `ChatStreamEvent`）；`api/agentApi.ts`、`api/chatApi.ts`。类型用 `type`，对齐后端 HTTP request/response。

`types/chat.ts` 关键：

```typescript
export type ChatStreamEvent =
  | { type: 'message_delta'; conversationId: string; assistantMessageId: string; delta: string }
  | { type: 'done'; conversationId: string; assistantMessageId: string }
  | { type: 'run_error'; message: string; retryable?: boolean }
```

`api/chatApi.ts` 关键函数（其余 conversation/message CRUD 类比 `modelApi.ts`）：

```typescript
function sendMessage(conversationId: string, content: string): Promise<{ userMessageId: string; createdAt: string }>
function openChatStream(messageId: string, handlers: {
  onDelta: (e: { conversationId: string; assistantMessageId: string; delta: string }) => void
  onDone: (e: { conversationId: string; assistantMessageId: string }) => void
  onError: (e: { message: string; retryable?: boolean }) => void
}): EventSource {
  const es = new EventSource(`/api/chat/stream?messageId=${encodeURIComponent(messageId)}`)
  es.addEventListener('message_delta', (ev) => handlers.onDelta(JSON.parse((ev as MessageEvent).data)))
  es.addEventListener('done', (ev) => { handlers.onDone(JSON.parse((ev as MessageEvent).data)); es.close() })
  es.addEventListener('run_error', (ev) => { handlers.onError(JSON.parse((ev as MessageEvent).data)); es.close() })
  es.onerror = () => { handlers.onError({ message: 'SSE 连接错误' }); es.close() }
  return es
}
```

> 注意 `apiGet` 第二参当前是 `params` 对象（见 `request.ts`）；OFFSET/Keyset query 直接传对象。会话列表、消息历史返回 `{records,nextCursor,hasMore}`（opaque cursor）。

#### 验收标准
- `npm run build` 通过；类型字段名与后端 JSON 一致。

---

### 任务 16：chatStore + useChatStream

#### 需要做
`stores/chatStore.ts`：`currentConversationId`、`messages: MessageView[]`、`isStreaming`、`eventSourceRef`（或 id）。actions：`setCurrentConversation`、`appendMessage`、`appendDelta(assistantMessageId, delta)`、`setStreaming`、`setEventSource`。**Store 不发 HTTP**。

`features/chat/hooks/useChatStream.ts`：封装 `sendMessage` + `openChatStream`，处理三类事件写 Store；暴露 `send(content)`、`stop()`（`es.close()`，后端经断连取消上游）。

> `MessageView`（前端视图类型）：`id/role/content/metadata/streaming?` 等。流式中临时 ASSISTANT 气泡用 `assistantMessageId`。

#### 验收标准
- `npm run build` 通过。

---

### 任务 17：Agent 列表页 + 表单页 + ModelSelector

#### 需要做
- `features/model/components/ModelSelector.tsx`：下拉，数据来自 `listModels({modelType:'LLM'})` 或新增一个可用模型接口（P1 复用现有 `/api/model/models` 过滤 enabled+provider active 即可，或读 `ModelFacade`——前端走 HTTP，用 `listModels`）。
- `features/agent/components/`：`AgentSelector.tsx`（新建会话选 Agent，弹窗，只列 ACTIVE+REACT）、`AgentForm.tsx`（Ant Design Steps：① 基础信息 name/description/agentType(仅 REACT 可选，WORKFLOW 禁用)；② 人设 systemPrompt；③ 能力 ModelSelector 选 modelId；④ 确认）、`AgentTypeSelector.tsx`、`PromptEditor.tsx`（多行）。
- `pages/agents/AgentListPage.tsx`：搜索 + 类型/状态筛选（OFFSET 分页，`useOffsetPagination`），卡片（名称/描述/REACT 标签/模型/状态/最近对话），操作 编辑/启用禁用/删除（`Popconfirm`）。
- `pages/agents/AgentFormPage.tsx`：按 `useParams` 有无 id 区分创建/编辑，复用 `AgentForm`。

#### 验收标准
- 创建只绑模型的 REACT Agent 成功；列表/编辑/删除/启停可用；`npm run build` 通过。

---

### 任务 18：对话页

#### 需要做
`pages/chat/ChatPage.tsx` + `pages/chat/components/`（`ConversationSidebar`、`ChatPanel`、`MessageList`、`MessageInput`）：
- 左栏：`useCursorPagination` 加载会话列表（按 lastMessageAt 倒序），「新建对话」→ `AgentSelector` → 创建会话并切入；搜索。
- 右栏：Agent 头部 + 消息流（USER 右、ASSISTANT 左，流式追加）+ 输入框 + 发送 + 停止（流式中显示）+「加载更多历史」（向上 `useCursorPagination`）。
- 发送：调 `useChatStream.send(content)`（先 POST 拿 userMessageId 再 openChatStream）；乐观渲染 USER 气泡。
- 空状态：引导「选择 Agent 开始对话」。
- 状态：跨组件的用 `chatStore`；页面局部（搜索、loading）用 `useState`。

#### 验收标准
- 选 Agent 新建会话 → 发消息看到**流式**回复；中断可停；刷新历史保留、可继续、可删；`npm run build` 通过。

---

### 任务 19：端到端验证 + DoD

#### 需要做
1. 配置一个真实 Provider + LLM 模型（P0 已有能力）。
2. 创建一个只绑模型的 REACT Agent。
3. 对话页选它新建会话，发消息，验证：流式 `message_delta` → `done`；中断取消；历史保留；继续对话；删除会话。
4. 长会话验证摘要压缩：`conversation.summary_text` 写入、`summary_covered_message_id` 推进。
5. 单条超大消息被 `MESSAGE_TOO_LONG` 拦截。
6. 过 `CLAUDE.md` §10 检查清单（模块归属/依赖、跨模块只走 Facade、事务不包 LLM、API Key 未泄露、大表 Keyset）。

#### 验收标准（DoD，对齐 `02-functional-spec.md` §14.2）
1. 创建只绑模型的 REACT Agent。
2. 流式回复正常，中断能取消上游。
3. 历史完整保留，可继续/删除。
4. 跨模块只走 Facade；事务未覆盖 LLM；API Key 未泄露。
5. `message`/`conversation` 用 Keyset；列表不返回大字段。
6. 摘要压缩在长会话触发；单条超限被拦截。

---

## 五、禁止实现的内容

1. 不要做 ReAct 工具调用循环（P2）、知识库检索（P3）、Workflow Agent（P4）。
2. 不要给 `tool_call_log` 建表/写日志（P2）。
3. 不要定义 `ToolFacade` / `KnowledgeFacade` / `WorkflowFacade`（推迟 P2/P3/P4；pom 依赖已就位，P1 不引用）。
4. 不要把 SSE 端点放 engine；engine 不得读写 conversation/message。
5. 不要在事务内调用 LLM/外部 API。
6. 不要引入 Redis（P1 对话闭环不用）。
7. 不要改 P0 已有的模型管理 CRUD/测试 Handler 逻辑（只做增量：context_window 列 + chatStream 网关）。
8. 不要改 `zify-common` 的 `CursorPageResult`（opaque 编解码在 Controller 层）。
9. 不要给 message 加 `status` 字段或建 GENERATING 占位行（ASSISTANT 完成才入库）。
10. 不要安装前端新依赖；不要改 `api/request.ts`、`shared/` 既有文件（只新增）。

---

## 六、实施顺序

后端：任务 1→2→3→4（model 网关）→ 5→6→7（agent）→ 8（engine 核心）→ 9→10→11→12（chat 数据/会话/消息/Controller）→ 13（SSE 核心，端到端验证）→ 14（compaction）。
前端：任务 15→16→17→18，期间后端可联调；任务 19 全链路验证。
每任务结束构建验证通过再进下一个。

## 七、验证命令

```bash
# 后端
mvn package -DskipTests
# 启动（需 MySQL；P1 不需 Redis）
java -jar zify-app/target/zify-app-*.jar
# 前端
cd zify-web && npm run build && npm run lint
```

SSE 联调示例：

```bash
curl -N "http://localhost:8080/api/chat/stream?messageId=<userMessageId>"
```

## 八、输出要求

每个任务输出：① 新增/修改文件（完整路径）；② 该任务实现的功能（一句）；③ 构建结果（通过/失败+关键错误）；④ 未完成事项。构建失败必须贴完整错误并说明修复方案，不得隐瞒。

全部完成后额外输出：① 全部 API/SSE 接口清单；② 跨模块 Facade 使用点；③ DoD 逐条核对结果；④ P2 接入工具调用时的注意事项（Spring AI tool-calling 复用点）。
