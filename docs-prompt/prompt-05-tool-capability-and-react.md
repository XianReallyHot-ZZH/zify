# Prompt 05：工具能力 + ReAct 多轮循环（P2）全栈实施

你是 Zify 项目的 AI 编码助手。现在要实施 P2（工具能力 + ReAct 多轮循环）的全部前后端功能：把 P1 的「单轮 LLM」升级为真正的 ReAct 多轮循环——Agent 自主决策、调用 HTTP/MCP 工具、观察结果、再决策，直到完成任务。

## 一、项目背景

Zify 是模块化单体 AI 应用。P2 在 P1 核心对话闭环之上，涉及五个后端模块：`zify-tool`（新建）、`zify-engine`（扩）、`zify-chat`（扩）、`zify-agent`（扩）、`zify-model`（扩），以及 `zify-web` 前端工具管理页、Agent 工具绑定、对话区工具调用展示。

后端技术栈：Java 21、Spring Boot 4.0、Spring AI 2.0（P1 已接入流式 Chat）、MyBatis-Plus、MySQL 8.x、Flyway。P2 **新增依赖**：`spring-ai-mcp-client`（核心库，非 starter）、`io.swagger.parser.v3:swagger-parser-v3`（OpenAPI 解析）。

前端技术栈：React 19 + TypeScript + Vite 8 + Ant Design 6 + Zustand 5 + Axios + 原生 EventSource（SSE）。

P1 已完成（你必须假设其 DoD 已达成）：`agent`/`conversation`/`message` 表、Agent CRUD、对话闭环（`POST /api/chat/conversations/{id}/messages` + `GET /api/chat/stream?messageId=`）、`ModelFacade.chatStream`（Spring AI 流式，OpenAI 兼容 + Anthropic）、`EngineFacade.runChatTurn`（单轮，零工具）、摘要压缩、`TextStreamSink`（仅 `onDelta`）、`llmTaskExecutor`（虚拟线程）、`SecretEncryptor`、`ErrorCode` 枚举（含 `TOOL_NOT_FOUND`=1401）。

你必须先阅读：

```text
CLAUDE.md
glm-docs/06-zify-code-organization.md          # 代码组织（tool 新模块走四层结构）
glm-docs/07-zify-LLM-api-calling.md            # LLM 调用（超时/重试/熔断/线程）
glm-docs/13-zify-tool-calling-spec.md          # 工具调用规范（超时/重试/熔断/SSRF/安全，P2 核心）
glm-docs/10-zify-database-spec.md              # 数据库规范
glm-docs/11-zify-core-data-model.md            # 核心数据模型
docs-prd/phase-P2/00-technical-decisions.md    # P2 13 项最终技术决策
docs-prd/phase-P2/01-data-model.md             # P2 表设计（mcp_server/tool/tool_call_log/agent_tool）
docs-prd/phase-P2/02-functional-spec.md        # P2 功能/接口/前端/ReAct/SSE
docs-prd/phase-P1/02-functional-spec.md        # P1 边界（engine 纯编排、SSE 协议、上下文管理）
```

再阅读并复用 P1 已有代码，不要重复造轮子：

```text
zify-common：BaseEntity、IdGenerator、Result、ErrorCode、BusinessException、GlobalExceptionHandler、
  PageRequest/PageResult、CursorPageRequest/CursorPageResult、SecretEncryptor、MaskUtils、
  AsyncExecutorConfig（含 llmTaskExecutor）、ExternalCallException 体系、TextStreamSink（P2 扩展）、
  RestClientConfig、TraceIdFilter
zify-model：ModelFacade/ModelFacadeImpl（chatStream）、LlmChatGateway、ProviderBulkhead、LlmRetryWrapper、
  OpenAiChatClient、AnthropicChatClient、LlmException 体系、ModelEntity/ModelProviderEntity/Mapper、SecretEncryptor 用法
zify-agent：AgentFacade/AgentFacadeImpl、AgentService、AgentEntity/Mapper/Converter（P2 扩展 agent_tool）
zify-engine：EngineFacade/EngineFacadeImpl、EngineService、ContextManager（P2 扩展 ReAct 循环）
zify-chat：ConversationService、MessageService、ChatStreamService、ChatStreamSseController、
  ConversationEntity/MessageEntity/Mapper/Converter、CursorCodec（P2 扩展工具消息持久化 + SSE 事件）
zify-web：api/{request.ts,agentApi.ts,chatApi.ts}、types/{api.ts,agent.ts,chat.ts}、stores/chatStore.ts、
  features/chat/hooks/useChatStream.ts、features/{model,agent}/components/*、shared/hooks/{useCursorPagination,useOffsetPagination}、
  app/router.tsx
```

## 二、硬性约束

1. 跨模块调用只走目标模块 `api` Facade；Controller 只调本模块 Service；Entity/Mapper/Service 禁止跨模块。
2. HTTP Request/Response 不进 domain；Facade 不返回 Entity 或 MyBatis-Plus Page 对象。
3. **事务内禁止调用 LLM / MCP / HTTP 工具或其他慢外部 API**。事务只包数据库写（落库消息/工具日志/绑定）。
4. MySQL 表必含 `id`(CHAR(36))/`created_at`(DATETIME(3))/`updated_at`/`is_deleted`(TINYINT)；软删唯一约束用 generated column；关联 ID 建索引；不建物理外键。
5. 大表 `tool_call_log` 用 Keyset 分页，禁止 OFFSET；列表接口不返回 `input`/`output` 大字段。
6. 所有外部调用（HTTP 工具、MCP）有超时；**SSE 断连/超时必须取消上游 LLM + 进行中的工具调用**；API Key/鉴权凭据全链路不记录、不返回、加密存储。
7. 异常用 `BusinessException` + `ErrorCode` 枚举（**新错误码加到 `zify-common` 的 `ErrorCode`**）。
8. **Spring AI 类型不跨 Facade 泄漏**：`ToolDefinition`/`ToolResponseMessage`/`ChatResponse`/`McpClient` 等封装在各模块内部——model 封装 Spring AI tool 类型，tool 模块的 `ToolFacade`/`Tool` 接口只用中立 DTO。
9. **engine 保持纯编排**（P1 §2.1 不变）：engine 不碰任何 DB；工具调用过程（ASSISTANT toolCall + TOOL）由 chat 落 `message` 表，`tool_call_log` 由 `ToolFacade.executeTool` 内部写（执行点即记录点）。
10. 工具执行失败**返回 `ToolExecutionResultDTO.status=ERROR`（不抛异常）**，engine 把 output 回灌模型，不中断循环；仅致命错误（超轮次/循环超时/用户中断）才中断发 `run_error`。
11. 工具执行器用独立 `ToolExecutor`（`newVirtualThreadPerTaskExecutor()` + 全局 `Semaphore` 50），与 `llmTaskExecutor` 隔离；禁 `Executors.newFixed/newCached`（虚拟线程执行器是已定例外）。
12. **MCP 接入方式（已确认）**：引核心库 `spring-ai-mcp-client`（提供 `McpSyncClient` + `HttpClientSseClientTransport`/`HttpClientStreamableHttpTransport`），**不引 starter**、不走其连接 auto-config；tool 模块按 DB 配置程序化构建/关闭每个 server 的 `McpSyncClient`（常驻保活）。具体 artifact 名以 Spring AI 2.0 实际可用为准。
13. SSRF 黑名单（默认开）：禁内网/保留地址 + 基础 DNS 检查，**保存时 + 运行时**双重校验；响应 32KB 截断、请求体 1MB 上限；`Authorization`/`Cookie`/`Set-Cookie` 脱敏不记入日志/output。
14. 前端：相对 import（`../../`）不用 `@/`；类型用 `type` 不用 `interface`；页面局部状态用 `useState`；Store 不发 HTTP；SSE 经 `chatApi.ts` 创建、`features/chat/hooks/useChatStream.ts` 处理；不安装新依赖。

## 三、模块代码结构

