# P2 工具能力 + ReAct 多轮循环 — 最终技术决策

> 本文档是 P2（工具能力 + ReAct 多轮循环）的**最终技术决策总结**，整合 `docs-research/phase-P2/00-pending-tech-decisions.md` 的 13 题决策（A1/B2/A2/B1/C1-C7/D1/D2 全部 ✅），作为 P2 开发的技术依据。
> 决策溯源见上述清单（每题含背景/备选/理由/连带影响）。
> 依据：`glm-docs/02`(模块)、`glm-docs/07`(LLM 调用)、`glm-docs/11`(数据模型)、`glm-docs/13`(工具调用规范，P2 新建)、`glm-docs/12` §五(路线图 P2)、`docs-prd/phase-P1/*`(P1 已定边界与协议)。

---

## 一、P2 范围与模块

P2 在 P1 核心对话闭环之上，把「单轮 LLM」升级为**真正的 ReAct 多轮循环**——Agent 自主决策、调用 HTTP/MCP 工具、观察结果、再决策，直到完成任务。

| 模块 | P2 做什么 |
|------|---------|
| **tool**（新建） | `mcp_server`/`tool`/`tool_call_log` 表；统一 Tool 接口；HTTP 工具（手动配置 + OpenAPI 解析）；MCP Client（连接/发现/调用）；工具管理；`ToolFacade` 实装；`tool_call_log` 写入（执行点即记录点） |
| **engine**（扩） | ReAct 多轮循环（Spring AI User-Controlled streaming）；循环控制（终止/中断/死循环兜底）；并行工具执行；`ChatTurnResult` 扩展返回本轮消息序列 |
| **chat**（扩） | 工具消息持久化（ASSISTANT toolCall + TOOL 消息进 message 表）；SSE 协议扩展（tool_call 事件）；上下文重建（含 TOOL 消息、turn 级摘要） |
| **agent**（扩） | `agent_tool` 关联表；Agent 表单工具绑定步骤激活 |

P2 新增 4 张 MySQL 表：`mcp_server`(3)、`tool`(4)、`tool_call_log`(19)、`agent_tool`(6)。**注意**：`tool_call_log` 归 **tool 模块**（原 glm-docs/11 归 engine，P2 修订）。

---

## 二、关键架构决策

### 2.1 ReAct 循环机制（A1）— Spring AI User-Controlled streaming

**决策**：复用 P1 的 `ChatModel.stream()`，engine 手动驱动 ReAct 多轮循环（`while(hasToolCalls())`）。

**落地形态**：
1. `ChatModel` 直接调用（2.0 起 `internalExecutionEnabled` 已移除，`ChatModel.stream()` 默认只下发 tool 定义、**不自动执行**）。
2. 每轮 `chatModel.stream(prompt)` 用 `MessageAggregator` 聚合 chunks，token 流 forward 到 `TextStreamSink`（SSE）。
3. 聚合后若 `hasToolCalls()` → engine 调 `ToolFacade.executeTool`（中立 DTO）执行 → 结果作为 TOOL 消息加回 messages。
4. 循环至无 tool call。

**为何不用 ChatClient + ToolCallingAdvisor 自动循环**：会绕过 P1 在 `LlmChatGateway` 的 `ProviderBulkhead`/`LlmRetryWrapper`/解密/超时封装。手动循环让每轮仍走 `ModelFacade.chatStream`，per-call 超时/重试/隔离全保留。

**澄清 `glm-docs/02` §1**：「不做 Function Calling 策略，ReAct 已覆盖」是**产品形态**层面（不单做单轮 FC 应用）；技术实现用 Provider 原生 tool calling 作手段，不冲突。

### 2.2 中立化边界（B2 + C1 修正）— 三模块分工

Spring AI 类型不能跨 Facade 泄漏（§3），由此确定三模块职责与类型边界：