**`zify-tool`（新建，遵循 doc 06 四层结构）**

```text
com.zify.tool/
├── api/
│   ├── ToolFacade.java
│   └── dto/                          # 全部中立（零 spring-ai LLM 抽象）
│       ├── ToolViewDTO.java
│       ├── ToolExecutionCommand.java
│       ├── ToolExecContext.java
│       └── ToolExecutionResultDTO.java
├── adapter/web/
│   ├── McpServerController.java
│   ├── ToolController.java
│   ├── ToolCallLogController.java
│   └── request|response/...
├── domain/
│   ├── Tool.java                     # 内部统一接口（toView / execute）
│   ├── ToolService.java              # 工具 CRUD + OpenAPI 导入 + 测试
│   ├── McpServerService.java         # MCP Server CRUD + 测试 + 刷新发现
│   ├── ToolCallLogService.java
│   ├── http/HttpTool.java            # HTTP 工具实现
│   ├── mcp/McpTool.java              # MCP 工具实现
│   ├── mcp/McpConnectionManager.java # McpSyncClient 程序化管理
│   └── openapi/OpenApiParser.java    # Swagger Parser → operation 预览/生成配置
└── infrastructure/
    ├── entity/{McpServerEntity,ToolEntity,ToolCallLogEntity}.java
    ├── mapper/{McpServerMapper,ToolMapper,ToolCallLogMapper}.java
    ├── converter/{McpServerConverter,ToolConverter,ToolCallLogConverter}.java
    ├── facade/ToolFacadeImpl.java
    ├── client/                       # 外部调用客户端
    │   ├── ssrf/SsrfGuard.java
    │   ├── sizer/ResponseSizer.java
    │   ├── retry/ToolRetryWrapper.java
    │   ├── breaker/ToolCircuitBreaker.java
    │   └── mcp/McpClientFactory.java # McpSyncClient 构造（transport 选择）
    ├── executor/ToolExecutorConfig.java   # ToolExecutor Bean（虚拟线程 + Semaphore）
    └── exception/
        ├── ToolException.java
        ├── ToolRetryableException.java
        ├── ToolNonRetryableException.java
        ├── ToolTimeoutException.java
        ├── ToolBusyException.java
        ├── ToolCircuitOpenException.java
        └── ToolCancelledException.java
```

**`zify-agent`（扩，新增 agent_tool 关联）**：`api/dto` 增 `BoundToolDTO`；`AgentFacade` 增 `getBoundToolIds`；`domain/AgentToolService` + `infrastructure/entity/AgentToolEntity` + `mapper/AgentToolMapper`；`adapter/web/AgentController` 增 `/tools` 端点。

**`zify-engine`（扩，ReAct 循环）**：`api/dto` 扩展 `ChatMessage`（增 toolCalls/toolCallId）、`ChatTurnResult`（增 `newMessages`/`finalAssistantMessageId`）；`domain/ReActLoop`（新）+ `EngineService` 改造。

**`zify-chat`（扩）**：`MessageService` 增 `persistTurn`（批量落库本轮消息序列）；`ChatStreamService.runTurn` 改造（调 ReAct 循环、发工具 SSE 事件、批量落库）；`ContextManager` 调用方扩展含 TOOL/turn 边界。

**`zify-model`（扩，沿用既有扁平结构）**：`api/dto/chat` 增 `ToolDefinitionDTO`/`ToolCallDTO`，`ChatMessage`/`ChatCompletionCommand`/`ChatCompletionResult` 扩展；`LlmChatGateway`/`OpenAiChatClient`/`AnthropicChatClient` 支持下发 tool 定义 + 提取 toolCalls。

**`zify-common`（扩）**：`TextStreamSink` 升级（增 `onAssistantSegment`/`onToolCallStart`/`onToolCallEnd` default 方法）；`ErrorCode` 增 1402–1408。

---

## 四、实施任务清单

> 严格按顺序，每个任务结束运行构建验证通过再进下一个。后端 `mvn package -DskipTests`，前端 `cd zify-web && npm run build`。DDL/字段/索引/JSON 结构严格按 `docs-prd/phase-P2/01-data-model.md` 对应章节，**不要自己改表结构**。

---

### 任务 1：tool 模块基座（迁移 + pom + 配置 + 属性类）

#### 目标
建立 tool 模块的地基：建表、引依赖、配置、属性类。

#### 需要做

**1.1 迁移脚本**（`zify-app/src/main/resources/db/migration/`）：
- `V6__tool__create_tool_tables.sql`：建 `mcp_server`、`tool`、`tool_call_log` 三表，DDL 严格按 `01` §3.1 / §3.2 / §3.3（含全部字段、generated column `active_name`、索引）。一张表一个 `CREATE TABLE`。
- `V7__agent__create_agent_tool.sql`：建 `agent_tool`，DDL 严格按 `01` §3.4。

**1.2 pom 依赖**（`zify-tool/pom.xml`，在 `zify-common` 之外；版本由父 POM BOM 管理或显式声明）：
```xml
<dependency><groupId>org.springframework.ai</groupId><artifactId>spring-ai-mcp-client</artifactId></dependency>
<dependency><groupId>io.swagger.parser.v3</groupId><artifactId>swagger-parser-v3</artifactId></dependency>
```
> artifact 名以 Spring AI 2.0 / Swagger Parser 实际可用名为准（mcp client 核心库可能为 `spring-ai-mcp-client` 或带 `core` 后缀）。**不引** `*-starter-mcp-client`。

**1.3 配置属性类**（`zify-tool/domain/` 或 `infrastructure/`，`@ConfigurationProperties`）：
- `ToolProperties`（`zify.tool`）：`timeout{connect,mcpHandshake,requestDefault,totalCap}(Duration)`、`circuitBreaker{failureThreshold(int),openDuration(Duration)}`、`executor{maxConcurrent(int)=50}`、`security{ssrf{enabled,allowPrivate},responseMaxBytes(int=32768),requestMaxBytes(int=1048576)}`。
- `ReactLoopProperties`（`zify.chat.react`，可放 chat 模块或 common）：`maxTurns(int=10)`、`loopDeadline(Duration=120s)`、`dupToolCallThreshold(int=3)`。

**1.4 application.yml 增量**（`zify-app/src/main/resources/application.yml`）：按 `02` §十五 写入 `zify.tool.*`、`zify.chat.react.*`。**不写** `spring.ai.mcp.client.*`（不走 starter auto-config）。

#### 验收标准
- `mvn package -DskipTests` 通过；应用启动 Flyway 执行 V6/V7 成功（四张表 + 索引就位）；无 starter auto-config 报错。

---

### 任务 2：四张表 Entity + Mapper + Converter

#### 需要做

**2.1 Entity**（`infrastructure/entity/`，均 `extends BaseEntity`，`@TableName`）：
- `McpServerEntity`：`createdBy/updatedBy/name/description/baseUrl/transportType/authType/authConfig(String)/enabled(Integer)/status(String)/statusMessage/lastConnectedAt(LocalDateTime)`。注意 `active_name` 是 generated column，**不要映射为字段**。
- `ToolEntity`：`createdBy/updatedBy/name/description/sourceType/mcpServerId/inputSchema(String)/endpoint/method/configJson(Map,JacksonTypeHandler)/authConfig(String)/timeoutSeconds(Integer)/idempotent(Integer)/enabled(Integer)`；`@TableName(autoResultMap=true)`（因 configJson 用 TypeHandler）。`active_name` 不映射。
- `ToolCallLogEntity`：`toolId/toolName/sourceType/mcpServerId/agentId/conversationId/workflowRunId/workflowNodeRunId/turn(Integer)/toolCallId/input(Map,JacksonTypeHandler)/output(String)/status/durationMs(Integer)/error(String)`；`@TableName(autoResultMap=true)`。无 `createdBy/updatedBy`。
- `AgentToolEntity`（agent 模块 `infrastructure/entity/`）：`agentId/toolId`；`active_agent_tool` generated column 不映射。

**2.2 Mapper**（`infrastructure/mapper/`，均 `extends BaseMapper<XxxEntity>`）：四个 Mapper。

**2.3 Converter**（`infrastructure/converter/`，静态工具）：`McpServerConverter`、`ToolConverter`、`ToolCallLogConverter`（`toResponse`/`toSummary`/`toEntity` 等；`authConfig` 密文**不**进 Response）。

#### 验收标准
- `mvn package -DskipTests` 通过；字段与 `01` §3 一致；generated column 不被误映射。

---

### 任务 3：tool 中立 DTO + 内部 Tool 接口 + ToolFacade 接口 + 异常体系 + 执行器

#### 需要做

**3.1 中立 DTO**（`api/dto/`，纯 POJO，零 spring-ai）：
```java
// ToolViewDTO        id,name,description,inputSchema(String JSON),sourceType(HTTP/MCP/WORKFLOW)
// ToolExecContext    conversationId(nullable),agentId(nullable),turn(nullable Integer)
// ToolExecutionCommand   toolId,args(Map<String,Object>),context(ToolExecContext)
// ToolExecutionResultDTO status(SUCCESS/ERROR),output(String),durationMs(long),toolCallLogId(String),error(String)
```

**3.2 内部统一 Tool 接口**（`domain/Tool.java`，不跨 Facade）：
```java
public interface Tool {
    ToolView toView();
    ToolExecutionResult execute(Map<String,Object> args, ToolExecContext ctx);
}
```

**3.3 `ToolFacade` 接口**（`api/ToolFacade.java`）：
```java
List<ToolViewDTO> listAvailableTools(Collection<String> toolIds);
ToolExecutionResultDTO executeTool(ToolExecutionCommand command);
```
> 边界修正（相对 `00-tech` §3.1）：不提供 `listBoundTools(agentId)`——`agent_tool` 归 agent 模块，tool 不能跨模块读。engine 先 `AgentFacade.getBoundToolIds(agentId)` 再 `ToolFacade.listAvailableTools(ids)`。

**3.4 异常体系**（`infrastructure/exception/`）：`ToolException extends RuntimeException`（含 `toolId/toolName/scenario/retryable`，**不含凭据**），子类 `ToolRetryableException`/`ToolNonRetryableException`/`ToolTimeoutException`/`ToolBusyException`/`ToolCircuitOpenException`/`ToolCancelledException`。

**3.5 ToolExecutor**（`infrastructure/executor/ToolExecutorConfig.java`）：
```java
@Bean(name = "toolExecutor", destroyMethod = "close")
public ExecutorService toolExecutor() { return Executors.newVirtualThreadPerTaskExecutor(); }
// 另注册一个全局 Semaphore(maxConcurrent) Bean（ToolProperties.executor.maxConcurrent）
```

**3.6 ErrorCode 增量**（`zify-common` `ErrorCode` 枚举）：`MCP_SERVER_NOT_FOUND(1402)`、`TOOL_NAME_DUPLICATE(1403)`、`MCP_SERVER_NAME_DUPLICATE(1404)`、`OPENAPI_PARSE_FAILED(1405)`、`TOOL_SSRF_BLOCKED(1406)`、`TOOL_CONFIG_INVALID(1407)`、`TOOL_NOT_AVAILABLE(1408)`。

#### 验收标准
- `mvn package -DskipTests` 通过；DTO/接口不含任何 spring-ai import。

---

### 任务 4：工具调用运行时保护（重试 + 熔断 + 并发 + SSRF + 截断）

#### 目标
实现 `glm-docs/13` §四–§八 的运行时保护组件，供 HttpTool/McpTool 复用。

#### 需要做

**4.1 `SsrfGuard`**（`infrastructure/client/ssrf/`）：`void validate(String url)`——解析域名 → 校验所有解析 IP 不在黑名单（`13` §8.1 全部网段；`ToolProperties.security.ssrf.allowPrivate=false` 时禁内网）；`enabled=false` 时直接放行。保存时（即时反馈）与运行时（防 IP 变更）都调。违例抛带 `TOOL_SSRF_BLOCKED` 语义的 `ToolNonRetryableException`（保存场景由 Service 转 `BusinessException(TOOL_SSRF_BLOCKED)`）。

**4.2 `ResponseSizer`**（`infrastructure/client/sizer/`）：`String truncate(String text, int maxBytes)`（超 `response-max-bytes` 截断 + 追加 `...[truncated]`，UTF-8 字节计）；`void checkRequestSize(int bytes)`（超 `request-max-bytes` 抛 `ToolNonRetryableException`）。

**4.3 `ToolCircuitBreaker`**（`infrastructure/client/breaker/`）：进程内、per `tool_id`。状态机 CLOSED/OPEN/HALF_OPEN；连续 `failureThreshold`（默认 5）次**可重试**失败 → OPEN `openDuration`（默认 60s）→ HALF_OPEN 放 1 个探测；4xx/非可重试不计入。提供 `<T> T execute(String toolId, Callable<T> action)`，OPEN 时抛 `ToolCircuitOpenException`。用 `ConcurrentHashMap<String, BreakerState>`。

**4.4 `ToolRetryWrapper`**（`infrastructure/client/retry/`）：显式 wrapper（不用 `@Retryable`）。`<T> T withRetry(boolean idempotent, Instant deadline, Callable<T> action)`：
- 最大 3 次尝试、初始退避 1s、倍率 2、jitter 20%、单次最大等待 10s、尊重 `deadline`（不足直接失败）。
- 可重试条件（`13` §5.2）：**建连失败**（连接超时/被拒）→ 幂等/非幂等都可重试（请求未送达）；**请求已发出后失败**（读超时/5xx/429）→ 仅 `idempotent=true` 可重试，否则 `ToolNonRetryableException`；4xx → 不可重试。
- 抛 `ToolRetryableException` 触发重试；耗尽 → 抛 `ToolRetryableException`（由上层转 status=ERROR 回灌）。

#### 验收标准
- `mvn package -DskipTests` 通过；SSRF 校验覆盖 `13` §8.1 全部网段（含 `169.254.169.254`）；熔断/重试逻辑可被 HttpTool/McpTool 直接复用。

---

### 任务 5：HttpTool 执行实现

#### 目标
HTTP 工具的参数映射、鉴权注入、执行、写日志、失败回灌。

#### 需要做

**5.1 `HttpTool`**（`domain/http/HttpTool.java`，`implements Tool`，持有 `ToolEntity` 快照 + 解密后的 `authConfig`）：
- `execute(args, ctx)`：
  1. 按 `configJson.paramsMapping` 把 `args` 映射到 path（填 `{param}` 占位）/query/header/body；Header/Body 模板的 `{{auth.xxx}}` 从解密 `authConfig` 替换、`{{args.xxx}}` 从 args 替换。
  2. `SsrfGuard.validate(endpoint)`；`ResponseSizer.checkRequestSize(请求体字节数)`。
  3. `deadline = now + min(tool.timeoutSeconds ?: requestDefault, totalCap)`；`idempotent = tool.idempotent`。
  4. `circuitBreaker.execute(toolId, () -> retryWrapper.withRetry(idempotent, deadline, () -> doRequest(...)))`。
  5. `doRequest`：用 `RestClient`（复用 common 的 `RestClient.Builder`，按 deadline 设 connect/read 超时）发起请求；返回响应体字符串。
  6. `output = ResponseSizer.truncate(响应体)`；耗时计时。
  7. **写 `tool_call_log`**（input=args JSON 截断后、output=截断后、status=SUCCESS、durationMs、agentId/conversationId/turn 来自 ctx）；返回 `ToolExecutionResult{SUCCESS, output, durationMs, toolCallLogId}`。
  8. 失败：catch `ToolException` → 写 `tool_call_log`（status: TIMEOUT/CIRCUIT_OPEN/ERROR，error=精简信息，**不含凭据**，**脱敏敏感 Header**）→ 返回 `ToolExecutionResult{ERROR, output=回灌文本, ...}`（回灌文本按 `13` §6.2 表）。