| 模块 | 职责 | 碰 spring-ai 类型？ |
|------|------|------------------|
| **tool** | 工具定义存储 + HTTP/MCP 执行 + tool_call_log | ❌ 接口中立（infra 层可用 `spring-ai-mcp-client`，C1 修正） |
| **engine** | ReAct 循环编排（轮次/中断/SSE 事件/上下文） | ❌ 只用中立 DTO |
| **model** | 单轮带 tool 的流式 LLM 调用 | ✅ 封装 `ToolDefinition`/`ToolResponseMessage`/`ChatResponse` |

**为何如此**：
- engine 用不了 `ToolCallingManager`——需 `ChatResponse`，按 §3 不能跨 Facade 泄漏。
- engine 不能直接 `chatModel.stream()`——会持有解密后的 `ChatModel`（含 API Key），违反 §6。
- 循环必须在 engine（engine=编排）；每轮走 `ModelFacade.chatStream`（单轮带 tool 定义下发）复用网关。

**C1 对 B2 的修正**：tool 模块「中立」从「pom 零 spring-ai」精确为「**接口中立**」——`ToolFacade`/`Tool` 接口不含 spring-ai 的 LLM 抽象（`ChatModel`/`ToolCallback`/`ChatResponse`），但 infrastructure 层可用 `spring-ai-mcp-client`（MCP 协议实现，与 HTTP 工具用 RestClient 同性质）。

### 2.3 ReAct 循环时序（端到端）

```text
chat (SSE) → EngineFacade.runChatTurn(cmd, sink)
  engine 循环 (虚拟线程):
   ┌─ 1. ToolFacade.listBoundTools(agentId) → List<ToolViewDTO>（首轮取，缓存）
   │  2. 构造中立 tool 定义 → ModelFacade.chatStream(cmd{messages, toolDefs}, sink)
   │       └► model 内部：ToolDef→ToolCallingChatOptions、ChatMessage→spring Message、stream+聚合、返回 toolCall 请求 DTO
   │  3. sink 推 message_delta（token）→ 前端
   │  4. 若 model 返回 toolCalls:
   │       - sink 推 tool_call_start(每个工具)
   │       - 并行 ToolFacade.executeTool(toolId,args,ctx) → tool 内部写 tool_call_log + 返回 DTO
   │       - sink 推 tool_call_end(每个工具，带 toolCallLogId)
   │       - 结果作为 TOOL 消息加回 messages
   │       - 回 2（下一轮）
   └─ 5. 无 toolCall → 返回 ChatTurnResult{newMessages, finishReason, ...}
  chat 落库 newMessages（含工具消息）→ 发 done
```

---

## 三、统一工具系统

### 3.1 `ToolFacade` 契约（B2，中立，`com.zify.tool.api.ToolFacade`）

```java
List<ToolViewDTO> listBoundTools(String agentId);          // engine 首轮取全量、内存缓存
ToolExecutionResultDTO executeTool(ToolExecutionCommand command);
```

- `listBoundTools` 只返回可用工具（`tool.enabled=1 AND is_deleted=0 AND (source_type=HTTP OR mcp_server.status=ONLINE)`）；`name` 全局唯一。
- 跨模块只暴露 DTO，不返回 Entity/分页（§3）。

### 3.2 DTO（`com.zify.tool.api.dto`，全部中立）

| DTO | 字段 |
|-----|------|
| `ToolViewDTO` | `id, name, description, inputSchema(String JSON), sourceType(HTTP/MCP/WFT)` |
| `ToolExecutionCommand` | `toolId, args: Map<String,Object>, context: ToolExecContext` |
| `ToolExecContext` | `conversationId, agentId, turn`（不发给模型，纯审计） |
| `ToolExecutionResultDTO` | `status(SUCCESS/ERROR), output(String), durationMs, error?` |

### 3.3 tool 模块内部统一 Tool 接口（`domain`，中立，不跨 Facade）

```java
interface Tool {
    ToolView toView();
    ToolExecutionResult execute(Map<String,Object> args, ToolExecContext ctx);
}
```

HTTP/MCP/Workflow-as-Tool（P4）各自实现；`ToolFacadeImpl` 按 toolId 选实现执行。

### 3.4 HTTP 工具（C3）

- **两种定义方式同构**：手动配置（填 endpoint/method/header/body 模板 + inputSchema）与 OpenAPI 解析（导入 spec 自动提取）产出同构的 `tool` 表配置。
- **OpenAPI 解析**：3.0/3.1；一个 operation → 一个 tool（Swagger Parser `io.swagger.parser.v3:swagger-parser-v3`，新依赖）。
- **鉴权加密**：Header/Body token/API Key 加密存储，复用 `common.SecretEncryptor`；明文仅执行时解密、不记录不返回。
- **参数映射**：args 按 OpenAPI `in` 映射 path/query/header/body。

### 3.5 MCP 工具（C1 + C2）

- **传输**：只做 Streamable-HTTP + SSE（不做 stdio）。
- **客户端**：`spring-ai-starter-mcp-client`（HttpClient 版，SYNC）+ `spring.ai.mcp.client.toolcallback.enabled=false`（关闭 ToolCallback auto-config）+ 自适配 `McpClient`→中立 Tool。
- **连接生命周期**：常驻保活（启动连已配置 server + 新增即时建连）+ 连接时一次性 `listTools` 发现 + `toolsChangeConsumer` 增量更新 tool 表 + 断连重连 + `mcp_server` 状态标记（ONLINE/OFFLINE/ERROR）降级 + 单连接复用。
- **工具发现粒度**：每个 remote tool 一条 `tool` 表记录（`source_type=MCP`、`mcp_server_id`、`input_schema`）。

### 3.6 工具生命周期与运行时校验（D2）

- 禁用（`enabled=0`）/软删/断连 → `listBoundTools` 过滤（本轮 LLM 看不到）；`agent_tool` 关联不自动删。
- 命名冲突：HTTP 工具用户起名校验 `name` 未删除唯一；MCP 工具冲突加前缀 `mcpServerName__toolName`（Zify 自实现，因关闭了 spring-ai-mcp prefix generator）。
- 绑定校验：保存时校验存在+enabled（即时反馈）+运行时 `listBoundTools` 再校验（对齐 P1 A-07）。

---

## 四、ReAct 多轮循环

### 4.1 循环控制（C4）

- **终止**：①模型 `finishReason=STOP` 无 tool call；②**最大轮次**（默认 10，可配）→ 落库已产出（`MAX_TURNS`，视作截断不报错）；③token 累计接近窗口 → A2 的 turn 级摘要压缩，仍超则中断。
- **死循环兜底**：主兜底=最大轮次；增强=同一 `(toolName, args)` 连续重复 3 次 → 回灌「检测到重复调用，请换方法」一轮，仍重复则中断。
- **用户中断**（SSE 断连/停止）：取消链 SSE 断连 → chat `Future.cancel` → engine 中断循环 + 取消进行中工具 → model 取消当前 stream；已产出文本+已完成工具结果落库（`CANCELLED`），进行中工具取消不落库。
- **超时分层**：单次 LLM stream 120s（07）/单工具 30s 可配（B1）/整轮循环 deadline 120s（从用户发消息起，对齐 SseEmitter）。

### 4.2 并行工具 + 线程（C6）

- 模型一次返回多 tool call → **并行执行**（独立 IO 提效）；各独立超时+熔断+共享循环 deadline；结果按 `toolCallId` 配对。
- **工具执行器**：独立 `ToolExecutor`（`newVirtualThreadPerTaskExecutor` + 全局 `Semaphore` 50），与 `llmTaskExecutor` 隔离。per-tool 限流留二期。
- **同步阻塞模型**：engine 循环在虚拟线程同步调 model+tool（阻塞 IO 不占 OS 线程）；并行多工具用 `CompletableFuture.allOf`。
- **SSE 时序**：`tool_call_start(A/B)` 提交时发 → `tool_call_end` 各完成时发（乱序，前端按 toolCallId 配对）→ 全部完成 → 下一轮 `message_delta`。