> HttpTool 不自己 new RestClient 超时配置；通过一个 `HttpClientFactory`（按 deadline 产出带超时的 RestClient）集中，避免每调用 new。

**5.2 关键不变量**：鉴权凭据（`authConfig` 解密明文）只存在于 `execute` 栈内，不入日志/异常/output；并行安全（HttpTool 无可变状态，快照只读）。

#### 验收标准
- `mvn package -DskipTests` 通过；用一段临时代码对一个公开 HTTP 端点（如 `https://httpbin.org/get`）配置工具并执行，看到 `tool_call_log` 写入、output 正确、超时/重试/熔断可触发；凭据不出现在日志。

---

### 任务 6：OpenAPI 解析器

#### 目标
Swagger Parser 解析 OpenAPI 3.0/3.1 → operation 预览 + 生成 HTTP tool 配置。

#### 需要做

**6.1 `OpenApiParser`**（`domain/openapi/OpenApiParser.java`）：
- `OpenApiParseResult parse(String specContent)`：用 `OpenAPIV3Parser().readContents(spec, ...)`；失败抛 `BusinessException(OPENAPI_PARSE_FAILED)`。
- 产出：`baseUrl`（取首个 server URL，可被调用方覆盖）+ `List<OperationPreview>`，每个含 `operationId/method/path/summary/suggestedName(operationId 缺失用 method_path，小写蛇形)/hasRequestBody`。
- `ToolBuildPlan toToolConfig(OperationPreview op, String baseUrl, AuthConfig auth)`：生成该 operation 的 `inputSchema`（由 parameters + requestBody schema 生成 JSON Schema）+ `configJson.paramsMapping`（按 `in`：path/query/header/body）+ `endpoint`(baseUrl+path) + `method`。一个 operation → 一个 tool 配置。

**6.2 命名去重**：导入多个 operation 时，`suggestedName` 冲突加序号后缀（`get_user`、`get_user_2`）；最终落库时由 ToolService 再校验未删除唯一（`TOOL_NAME_DUPLICATE`）。

#### 验收标准
- `mvn package -DskipTests` 通过；用一个公开 OpenAPI spec（如 `https://petstore3.openapi.io` 的 v3 spec 文本）解析，得到 operation 列表 + 单个 operation 的 tool 配置（inputSchema/paramsMapping 正确）。

---

### 任务 7：MCP 连接管理（程序化 McpSyncClient）

#### 目标
按 DB 配置程序化管理每个 MCP Server 的 `McpSyncClient`：启动连已配置、新增即时连、删除/禁用断连、状态标记、断连重连。

#### 需要做

**7.1 `McpClientFactory`**（`infrastructure/client/mcp/`）：按 `transportType` 构造 transport（`HttpClientStreamableHttpTransport(baseUrl, ...)` 或 `HttpClientSseClientTransport(baseUrl, ...)`）+ 认证 Header（`authType`：API_KEY→自定义 header；BEARER→`Authorization: Bearer`）；`McpSyncClient` 用 builder 建（`requestTimeout` = `mcp-handshake`/`requestDefault` 对齐 `13` §4）。具体 API 形态以 spring-ai-mcp-client 实际为准。

**7.2 `McpConnectionManager`**（`domain/mcp/`，`@Component`，注入 `McpServerMapper`、`McpClientFactory`、`ToolProperties`）：
- 内部 `Map<String, McpSyncClient>`（serverId → client，`ConcurrentHashMap`）。
- `connect(McpServerEntity)`：`SsrfGuard.validate(baseUrl)` → factory 建 client → `client.initialize()`（握手，`mcp-handshake` 超时）→ 成功置 `status=ONLINE, lastConnectedAt=now` 并落库；失败置 `status=ERROR, statusMessage`。**常驻保活**，不按需建连。
- `disconnect(serverId)`：`client.close()`，置 `status=OFFLINE`。
- `getClient(serverId)`：返回现有 client（不存在/未连接返回空）。
- **启动连已配置**：实现 `ApplicationRunner`（或 `@EventListener(ApplicationReadyEvent)`）→ 查所有 `enabled=1 AND is_deleted=0` 的 server → 逐个 `connect`（异步，不阻塞启动）。
- **断连重连**：用一个定时任务（`@Scheduled` 每 30s）扫描 `status != ONLINE AND enabled=1` 的 server 重试 `connect`；或用 client 自带的 reconnect 回调（若 spring-ai-mcp 提供）。重连失败保持 `ERROR`。

**7.3 状态联动**：`connect`/`disconnect`/重连结果写 `mcp_server.status`/`status_message`/`last_connected_at`（短事务，仅 DB 写）。

#### 验收标准
- `mvn package -DskipTests` 通过；应用启动时不因 MCP 配置为空报错；手动 insert 一条 MCP server（或用 mock server）验证 `connect` 置 ONLINE、`disconnect` 置 OFFLINE。

---

### 任务 8：MCP 工具发现 + 执行

#### 目标
MCP 工具发现（listTools → 写 tool 表 + toolsChange 增量）、命名去重、执行适配。

#### 需要做

**8.1 发现**（`McpConnectionManager` 或独立 `McpDiscoveryService`）：
- `connect` 成功后调 `client.listTools()` → 每个 remote tool 写/更新 `tool` 表（`source_type=MCP`、`mcp_server_id`、`input_schema`=`McpSchema.Tool.inputSchema()` 序列化、`name` 冲突时改为 `mcpServerName__toolName`）。
- **toolsChange 增量**：若 spring-ai-mcp 提供 tools 变更回调则注册；否则提供 `refresh(serverId)`（手动重发现）：新增工具默认 `enabled=1`、server 端已移除的工具软删（`is_deleted=1`）、已存在工具保留 `enabled`。
- 发现/刷新走短事务（仅 DB 写）。

**8.2 `McpTool`**（`domain/mcp/McpTool.java`，`implements Tool`，持有 tool 快照 + `McpConnectionManager`）：
- `execute(args, ctx)`：`client = connectionManager.getClient(mcpServerId)`；不存在/未连接 → 返回 `ToolExecutionResult{ERROR, "工具 <name> 当前不可用"}`（不抛，降级）。
- 否则 `circuitBreaker.execute(toolId, () -> retryWrapper.withRetry(idempotent=false(默认), deadline, () -> client.callTool(name, args)))`（`13` §9.2）；MCP 默认非幂等。
- 结果 `output = ResponseSizer.truncate(...)`；写 `tool_call_log`（`source_type=MCP, mcp_server_id`）；成功/失败回灌同 HttpTool 规则。

#### 验收标准
- `mvn package -DskipTests` 通过；用 mock MCP server（或现有公开 MCP 端点）验证发现写 tool 表、`callTool` 执行写 log、断连时 `listAvailableTools` 过滤其工具。

---

### 任务 9：ToolFacade 实现（listAvailableTools + executeTool 分派）

#### 目标
实装 `ToolFacade`，统一供 engine 调用。

#### 需要做

**9.1 `ToolFacadeImpl`**（`infrastructure/facade/`，注入 `ToolMapper`、`McpServerMapper`、`HttpTool`/`McpTool` 工厂、`ToolExecutor`、`Semaphore`）：
- `listAvailableTools(toolIds)`：查 `tool where id in (?) and is_deleted=0 and enabled=1`，过滤可用性 `source_type=HTTP OR (source_type=MCP AND 对应 mcp_server.status=ONLINE)`；返回 `List<ToolViewDTO>`（含 inputSchema）。**禁止 SELECT \***，显式列字段。
- `executeTool(command)`：
  1. 查 tool（不存在 → 返回 ERROR `工具不存在`；禁用/不可用 → ERROR 回灌）。
  2. 按 `source_type` 选实现（HTTP→HttpTool、MCP→McpTool、WORKFLOW→P4 暂不支持，返回 ERROR）。
  3. 在 `toolExecutor` 虚拟线程上执行（`Semaphore.acquire(maxConcurrent)`，超时 `ToolBusyException`→ERROR `工具繁忙`）。
  4. catch 一切 `ToolException` → 转 `ToolExecutionResultDTO{ERROR, 回灌文本}`（**不向 engine 抛**，对齐 `13` §6.2）；致命错误（如 ToolCancelledException 由中断触发）才上抛。
  5. 工具实现内部已写 `tool_call_log`，返回 `toolCallLogId`。