---

## 五、持久化与上下文

### 5.1 工具过程进 message 表（A2）

- 工具调用过程（ASSISTANT toolCall 请求 + TOOL 响应）**落 message 表**——上下文正确性硬要求（模型继续对话需之前的工具上下文）。
- P1 message 表 role 已预留 TOOL，**无需改列**；扩展 `metadata` JSON：
  - `ASSISTANT`(toolCall)：增 `toolCalls:[{id,name,args(JSON)}]`；
  - `TOOL`：`content`=工具结果 output，metadata=`{toolCallId,toolName,toolCallLogId}`；
  - 最终 ASSISTANT 文本：metadata 不变（modelId/tokens/finishReason）。
- engine **保持纯编排**（P1 §2.1 不改）：engine 不碰 DB；`ChatTurnResult` 扩展返回 `List<ChatMessage> newMessages`（本轮消息序列），chat 批量落库（短事务，事务内只 DB 写）。`persistAssistantTurn`→`persistTurn`。

### 5.2 tool_call_log（A2 + D1）

- **归 tool 模块**（修订 glm-docs/11，原归 engine），由 `ToolFacade.executeTool` 内部写（执行点即记录点）。理由：要同时服务对话（engine 经 ToolFacade）和工作流（workflow 经 ToolFacade，P4），唯一能被两者依赖的是 tool 模块。
- **字段**（17 列，glm-docs/10/11 编号 19）：`id`/标准审计/`tool_id`/`tool_name`(快照)/`source_type`(MCP/HTTP/WFT)/`mcp_server_id`(NULL)/`agent_id`(NULL)/`conversation_id`(NULL)/`workflow_run_id`(NULL,P4)/`workflow_node_run_id`(NULL,P4)/`turn`(NULL)/`tool_call_id`(关联 TOOL message)/`input`(JSON,截断后)/`output`(LONGTEXT,截断后)/`status`(SUCCESS/ERROR/TIMEOUT/CIRCUIT_OPEN/CANCELLED)/`duration_ms`/`error`(TEXT NULL)。
- **存储**：input/output 存截断后内容（C7 响应 32KB 阈值）；4 索引（conv/agent/tool/created_at）；一期不分区，P5 归档按 created_at 分批物理删除；禁止 SELECT *。
- **关联**：TOOL message `metadata.toolCallLogId` 指向 tool_call_log 主键，前端可下钻。

### 5.3 上下文重建与摘要（A2）

- **历史回放**：`loadActiveWindow` 扩展为 `role IN (USER, ASSISTANT, TOOL)`，按 turn 重建（与 SSE 实时流同一套渲染）。
- **turn 级摘要**：turn = 1 USER + 其后所有 ASSISTANT/TOOL；摘要折叠/截断**以 turn 为整体**，绝不拆散工具消息序列。

---

## 六、SSE 协议扩展（C5）

在 P1 三事件（`message_delta`/`done`/`run_error`）基础上新增两事件，**沿用 P1 两步协议**（GET SSE，无新端点）：

| 事件 | 时机 | data 载荷 |
|------|------|----------|
| `tool_call_start`（新增） | 模型决定调工具、engine 即将执行 | `{ conversationId, assistantMessageId, toolCallId, toolName, args(JSON) }` |
| `tool_call_end`（新增） | 工具执行完 | `{ conversationId, assistantMessageId, toolCallId, toolName, status(SUCCESS/ERROR), output, durationMs, toolCallLogId }` |

- **不需要 turn 事件**：靠 `assistantMessageId` 分段（多轮每轮 LLM 输出独立 id）。
- **前端渲染**：文本段按 `assistantMessageId` 分组；工具卡片（`tool_call_start`→`tool_call_end`）内联折叠，可点 `toolCallLogId` 下钻。
- **历史回放=实时流同渲染**：message 表 role+metadata → 同一 `MessageView`（增 `toolCalls`）。
- **sink 回调需扩展**：P1 `TextStreamSink`（仅 `onDelta`）升级为支持工具事件（`onDelta`/`onToolCallStart`/`onToolCallEnd`）。