> `executeTool` 是 engine 在 ReAct 循环里同步调用的入口；并行多工具由 engine 用 `CompletableFuture.allOf` 在 `toolExecutor` 上调度（engine 侧），`executeTool` 单次执行一个工具。

#### 验收标准
- `mvn package -DskipTests` 通过；跨模块只暴露 DTO；失败返回 ERROR 不抛（除致命）。

---

### 任务 10：MCP Server Service + Controller

#### 需要做

**10.1 `McpServerService`**（`domain/`，注入 `McpServerMapper`、`McpConnectionManager`、`SecretEncryptor`）：
- `create(CreateMcpServerRequest)`：name 未删除唯一（`MCP_SERVER_NAME_DUPLICATE`）；`baseUrl` SSRF 校验（保存时）；`credential` 加密存 `authConfig`；INSERT → `connectionManager.connect` → 返回详情（含 discoveredTools）。
- `list/query/get/update/delete/setEnabled/test/refresh`（行为按 `02` §12.1）。
- 更新 `baseUrl`/认证 → 重连 + 重发现；删除 → 软删 server + 软删其下所有 tool + `disconnect`；`enabled=0` → `disconnect` + 置 OFFLINE。
- `test`：即时建临时连接 → `listTools` 预览 → 关闭（不持久化连接/工具）；超时 15s；返回 `{success,message,latencyMs,discoveredTools}`（不抛，`data.success` 表达）。
- 响应中 `authConfig` 密文不返回，返回 `hasAuth`。

**10.2 `McpServerController`**（`adapter/web/`，`@RequestMapping("/api/tool/mcp-servers")`）：按 `02` §12.1 全部端点（POST 创建、GET 列表 OFFSET、GET 详情、PUT 更新、DELETE、PUT `/{id}/enabled`、POST `/{id}/test`、POST `/{id}/refresh`、POST `/test`（未保存配置测试））。

#### 验收标准
- `mvn package -DskipTests` 通过；Controller 无业务逻辑；测试接口走 `data.success`；凭据不返回。

---

### 任务 11：Tool Service + Controller（HTTP CRUD + OpenAPI 导入 + 测试）

#### 需要做

**11.1 `ToolService`**（`domain/`，注入 `ToolMapper`、`McpServerMapper`、`ToolFacade`（用于执行/校验）、`SecretEncryptor`、`OpenApiParser`）：
- `create(CreateToolRequest)`（HTTP 手动）：name 未删除唯一（`TOOL_NAME_DUPLICATE`）；`endpoint` SSRF；`credential` 加密；`idempotent` 为 null 按 method 推断（GET/HEAD=1，POST/PUT/DELETE/PATCH=0）；INSERT。
- `parseOpenApi(specContent)` → 返回 `OpenApiParseResult`（不持久化）。
- `importOpenApi(ImportOpenApiRequest)`：对 `selected=true` 的 operation 调 `OpenApiParser.toToolConfig` 生成配置 → 批量创建工具（name 冲突加序号）；事务内批量 INSERT。
- `list/query/get/update/delete/setEnabled/test`（按 `02` §12.2）。
- `test(id, args)`：经 `ToolFacade.executeTool`（context 全 NULL，标记手动测试）真实执行，返回结果 + toolCallLogId（已写日志）；失败按 B1 回灌 `status=ERROR`（不抛）。
- 更新仅 HTTP 工具可改配置；MCP 工具不可编辑（仅 enabled，走 `setEnabled`）。
- 响应 `inputSchema`/`configJson`/`authType`/`hasAuth`（密文不返回）。

**11.2 `ToolController`**（`@RequestMapping("/api/tool/tools")`）：按 `02` §12.2 全部端点（POST 创建、POST `/parse-openapi`、POST `/import-openapi`、GET 列表 OFFSET、GET 详情、PUT 更新、DELETE、PUT `/{id}/enabled`、POST `/{id}/test`）。

#### 验收标准
- `mvn package -DskipTests` 通过；OpenAPI 导入产出一批工具且 name 唯一；测试调用写 tool_call_log。

---

### 任务 12：ToolCallLog Service + Controller

#### 需要做

**12.1 `ToolCallLogService`**（`domain/`，注入 `ToolCallLogMapper`）：
- `get(id)` → 详情（含 input/output）。
- `list(ToolCallLogQuery)` → Keyset（按 `created_at DESC, id DESC`）；至少一个过滤维度（conversationId/agentId/toolId），禁止全表扫；列表只返回轻量字段（不返回 input/output）。

**12.2 `ToolCallLogController`**（`@RequestMapping("/api/tool/call-logs")`）：`GET /{id}`、`GET ?conversationId=&agentId=&toolId=&cursor=&limit=`。Controller 层做 opaque 游标编解码（复用 P1 `CursorCodec`）。

#### 验收标准
- `mvn package -DskipTests` 通过；列表命中 `idx_tcl_*` 索引、不返回大字段。

---

### 任务 13：agent 扩展（agent_tool 绑定 + getBoundToolIds）

#### 需要做

**13.1** `AgentToolEntity` + `AgentToolMapper`（agent 模块；任务 2 已建 Entity，这里建 Mapper）。

**13.2** `AgentToolService`（agent 模块 `domain/`，注入 `AgentToolMapper`、`ToolFacade`）：
- `getBoundTools(agentId)` → `{toolIds, tools:[{id,name,description,sourceType,enabled,available}]}`（`available` 经 `ToolFacade.listAvailableTools` 比对）。
- `bindTools(agentId, toolIds)`（`@Transactional`）：经 `ToolFacade.listAvailableTools(toolIds)` 校验——请求集与返回集不一致（缺失/禁用）→ `TOOL_NOT_AVAILABLE`；事务内软删旧绑定、插入新绑定。
- `getBoundToolIds(agentId)` → `List<String>`（供 engine）。

**13.3** `AgentFacade` 增方法：
```java
List<String> getBoundToolIds(String agentId);
```
`AgentFacadeImpl` 转发；`AgentConfigDTO` 增 `boundToolIds: List<String>`（`getAgentConfig` 一并返回）。

**13.4** `AgentController` 增端点（`02` §12.4）：`GET /api/agents/{id}/tools`、`PUT /api/agents/{id}/tools`；`GET /api/agents/{id}` 详情响应增 `toolIds`/`toolSummaries`。

#### 验收标准
- `mvn package -DskipTests` 通过；绑定校验经 ToolFacade（agent→tool 依赖合法）；事务仅 DB 写。

---

### 任务 14：model 扩展（chatStream 带工具 + 双 provider spike）

#### 目标
`ModelFacade.chatStream` 支持下发 tool 定义、返回 toolCalls；**先做 spike 验证 Spring AI 2.0 stream + tool calling**。

#### 需要做

**14.1 spike（必做，先于编码）**：用一段临时代码对 OpenAI 兼容 + Anthropic 各发起一次带 tool 定义的 `ChatModel.stream()`，验证：①`Flux<ChatResponse>` 经 `MessageAggregator`（或手动聚合）能得到 `AssistantMessage.getToolCalls()`；②token 逐块流出；③`dispose` 能取消。验证后删除临时代码。若 API 形态与预期不符，以实测为准并在本任务注释记录。

**14.2 DTO 扩展**（`api/dto/chat/`）：
```java
// ToolDefinitionDTO   name,description,inputSchema(String JSON)
// ToolCallDTO         id,name,args(String JSON)
// ChatMessage 扩展    role(USER/ASSISTANT/SYSTEM/TOOL),content,toolCalls(List<ToolCallDTO>,ASSISTANT 用),toolCallId(String,TOOL 用)
// ChatCompletionCommand 扩展   增 toolDefinitions: List<ToolDefinitionDTO>(可空)
// ChatCompletionResult 扩展    增 toolCalls: List<ToolCallDTO>(可空,finishReason=TOOL_CALLS 时非空)
```