---

## 七、工具调用规范（B1）

详见 `glm-docs/13-zify-tool-calling-spec.md`。要点：

- **超时**：连接 10s / MCP 握手 15s / 单次请求 30s（可配 `tool.timeout_seconds`）/ 总 ≤60s。
- **重试（幂等驱动，核心差异）**：建连失败可重试（请求未送达）；请求发出后按幂等性（非幂等不重试，防重复副作用）；4xx 不重试。显式 wrapper，max 2/退避 2/jitter 20%。
- **熔断**：per `tool_id`，连续 5 次可重试失败 → OPEN 60s → HALF_OPEN 探测。
- **失败回灌**：`executeTool` 返回 status=ERROR（不抛），engine 回灌模型，不中断循环（致命错误除外）。
- **异常**：`ToolException` 体系（Retryable/NonRetryable/Timeout/Busy/CircuitOpen），对 engine 只暴露 DTO。

---

## 八、安全（C7）

- **SSRF 黑名单**（默认开）：禁解析到内网/保留地址（127/10/172.16/192.168/169.254 含云元数据/0.0/100.64；IPv6 ::1/fc00/fe80）+ DNS 检查（一期基础防护，完整 DNS-rebinding 防护留二期）；保存时+运行时校验；MCP Server URL 同校验。
- **大小限制**：响应 32KB 截断（标 `truncated`）+ 请求体 1MB 上限。
- **Header 脱敏**：`Authorization`/`Cookie`/`Set-Cookie` 不明文记入 tool_call_log/output。

---

## 九、数据模型（P2 新增/扩展）

### 9.1 新增表（对齐 glm-docs/10/11）

| 表 | 模块 | 说明 |
|----|------|------|
| `mcp_server`(3) | tool | MCP Server 连接配置（`base_url`/`transport_type`/`auth_config`/状态 `ONLINE`/`OFFLINE`/`ERROR`） |
| `tool`(4) | tool | 统一工具定义（`name`唯一/`description`/`input_schema`/`source_type`/`mcp_server_id`/HTTP 配置 `endpoint`/`method`/`params_mapping`/`headers_template`/`body_template`/`auth_config`加密/`timeout_seconds`/`idempotent`/`enabled`） |
| `tool_call_log`(19) | tool | 工具调用日志（17 列，见 §5.2，大表） |
| `agent_tool`(6) | agent | Agent↔工具 N:N 关联 |

### 9.2 P1 表扩展

- `message`：**不改列**，仅扩 `metadata` JSON 结构（toolCalls/toolCallId/toolCallLogId）。
- `conversation`：不变（摘要列已就位）。

### 9.3 迁移脚本

`V6__tool__create_tool_tables.sql`（mcp_server/tool/tool_call_log）、`V7__agent__create_agent_tool.sql`（agent_tool）。

---

## 十、Facade 契约汇总

### 10.1 `ToolFacade`（tool，新建，见 §3.1）

### 10.2 `ModelFacade`（model，扩展）

`chatStream(ChatCompletionCommand, TextStreamSink)` 扩展：command 增 `toolDefinitions`（可空，P1 零工具时为 null 行为不变）；返回 `ChatCompletionResult` 增 `toolCalls`（可空，finishReason=TOOL_CALLS 时非空）。spring-ai 类型封装在 model 内部。

### 10.3 `EngineFacade`（engine，扩展）

`runChatTurn(ChatTurnCommand, TextStreamSink)` → `ChatTurnResult`：`content` 保留（最终文本）+ 新增 `List<ChatMessage> newMessages`（本轮消息序列，含工具消息）+ `finishReason` 增 `MAX_TURNS`/`TIMEOUT`。engine 不碰 DB（P1 §2.1 保持）。

---

## 十一、配置项汇总（application.yml 增量）

```yaml
spring:
  ai:
    mcp:
      client:
        type: SYNC
        toolcallback:
          enabled: false              # 关闭 ToolCallback auto-config

zify:
  chat:
    react:
      max-turns: 10
      loop-deadline: 120s
      dup-tool-call-threshold: 3
  tool:
    timeout:
      connect: 10s
      mcp-handshake: 15s
      request-default: 30s
      total-cap: 60s
    circuit-breaker:
      failure-threshold: 5
      open-duration: 60s
    executor:
      max-concurrent: 50
    security:
      ssrf:
        enabled: true
        allow-private: false
      response-max-bytes: 32768
      request-max-bytes: 1048576
```

新依赖：`spring-ai-starter-mcp-client`（tool pom）、`io.swagger.parser.v3:swagger-parser-v3`（tool pom）。

---

## 十二、实现顺序与验收

### 12.1 实现顺序（对齐 CLAUDE.md §2）

1. **tool 基座**：迁移 V6/V7 → `mcp_server`/`tool`/`tool_call_log`/`agent_tool` 表 → `ToolFacade` + DTO + 内部 Tool 接口 → `ToolExecutor`（虚拟线程+Semaphore）+ `ToolException` 体系 + 工具调用规范（超时/重试/熔断，glm-docs/13）。
2. **HTTP 工具**：手动配置 + OpenAPI 解析（Swagger Parser）+ 参数映射 + 鉴权加密（SecretEncryptor）+ SSRF/大小限制。
3. **MCP 工具**：spring-ai-mcp-client 接入 + 关闭 ToolCallback + 自适配 + 连接生命周期（常驻/toolsChange/状态标记）。
4. **engine 扩展**：`ModelFacade.chatStream` 带工具 + `ChatTurnResult` 返回消息序列 + ReAct 循环（含控制 C4/并行 C6）。
5. **chat 扩展**：工具消息持久化（persistTurn）+ SSE 协议（tool_call 事件）+ 上下文重建（loadActiveWindow 含 TOOL/turn 摘要）。
6. **agent 扩展**：工具绑定步骤。
7. 全链路联调 + 过 CLAUDE.md §10 检查清单 + A1 双 provider spike（验证 stream+tool calling）。

### 12.2 验收标准（DoD，对齐 glm-docs/12 §五）

1. 创建 HTTP 工具并绑定 Agent，Agent 对话中自主调用并基于返回结果回答。
2. 连接 MCP Server，自动发现工具，Agent 可调用已启用工具。
3. `tool_call_log` 记录每次调用输入输出，可调试；前端工具卡片可下钻。
4. 工具调用有超时；ReAct 循环可中断（用户停止/SSE 断连取消上游）；并行工具调用正常。
5. 跨模块只走 Facade；事务未覆盖 LLM/工具调用；API Key/鉴权凭据未泄露；SSRF 防护生效。

---

## 十三、已回写/需回写的正式文档

| 文档 | 状态 | 内容 |
|------|------|------|
| `glm-docs/11` | ✅ 已回写 | tool_call_log 归属 engine→tool；关系 message(TOOL) 1:1 tool_call_log；§一/§二/§四/§五 |
| `glm-docs/02` §1 | ✅ 已回写 | ReAct 实现机制澄清脚注 |
| `glm-docs/07` §一 | ✅ 已回写 | 工具调用指向 glm-docs/13 |
| `glm-docs/13` | ✅ 新建 | 工具调用技术方案（B1/C1/C2/C3/C6/C7 整合） |
| `CLAUDE.md` §11 | ✅ 已回写 | 文档索引加 13 |
| `docs-prd/phase-P1/02` §5.2 | 待落实 | SSE 协议表 + types/chat.ts ChatStreamEvent 扩展（C5，P2 编码时） |
| `docs-prd/phase-P1/02` §5.5 | 待落实 | 摘要压缩 turn 边界语义（A2） |
| `docs-prd/phase-P1/01` §四 | 待落实 | message.metadata JSON 结构扩展（A2） |
| `glm-docs/06` §11.7 | 待落实 | 前端 SSE useChatStream 工具事件分支（C5） |