**14.3 `LlmChatGateway.chatStream`** 扩展：把 `toolDefinitions` 透传给 client；client 把结果 `toolCalls` 填进 `ChatCompletionResult`（`finishReason=TOOL_CALLS`）。

**14.4 `OpenAiChatClient`/`AnthropicChatClient`** 扩展：
- 构造 `Prompt` 时，若 `toolDefinitions` 非空，附加 tool 定义（Spring AI 2.0：`ToolCallingChatOptions` 内部不自动执行——`internalToolExecutionEnabled=false` 等价已移除，`ChatModel.stream()` 默认只下发不执行；以 spike 实测的 2.0 API 为准）。
- 聚合 `Flux<ChatResponse>` 后，从最终 `AssistantMessage` 提取 `toolCalls` → `List<ToolCallDTO>`。
- 输入消息映射：`ChatMessage(role=TOOL, content, toolCallId)` → Spring AI `ToolResponseMessage`；`ChatMessage(role=ASSISTANT, toolCalls)` → `AssistantMessage` 带 toolCalls。

> spring-ai 类型（`ToolDefinition`/`AssistantMessage`/`ToolResponseMessage`/`ChatResponse`）全部封装在 model 模块内部，不进 `api/dto`。

#### 验收标准
- `mvn package -DskipTests` 通过；`toolDefinitions=null` 时行为与 P1 完全一致；带工具时模型返回 toolCalls 能被提取为 `ToolCallDTO`；API Key 不泄露。

---

### 任务 15：engine 扩展（ReAct 多轮循环，核心）

#### 目标
手动驱动 ReAct 循环：取工具 → 调 LLM → 有 toolCall 则并行执行 → 回灌 → 再调，直到无 toolCall 或触发终止。

#### 需要做

**15.1 DTO 扩展**（`api/dto/`）：
```java
// ChatMessage 扩展（同 model，engine 自有副本）   role,content,toolCalls,toolCallId,messageId(可选,供 turn 定位)
// ChatTurnResult 扩展   content(最终文本),finishReason(STOP/LENGTH/MAX_TURNS/TIMEOUT/CANCELLED),usage,
//                       newMessages: List<ChatMessage>(本轮新增消息序列),finalAssistantMessageId,
//                       newSummary,newSummaryCoveredMessageId(压缩时非空,本任务先透传 null)
```

**15.2 `ReActLoop`**（`domain/ReActLoop.java`，`@Component`，注入 `AgentFacade`、`ToolFacade`、`ModelFacade`、`@Qualifier("toolExecutor") ExecutorService`、`Semaphore`、`ReactLoopProperties`、`ContextManager`）：
```java
public ChatTurnResult run(ChatTurnCommand cmd, TextStreamSink sink) {
    AgentConfigDTO agent = agentFacade.getAgentConfig(cmd.agentId);           // 校验存在
    List<ToolViewDTO> tools = toolFacade.listAvailableTools(agent.boundToolIds);  // 首轮取，缓存
    List<ToolDefinitionDTO> toolDefs = tools.stream().map(toToolDef).toList();
    Instant deadline = now + loopDeadline;
    List<ChatMessage> messages = contextManager.build(cmd, agent);            // system + summary + 活窗口(含本轮 user)
    List<ChatMessage> newMessages = new ArrayList<>();
    Map<String,Integer> dupMap = new HashMap<>();                             // (toolName,argsHash)→count
    String finalAssistantId = cmd.assistantMessageId;
    for (int turn = 1; turn <= maxTurns; turn++) {
        if (deadline 剩余不足) return finish(TIMEOUT, ...);
        String roundId = (turn==1 ? cmd.assistantMessageId : IdGenerator.uuid());
        sink.onAssistantSegment(roundId);
        // per-round sink wrapper：onDelta(delta) → sink.onDelta(delta)（chat 按 current segment 分组）
        ChatCompletionResult r = modelFacade.chatStream(toModelCmd(agent.modelId, messages, toolDefs), roundSink);
        // 1) 本轮 ASSISTANT 消息（含 r.content 文本 + r.toolCalls）→ newMessages + messages（id=roundId）
        // 2) token 已经经 roundSink 流出
        if (r.toolCalls == null || r.toolCalls.isEmpty()) {
            finalAssistantId = roundId;
            return finish(STOP, r, newMessages, finalAssistantId);            // 无工具调用，正常结束
        }
        // 死循环兜底：对每个 toolCall 算 (name,argsHash)，dupMap 达阈值 → 回灌提示一轮，仍重复 → return finish(MAX_TURNS,...)
        // 并行执行工具：
        sink 各 toolCall → onToolCallStart(roundId, toolCallId, toolName, argsJson)
        List<CompletableFuture<ToolResult>> futures = toolCalls.stream().map(tc ->
            CompletableFuture.supplyAsync(() -> toolFacade.executeTool(toCmd(tc, ctx)), toolExecutor)
        ).toList();
        CompletableFuture.allOf(...).join();                                  // 共享 deadline（各 future orTimeout）
        for (配对) { tr = future.join(); sink.onToolCallEnd(roundId, toolCallId, toolName, status(tr), output(tr), durationMs(tr), tr.toolCallLogId);
                     // TOOL 消息 → newMessages + messages（content=output, toolCallId, toolCallLogId, toolName） }
        // 回到循环顶（下一轮 LLM 会看到 ASSISTANT(toolCalls) + TOOL 历史）
    }
    return finish(MAX_TURNS, ...);                                            // 达最大轮次，正常截断
}
```
> 关键不变量：①每轮 ASSISTANT 消息独立 id（roundId）；②并行工具按 `toolCallId` 配对、结果按完成顺序发 `onToolCallEnd`（前端按 toolCallId 配对）；③`executeTool` 不抛（失败已转 ERROR DTO），回灌为 TOOL 消息让模型自决策；④循环在虚拟线程上同步阻塞（IO 不占 OS 线程）。

**15.3 `ContextManager.build`**：复用 P1 实现，扩展为 `role IN (USER, ASSISTANT, TOOL)` 加载活窗口、按 turn 重建、**摘要/截断以 turn 为整体**（不拆散 toolCall↔TOOL 配对）。本任务先把 turn 边界接好（摘要生成仍可延后到任务 17 完整接入）。

**15.4 取消**：`ChatStreamService` 的 `Future.cancel(true)` → 中断虚拟线程 → `modelFacade.chatStream` 内 `dispose`（P1 已实现）+ 进行中的 `executeTool` future `cancel(true)`（RestClient/McpCall 检测中断中止）。

**15.5 `EngineFacade.runChatTurn`** → `EngineFacadeImpl` 转发到 `ReActLoop.run`。engine 全程不碰 DB。

#### 验收标准
- `mvn package -DskipTests` 通过；engine 无 Mapper/Entity 依赖；循环逻辑可单测（mock ToolFacade/ModelFacade：第一轮返回 toolCalls、第二轮返回 STOP，验证 newMessages 含 ASSISTANT(toolCall)+TOOL+最终 ASSISTANT 三类）。

---

### 任务 16：chat 扩展 A（TextStreamSink + SSE 工具事件 + persistTurn）

#### 需要做

**16.1 `TextStreamSink` 升级**（`zify-common/web/`）：
```java
public interface TextStreamSink {
    void onDelta(String delta);                                                          // model 调（单轮文本）
    default void onAssistantSegment(String assistantMessageId) {}                        // engine 每轮文本段开始
    default void onToolCallStart(String assistantMessageId, String toolCallId, String toolName, String argsJson) {}
    default void onToolCallEnd(String assistantMessageId, String toolCallId, String toolName,
                               String status, String output, long durationMs, String toolCallLogId) {}
}
```
> 用原生类型，不引入业务 DTO；`conversationId` 由 chat 层补入 SSE 事件。P1 仅用 `onDelta` 的调用方不受影响（新方法 default 空）。

**16.2 `ChatStreamService.runTurn` 改造**（注入 `ReActLoop` 经 `EngineFacade`）：
- sink 实现：`onAssistantSegment(id)` 记录 `currentAssistantId`；`onDelta(delta)` → 发 `message_delta` 事件 `{conversationId, assistantMessageId:currentAssistantId, delta}` 并累计到对应文本段；`onToolCallStart(...)` → 发 `tool_call_start`；`onToolCallEnd(...)` → 发 `tool_call_end`（事件载荷按 `02` §9.1）。
- 调 `engineFacade.runChatTurn(cmd, sink)` → `ChatTurnResult`。
- **`persistTurn`**（短事务，仅 DB 写）：批量 INSERT `newMessages`：ASSISTANT(toolCall) 消息（metadata.toolCalls）、TOOL 消息（content=output, metadata={toolCallId,toolName,toolCallLogId}）、最终 ASSISTANT 消息（metadata={modelId,...,finishReason}）；`conversation.message_count += newMessages.size()`、`last_message_at=now`。`id` 用 newMessages 里 engine 给的 messageId/roundId。
- 成功 → 发 `done {conversationId, assistantMessageId: finalAssistantId}`。
- 失败（engine 抛致命异常）→ 不落库 ASSISTANT，发 `run_error`。
- **中断**：已发过 delta 或已完成工具 → `persistTurn` 落库已产出（最终 ASSISTANT `finishReason=CANCELLED`）+ 发 `done`；进行中工具结果不落库（engine 不把它放进 newMessages）。

**16.3 `MessageService.persistTurn`**：新增批量落库方法（替代 P1 `persistAssistantTurn` 单条）；USER 消息落库仍走 P1 的 `send`。

#### 验收标准
- `mvn package -DskipTests` 通过；事务仅 DB 写；curl 触发一轮带工具的对话，SSE 收到 `message_delta`/`tool_call_start`/`tool_call_end`/`done`，DB 中 message 表出现 ASSISTANT(toolCall)+TOOL+最终 ASSISTANT 三类消息、metadata 正确。

---

### 任务 17：chat 扩展 B（上下文重建 + turn 级摘要边界）

#### 需要做

**17.1 `loadActiveWindow` 扩展**（`ChatStreamService`/engine `ContextManager`）：加载历史时 `role IN (USER, ASSISTANT, TOOL)`，按 `created_at ASC`，含本轮 user；映射为 engine `ChatMessage`（ASSISTANT 带 toolCalls、TOOL 带 toolCallId/content）。

**17.2 turn 级摘要**：扩展 P1 摘要压缩（`02` §十）——摘要折叠/尾部截断**以 turn 为整体**（1 USER + 其后 ASSISTANT/TOOL），绝不拆散工具消息序列；摘要触发时把整个旧 turn 折叠进 running summary。摘要生成仍走 `ModelFacade.chatStream`（收集型 sink，不接用户 SSE），不在事务内；`newSummary`/`newSummaryCoveredMessageId` 经 `ChatTurnResult` 回传，chat 落 `conversation.summary_text`/`summary_covered_message_id`（幂等 CAS，沿用 P1）。

#### 验收标准
- `mvn package -DskipTests` 通过；构造长会话（多轮带工具）触发摘要，验证 toolCall↔TOOL 不被拆散、对话仍连贯。

---

### 任务 18：后端端到端联调 + checklist

#### 需要做
1. 配置一个真实 LLM Provider + 模型（P0/P1 已有能力）。
2. 创建一个 HTTP 工具（手动配置，指向公开 API 如 httpbin），绑定到一个 REACT Agent。
3. `curl -N "http://localhost:8080/api/chat/stream?messageId=<userMessageId>"` 触发对话，验证 Agent 自主调用工具：SSE 收到 `tool_call_start`→`tool_call_end`（带 toolCallLogId）→ 后续 `message_delta`（基于结果的回复）→ `done`。
4. OpenAPI 导入：`POST /api/tool/tools/parse-openapi` + `/import-openapi`，验证批量建工具。
5. MCP：连接一个 MCP server（可用本地 mock），验证发现 + 调用 + 断连降级。
6. 中断：流式中断开 SSE，验证取消上游 LLM + 进行中工具。
7. 过 `CLAUDE.md` §10 + P2 `02` §17 偏离项检查清单。

#### 验收标准
- HTTP 工具 ReAct 闭环跑通；`tool_call_log` 记录输入输出；中断取消生效；跨模块只走 Facade；事务不包外部调用；凭据未泄露；SSRF 拒绝内网地址。

---

### 任务 19：前端类型 + API 层

#### 需要做
- `types/tool.ts`：`McpServer*`（Request/Response/Summary/Detail）、`Tool*`（Request/Response/Summary/Detail）、`OpenApiParseResult`/`OperationPreview`/`ImportOpenApiRequest`、`ToolCallLogSummary`/`ToolCallLogDetail`、`ToolListQuery`/`McpServerListQuery`/`ToolCallLogQuery`（Keyset）。
- `types/chat.ts` 扩展：`ChatStreamEvent` 增 `tool_call_start`/`tool_call_end` 分支（按 `02` §11.10）；`MessageView` 增 `toolCalls?: ToolCallView[]`（toolCallId/toolName/args/status/output/durationMs/toolCallLogId）。
- `types/agent.ts` 扩展：`AgentDetailResponse` 增 `toolIds`/`toolSummaries`。
- `api/toolApi.ts`：MCP Server CRUD + test/refresh；Tool CRUD + parse-openapi/import-openapi + test；call-logs（get/list）。会话/工具日志列表返回 `{records,nextCursor,hasMore}`。
- `api/agentApi.ts` 扩展：`getBoundTools`/`bindTools`。
- `api/chatApi.ts` 扩展：`openChatStream` handlers 增 `onToolCallStart`/`onToolCallEnd`。

类型用 `type`，对齐后端 HTTP request/response（**不**对齐 Facade DTO）。

#### 验收标准
- `cd zify-web && npm run build` 通过；字段名与后端 JSON 一致。

---

### 任务 20：前端 工具列表页 + HTTP 工具表单

#### 需要做
- `pages/tools/ToolListPage.tsx`：顶部「新建工具 ▾」（HTTP 工具 / 连接 MCP Server；工作流工具项禁用标 P4）+ 搜索 + 来源/状态筛选；分组卡片（HTTP 工具 / MCP 工具按 server 分组 / 工作流工具占位）；HTTP 卡片（name/desc/method+endpoint/HTTP 标签/启用开关/测试/编辑/删除）；MCP server 卡片（name/baseUrl/状态点 ONLINE🟢/OFFLINE⚫/ERROR🔴/工具数/测试/编辑/启停/删除，可展开显示已发现工具逐个启停）。OFFSET 分页（`useOffsetPagination`）。
- `pages/tools/ToolFormPage.tsx`（`/tools/create?type=http` 与 `/tools/:id/edit`）：Ant Design Steps。① 基础（name/desc）；② 请求（method/endpoint + `ParamSchemaEditor` + headersTemplate 键值表 + bodyTemplate）；③ 鉴权（authType NONE/API_KEY/BEARER + credential 密码框，编辑时 `••••••`）；④ 高级（timeoutSeconds/idempotent 开关）；⑤ 测试（填示例 args → `POST /test` → 显示响应）；⑥ 确认。
- `features/tool/components/ParamSchemaEditor.tsx`：**可视化参数行表**（每行 name/in(path,query,header,body)/type/required/description/secret）→ 实时生成 `inputSchema` JSON Schema + `configJson.paramsMapping`；提供「源码模式」切换（直接编辑 JSON Schema，切回尽量回填）。
- `features/tool/components/HttpToolForm.tsx`：表单主体（含 ParamSchemaEditor）。

#### 验收标准
- `npm run build` 通过；创建/编辑/启停/删除/测试 HTTP 工具可用；可视化参数表单生成正确 JSON Schema；endpoint 命中内网被 SSRF 拒绝（前端展示错误）。

---

### 任务 21：前端 OpenAPI 导入向导 + MCP Server 表单

#### 需要做
- `features/tool/components/OpenApiImportWizard.tsx`（`/tools/create?type=http&mode=openapi`）：① 上传 .json/.yaml 或粘贴 spec → `POST /parse-openapi`；② operation 预览列表（勾选框/method+path/operationId/可编辑 name）；③ 鉴权（统一）；④ 确认 → `POST /import-openapi` → 跳回列表。
- `pages/tools/McpServerFormPage.tsx`（`/tools/create?type=mcp` 与 `/tools/mcp/:serverId/edit`）：name/desc/baseUrl/transportType/authType + credential；「测试连接」→ `POST /test` 显示 discoveredTools 预览；保存后展示已发现工具列表（`DiscoveredToolList`，逐个启停）；编辑页「刷新发现」→ `POST /refresh`。

#### 验收标准
- `npm run build` 通过；OpenAPI 导入产出多个工具；MCP 测试连接展示发现工具；启停/刷新可用。

---

### 任务 22：前端 Agent 工具绑定 + 对话工具卡片 + 日志下钻

#### 需要做
- `features/tool/components/ToolBinder.tsx`：Agent 表单 Step 3 工具绑定多选，按来源分组展示可用工具（HTTP / 按 MCP server），不可用的灰显标「Server 不可用」；保存调 `bindTools`。
- `pages/agents/AgentFormPage.tsx`/`AgentForm.tsx`：Step 3 增工具绑定区（P1 隐藏的步骤激活，知识库/工作流仍隐藏）。
- `features/chat/hooks/useChatStream.ts` 扩展：handlers 增 `onToolCallStart`/`onToolCallEnd`；`onAssistantSegment` 分段（前端按 `assistantMessageId` 分组文本段）。
- `stores/chatStore.ts` 扩展：`MessageView.toolCalls`；actions `appendToolCall`/`updateToolCall`。
- `pages/chat/components/ToolCallTrace.tsx`：实时流 `tool_call_start` 显示「🔧 调用 toolName…」加载态，`tool_call_end` 更新（状态✅/❌、耗时、可折叠 output）；历史回放从 `MessageView.toolCalls` 同渲染；点 `toolCallLogId` → `ToolCallLogDrawer`（`GET /api/tool/call-logs/{id}`）展示完整 input/output/error。
- `app/router.tsx`：加 `/tools`、`/tools/create`、`/tools/:id/edit`、`/tools/mcp/:serverId/edit`。

#### 验收标准
- `npm run build` 通过；Agent 绑定工具后对话中显示工具卡片；下钻日志抽屉显示完整记录；历史回放与实时流渲染一致。

---

### 任务 23：端到端验证 + DoD

#### 需要做
1. 创建 HTTP 工具绑定 Agent → 对话中自主调用并基于结果回答（前端工具卡片 + 下钻日志）。
2. OpenAPI 导入一批工具 → 绑定 → Agent 选用。
3. 连接 MCP Server → 发现 → 调用；断连后工具降级不可见。
4. 中断（停止/SSE 断连）取消上游 LLM + 工具；死循环兜底（同 args 重复 3 次回灌后中断）。
5. 过 `CLAUDE.md` §10 全量检查清单 + P2 `02` §17 偏离项。

#### 验收标准（DoD，对齐 `02` §18.2）
1. HTTP 工具绑定 Agent，对话中自主调用 + 基于结果回答。
2. OpenAPI 导入工具可被 Agent 选用。
3. MCP Server 发现工具可调用；断连降级。
4. `tool_call_log` 记录输入输出/耗时/状态，前端卡片可下钻。
5. 工具有超时；ReAct 循环可中断（取消上游）；并行工具正常；死循环兜底生效。
6. 跨模块只走 Facade；事务未覆盖 LLM/工具调用；凭据未泄露；SSRF 生效。
7. `tool_call_log` 用 Keyset；列表不返回大字段。

---

## 五、禁止实现的内容

1. 不要做 Workflow-as-Tool（P4）、工作流引擎（P4）、知识库 RAG（P3）。
2. 不要引 `spring-ai-starter-mcp-client`（用核心库 `spring-ai-mcp-client` + 程序化管理）；不要走 MCP 连接 auto-config。
3. 不要做 MCP Server（只做 Client）、内置工具库、工具 OAuth、per-tool 并发限流、完整 DNS-rebinding 防护（二期）。
4. 不要让 Spring AI 类型（`ToolDefinition`/`ToolResponseMessage`/`ChatResponse`/`McpClient`）跨 Facade 泄漏；不要让 Tool 实现接口 `ToolCallback`；不要让 engine 用 `ToolCallingManager`。
5. 不要让 engine/chat 直接发起 HTTP/MCP 调用（只经 `ToolFacade`）；不要让 tool 模块读 `agent_tool` 表（归属 agent）。
6. 不要在事务内调 LLM/工具；不要给 tool 执行失败抛 ErrorCode（返回 status=ERROR DTO 回灌）。
7. 不要改 P1 已有的 `CursorPageResult`、`message` 表结构（只扩 metadata JSON）、`EngineFacade` 既有方法签名（新增字段/方法，旧调用方兼容）。
8. 不要给 `tool`/`tool_call_log` 加 SELECT *；不要在列表接口返回 input/output。
9. 不要安装前端新依赖；不要改 `api/request.ts`、`shared/` 既有文件（只新增）。

---

## 六、实施顺序

后端：1（基座）→ 2（Entity/Mapper）→ 3（DTO/接口/异常/执行器）→ 4（运行时保护）→ 5（HttpTool）→ 6（OpenAPI 解析）→ 7（MCP 连接）→ 8（MCP 工具）→ 9（ToolFacade 实现）→ 10（MCP Server CRUD）→ 11（Tool CRUD/导入/测试）→ 12（call-logs）→ 13（agent 绑定）→ 14（model 带工具 + spike）→ 15（ReAct 循环核心）→ 16（chat SSE/persistTurn）→ 17（上下文 turn 摘要）→ 18（后端 E2E）。
前端：19（类型/API）→ 20（列表+HTTP 表单）→ 21（OpenAPI+MCP 表单）→ 22（绑定+对话卡片+下钻）；任务 23 全链路 DoD。
每任务结束构建验证通过再进下一个。

## 七、验证命令

```bash
# 后端
mvn package -DskipTests
java -jar zify-app/target/zify-app-*.jar   # 需 MySQL；P2 不需 Redis
# 前端
cd zify-web && npm run build && npm run lint
```

SSE 联调（带工具的 ReAct）：

```bash
# 1. 发用户消息
curl -X POST http://localhost:8080/api/chat/conversations/<convId>/messages -H 'Content-Type: application/json' -d '{"content":"查一下北京天气"}'
# 2. 用返回的 userMessageId 开流
curl -N "http://localhost:8080/api/chat/stream?messageId=<userMessageId>"
# 期望事件序列：message_delta* → tool_call_start → tool_call_end → message_delta* → done
```

## 八、输出要求

每个任务输出：① 新增/修改文件（完整路径）；② 该任务实现的功能（一句）；③ 构建结果（通过/失败+关键错误）；④ 未完成事项。构建失败必须贴完整错误并说明修复方案，不得隐瞒。

全部完成后额外输出：① 全部 API/SSE 接口清单（tool/agent 增量）；② 跨模块 Facade 使用点（engine→{Agent,Tool,Model}Facade）；③ DoD 逐条核对结果；④ Spring AI tool-calling 实测结论（spike 记录：stream+tool 的实际 API 形态、`dispose` 取消是否生效）；⑤ P3/P4 接入时的复用点（ToolFacade 如何被工作流 Tool 节点复用、tool_call_log 如何记 workflow_run_id）。
