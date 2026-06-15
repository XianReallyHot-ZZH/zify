# P2 工具能力 + ReAct 多轮循环 — 功能规格说明

> 本文档定义 P2（工具能力 + ReAct 多轮循环）的全部产品功能、边界、前端交互、后端功能与接口设计。
> 数据库设计见 `01-data-model.md`。
> 依据：`glm-docs/02`（模块）、`glm-docs/03`（前端）、`glm-docs/06`（代码组织）、`glm-docs/07`（LLM 调用）、`glm-docs/13`（工具调用规范）、`glm-docs/12` §五（路线图 P2）、`docs-prd/phase-P1/*`（P1 已定边界与协议）、`docs-prd/phase-P2/00-technical-decisions.md`（13 项最终决策）。

---

## 一、模块定位与范围

P2 把 P1 的「单轮 LLM」升级为真正的 ReAct 多轮循环——Agent 能自主决策、调用 HTTP/MCP 工具、观察结果、再决策，直到完成任务。

### 1.1 涉及模块

| 模块 | P2 做什么 | 跨模块接口 |
|------|---------|-----------|
| **tool**（新建） | `mcp_server`/`tool`/`tool_call_log` 表；统一 Tool 接口；HTTP 工具（手动配置 + OpenAPI 解析）；MCP Client（连接/发现/调用）；工具管理；工具调用规范（超时/重试/熔断/安全）；`tool_call_log` 写入 | 定义 `ToolFacade`（中立 DTO，供 engine 调用） |
| **engine**（扩） | ReAct 多轮循环（手动驱动）；循环控制（终止/中断/死循环兜底）；并行工具执行；`ChatTurnResult` 返回本轮消息序列；上下文重建含 TOOL 消息 | `EngineFacade.runChatTurn` 扩展 |
| **chat**（扩） | 工具消息持久化（ASSISTANT toolCall + TOOL 消息进 `message`）；SSE 协议扩展（`tool_call_start`/`tool_call_end`）；上下文重建（含 TOOL，turn 级摘要） | — |
| **agent**（扩） | `agent_tool` 关联表；Agent 表单「工具绑定」步骤激活；`AgentFacade.getBoundToolIds` | `AgentFacade` 扩展 |
| **model**（扩） | `ModelFacade.chatStream` 支持带工具的单轮流式调用（下发 tool 定义、返回 toolCalls） | `ModelFacade.chatStream` 扩展 |

### 1.2 模块依赖（pom 已声明，P2 无需改 pom）

```text
tool      → common
engine    → common, agent, model, tool (+ knowledge/workflow，P2 不调用)
agent     → common, model, tool (+ knowledge/workflow，P2 调 tool 校验绑定)
chat      → common, agent, engine
```

### 1.3 功能总览

| 功能 | P2 | 说明 |
|------|----|------|
| MCP Server 连接管理（增删改查 + 测试 + 刷新发现） | ✅ | Streamable HTTP/SSE；常驻保活；连接状态标记 |
| MCP 工具自动发现 + 逐个启用/禁用 | ✅ | 连接/刷新时 `listTools` → 写 `tool` 表 |
| HTTP 工具手动配置（可视化参数表单 → JSON Schema） | ✅ | endpoint/method/参数映射/Header/Body 模板 + 鉴权加密 |
| HTTP 工具 OpenAPI 导入（预览 + 勾选） | ✅ | 3.0/3.1，一个 operation → 一个 tool |
| 工具列表（按来源分组 + 启用/禁用 + 编辑 + 删除 + 测试） | ✅ | HTTP/MCP 分组；Workflow 分组占位（P4） |
| Agent 工具绑定（多选） | ✅ | P1 隐藏的步骤激活 |
| ReAct 多轮循环（LLM 决策 → 调工具 → 观察 → 再决策） | ✅ | 手动驱动，复用 P1 网关封装 |
| 并行工具调用 | ✅ | 模型一次多 tool call 并行执行 |
| 对话区工具调用过程展示（卡片 + 折叠 + 下钻日志） | ✅ | SSE `tool_call_start`/`tool_call_end` + 历史回放同渲染 |
| 工具调用日志（输入/输出/耗时/状态，可下钻） | ✅ | `tool_call_log`（归 tool 模块） |
| 工具调用超时/重试（幂等驱动）/熔断/失败回灌 | ✅ | 遵循 `glm-docs/13` |
| SSRF 防护 + 响应/请求大小限制 + Header 脱敏 | ✅ | 遵循 `glm-docs/13` §8 |
| Workflow-as-Tool | ❌ P4 | 工具列表「工作流工具」分组占位 |
| 内置工具库 / MCP Server（只做 Client） / 工具 OAuth | ❌ 不做 | 对齐 `glm-docs/02` §3 |
| per-tool 并发限流 / 完整 DNS-rebinding 防护 | ❌ 二期 | 全局并发 + 基础 DNS 检查 |

---

## 二、关键架构决策（产品视角）

P2 的 13 项技术决策详见 `00-technical-decisions.md`，以下是其产品级落地要点。

### 2.1 ReAct = Provider 原生 tool calling + engine 手动驱动多轮循环

- **机制**：复用 P1 的 `ModelFacade.chatStream`，engine 手动驱动 `while(有 tool call)` 循环。每轮仍走 `ModelFacade.chatStream`（单轮、下发 tool 定义），**per-call 超时/重试/隔离全保留**（P1 网关封装零返工）。
- **不用** Spring AI `ToolCallingManager`/`ChatClient` 自动循环：会绕过 P1 网关封装、且 `ChatResponse` 等 Spring AI 类型不能跨 Facade 泄漏（§3 边界）。
- 澄清 `glm-docs/02` §1「不做 Function Calling 策略，ReAct 已覆盖」：此为**产品形态**（不单做单轮 FC 应用），**不禁止**用 Provider 原生 tool calling 作实现手段。

### 2.2 三模块分工 + 中立 DTO 边界（核心）

Spring AI 类型不能跨 Facade 泄漏（§3），由此确定职责与类型边界：

| 模块 | 职责 | 碰 spring-ai 类型？ |
|------|------|------------------|
| **tool** | 工具定义存储 + HTTP/MCP 执行 + tool_call_log | ❌ 接口中立（infra 层可用 `spring-ai-mcp-client`，仅 MCP 协议实现） |
| **engine** | ReAct 循环编排（轮次/中断/SSE 事件/上下文） | ❌ 只用中立 DTO |
| **model** | 单轮带 tool 的流式 LLM 调用 | ✅ 封装 `ToolDefinition`/`ToolResponseMessage`/`ChatResponse` |

### 2.3 工具调用过程进 `message` 表；`tool_call_log` 归 tool 模块

- 工具调用过程（ASSISTANT toolCall 请求 + TOOL 响应）落 `message` 表（上下文正确性硬要求）；`message` 不改列，仅扩 `metadata` JSON（见 `01` §3.5）。
- `tool_call_log` 由 `ToolFacade.executeTool` 内部写（执行点即记录点），engine 不碰 DB（P1 §2.1 保持）。
- `ChatTurnResult` 从「单 content」扩展为返回本轮新增消息序列 `List<ChatMessage> newMessages`，chat 批量落库（短事务，事务内只 DB 写）。

### 2.4 工具系统对所有调用方中立

统一 Tool 接口（`name`/`description`/`inputSchema`/`execute`）由 ReAct 消费方（engine）定义需求；HTTP/MCP 各自实现，调用方不感知背后是哪种工具。一期复用此接口支撑 P4 工作流 Tool 节点与 Workflow-as-Tool。

---

## 三、统一工具系统（tool 模块）

### 3.1 `ToolFacade` 契约（中立，`com.zify.tool.api.ToolFacade`）

```java
// 供 engine 取「这些 ID 中可用的工具视图」（过滤 enabled/未删/来源可用）
List<ToolViewDTO> listAvailableTools(Collection<String> toolIds);

// 执行工具（内部写 tool_call_log，返回中立结果 DTO，不向 engine 抛异常）
ToolExecutionResultDTO executeTool(ToolExecutionCommand command);
```

> **边界修正**（相对 `00-technical-decisions.md` §3.1 的 `listBoundTools(agentId)`）：`agent_tool` 归 **agent 模块**，tool 模块不能读它。故拆为：engine 先 `AgentFacade.getBoundToolIds(agentId)` 取绑定 ID，再 `ToolFacade.listAvailableTools(ids)` 取视图。两步都满足依赖方向（`engine→agent`、`engine→tool`）。

### 3.2 DTO（`com.zify.tool.api.dto`，全部中立）

| DTO | 字段 |
|-----|------|
| `ToolViewDTO` | `id`, `name`, `description`, `inputSchema`(String JSON), `sourceType`(HTTP/MCP/WORKFLOW) |
| `ToolExecutionCommand` | `toolId`, `args: Map<String,Object>`, `context: ToolExecContext` |
| `ToolExecContext` | `conversationId`(nullable), `agentId`(nullable), `turn`(nullable)（纯审计，不发给模型） |
| `ToolExecutionResultDTO` | `status`(SUCCESS/ERROR), `output`(String), `durationMs`, `toolCallLogId`, `error?` |

### 3.3 tool 模块内部统一 Tool 接口（`domain`，中立，不跨 Facade）

```java
interface Tool {
    ToolView toView();
    ToolExecutionResult execute(Map<String,Object> args, ToolExecContext ctx);
}
```

`HttpTool` / `McpTool` 各自实现；`ToolFacadeImpl` 按 toolId 选实现执行（与 §3「Facade 不返回 Entity」不冲突——对外只暴露 DTO）。

### 3.4 工具调用规范（遵循 `glm-docs/13`，要点）

- **超时**（分层 + 可配）：连接 10s / MCP 握手 15s / 单次请求 30s（可被 `tool.timeout_seconds` 覆盖）/ 总 ≤60s。
- **重试（幂等驱动，核心差异）**：建连失败可重试（请求未送达）；请求发出后按幂等性（非幂等不重试，防重复副作用）；4xx 不重试。显式 wrapper（max 2 / 退避 2 / jitter 20%）。
- **熔断**（per `tool_id`）：连续 5 次可重试失败 → OPEN 60s → HALF_OPEN 探测 1 次；4xx 不计入。
- **失败回灌（不中断 ReAct）**：`executeTool` 返回 `status=ERROR`（不抛），engine 把 output 作为 TOOL 消息回灌模型，让其自主决策。仅**致命错误**（超轮次/用户中断/循环 deadline）才中断整轮发 `run_error`。

| 失败类型 | output（回灌模型） |
|---------|--------------------------|
| 可重试故障重试耗尽 | `工具 <name> 暂时不可用，请稍后重试或换一种方式` |
| 参数/认证错误（4xx） | `调用工具 <name> 失败：<精简错误>` |
| 熔断中 | `工具 <name> 当前不可用` |
| 非幂等执行失败 | `工具 <name> 执行失败：<精简错误>` |

- **安全**：SSRF 黑名单（默认开，禁内网/保留地址 + 基础 DNS 检查，保存时+运行时校验）；响应 32KB 截断（标 `truncated`）；请求体 1MB 上限；`Authorization`/`Cookie`/`Set-Cookie` 脱敏不记入日志/output。

---

## 四、HTTP 工具

### 4.1 两种定义方式（底层同构）

| 方式 | 说明 |
|------|------|
| **手动配置** | 用户填 endpoint/method + **可视化参数表单**（生成 inputSchema）+ Header/Body 模板 + 鉴权 |
| **OpenAPI 导入** | 上传/粘贴 OpenAPI 3.0/3.1 spec → 解析预览 → 勾选 operation → 批量生成工具 |

二者产出**同构的 `tool` 表配置**（`endpoint`/`method`/`input_schema`/`config_json`/`auth_config`）。OpenAPI 导入即批量创建一组 HTTP 工具，导入后每个工具等同手动配置的工具，可单独编辑。

### 4.2 手动配置：可视化参数表单

用户在表单里以**参数行表**定义输入参数，系统自动生成 `input_schema` + `config_json.paramsMapping`：

| 参数表字段 | 含义 | 说明 |
|-----------|------|------|
| `name` | 参数名 | LLM 填的 args 键 |
| `in` | 位置 | `path` / `query` / `header` / `body`（决定如何映射到请求） |
| `type` | 类型 | `string` / `number` / `integer` / `boolean` |
| `required` | 是否必填 | 生成 JSON Schema 的 `required` |
| `description` | 参数描述 | 写入 JSON Schema，帮助 LLM 理解 |

- 支持「源码模式」切换：直接编辑生成的 JSON Schema 原文（高级用户），切回表单模式时尽量回填（无法回填的高级特性保留在 schema）。
- Header 参数可标记「敏感」（`secret=true`），其值运行时从 `auth_config` 解密注入，不存明文。

### 4.3 OpenAPI 导入（预览 + 勾选）

```text
1. 上传 .json/.yaml 文件 或 粘贴 spec 文本
2. 后端解析（Swagger Parser，3.0/3.1）→ 返回 operation 预览列表
   每个 operation：{ operationId, method, path, summary, suggestedName, hasAuth }
3. 用户勾选要导入的 operation（可改 suggestedName）
4. 后端按勾选批量创建工具：每 operation → 一个 HTTP 工具
   - name = operationId（缺失用 method_path，冲突加序号后缀，校验未删除唯一）
   - endpoint = spec.serverUrl + path
   - method = operation.method
   - input_schema = 由 operation 参数定义生成
   - config_json.paramsMapping = 由参数的 in 字段生成
   - auth_config = 用户在导入时统一配置（可选）
5. 返回创建的工具列表，跳回工具列表页
```

- 一个 spec 多 operation → 多工具（决策 C3）。预览+勾选避免一次导入几十个噪音工具。
- 导入后工具可单独编辑/禁用/删除。

### 4.4 鉴权凭据加密

Header/Body 里的 token/API Key 敏感信息**加密存储**，复用 `common.SecretEncryptor`（P1 Provider API Key 已用）。明文仅执行时解密、**不记录、不返回**（对齐 §6）。`tool.auth_config` 存加密 JSON（结构见 `01` §4.3）。接口返回时只给 `hasAuth: true/false`，密文不返回。

### 4.5 参数映射（执行时）

LLM 填的 args 按 `config_json.paramsMapping` 的 `in` 映射：`path`→填 URL `{param}` 占位、`query`→拼 query string、`header`→设请求头、`body`→进 request body。Header/Body 模板的 `{{auth.xxx}}` 占位运行时从 `auth_config` 解密替换。

---

## 五、MCP 工具

### 5.1 传输与客户端

- **传输**：只做 Streamable-HTTP + SSE（不做 stdio）。Zify 是 Web 服务，stdio 需管理子进程生命周期，一期复杂。
- **客户端**：`spring-ai-starter-mcp-client`（HttpClient 版，**SYNC**）+ `spring.ai.mcp.client.toolcallback.enabled=false`（关闭 ToolCallback auto-config，决策 C1）+ tool 模块自适配 `McpClient` → 中立 Tool。
- **工具发现粒度**：每个 remote tool → 一条 `tool` 记录（`source_type=MCP`、`mcp_server_id`、`input_schema` 来自 `McpSchema.Tool.inputSchema()`）。

### 5.2 连接生命周期（常驻保活）

- **常驻保活**：应用启动时连接已配置且 `enabled=1` 的 `mcp_server`，复用连接；新增 server 即时建连；`enabled=0`/删除 → 关闭连接。
- **工具发现**：连接建立后一次性 `listTools()` → 写/更新 `tool` 表；`toolsChangeConsumer`（starter hook）监听 server 端工具增删 → 增量更新（新增/启用/软删），保留已存在工具的 `enabled`。
- **断连重连 + 状态标记**：starter 内置重连；`mcp_server.status` 存 `ONLINE`/`OFFLINE`/`ERROR`。重连失败 → 标 `ERROR` → `listAvailableTools` 过滤掉其下工具（降级）；恢复 → 重新 `listTools` 刷新 + 状态回 `ONLINE`。
- **并发**：每个 server 一条 `McpClient` 连接，并发调用复用（MCP JSON-RPC 单连接多请求）。

### 5.3 命名冲突去重（D2）

MCP 工具不同 server 可能同名，注册时去重：冲突则加前缀 `mcpServerName__toolName`（Zify 自实现，因关闭了 spring-ai-mcp 的 prefix generator）。`tool.name` 即 LLM-visible name，全局未删除唯一。

---

## 六、工具生命周期与运行时校验（D2）

- **禁用/软删/断连** → `listAvailableTools` 过滤（本轮 LLM 看不到）；`agent_tool` 关联**不自动删**（保留绑定）。
- **过滤逻辑**：`tool.enabled=1 AND tool.is_deleted=0 AND (source_type=HTTP OR mcp_server.status=ONLINE)`。
- **命名冲突**：HTTP 工具用户起名校验 `name` 未删除唯一；MCP 工具冲突加前缀。
- **绑定校验时机**（对齐 P1 A-07）：保存 Agent 工具绑定时校验工具存在 + enabled（即时反馈）；运行时 `listAvailableTools` 再校验可用性。
- **MCP Server 全部工具不可用**（server 断连）→ 该 Agent 本轮无工具能力，模型正常回答（不报错）。

---

## 七、Agent 工具绑定

### 7.1 绑定步骤（P1 隐藏步骤激活）

P1 Agent 表单 Step 3「能力配置」原仅模型选择，P2 激活**工具绑定**多选（知识库绑定留 P3，工作流绑定留 P4）：

| 步骤 | 字段 | 说明 |
|------|------|------|
| Step 3 能力 | `modelId` | 模型选择（同 P1） |
| | `toolIds` | **工具绑定多选**（新增）：按来源分组展示所有可用工具（HTTP 工具 / 按 MCP Server 分组），勾选绑定 |

- 只展示 `enabled=1` 且来源可用的工具（MCP server `status=ONLINE`）。
- 绑定保存时校验：每个 toolId 存在 + enabled（经 `ToolFacade.listAvailableTools` 比对请求集与返回集，缺失/禁用者报错即时反馈）。
- `agent_tool` 写入（agent 模块 Service，短事务）。

### 7.2 编辑与解绑

- 编辑 Agent 可增减工具绑定（全量覆盖 `toolIds`）。
- 解绑某工具（从 `toolIds` 移除并保存）→ 软删对应 `agent_tool` 行；不影响工具本身。

---

## 八、ReAct 多轮循环（engine 扩展）

### 8.1 端到端时序

```text
chat (SSE) → EngineFacade.runChatTurn(cmd, sink)
  engine 循环 (虚拟线程):
   ┌─ 1. AgentFacade.getAgentConfig(agentId) → 含 boundToolIds
   │     ToolFacade.listAvailableTools(boundToolIds) → List<ToolViewDTO>（首轮取，内存缓存）
   │  2. 构造中立 tool 定义 → ModelFacade.chatStream(cmd{messages, toolDefs}, sink)
   │       └► model 内部：ToolDef→Spring AI ToolDefinition、stream+聚合、返回 toolCalls 请求 DTO
   │  3. sink.onDelta(token) → 前端 message_delta
   │  4. 若 model 返回 toolCalls（finishReason=TOOL_CALLS）:
   │       - sink.onToolCallStart(toolCallId, toolName, args) → 前端 tool_call_start
   │       - 并行 ToolFacade.executeTool(toolId,args,ctx) → tool 写 tool_call_log + 返回 DTO
   │       - sink.onToolCallEnd(toolCallId, toolName, status, output, durationMs, toolCallLogId) → 前端 tool_call_end
   │       - 结果作为 TOOL 消息加回 messages；ASSISTANT(toolCall) 消息加入 newMessages
   │       - 回 2（下一轮）
   └─ 5. 无 toolCall（finishReason=STOP）→ 返回 ChatTurnResult{newMessages, content(最终文本), finishReason}
  chat 落库 newMessages（含工具消息）→ 发 done
```

### 8.2 循环控制（C4）

| 控制点 | 规则 |
|--------|------|
| **终止** | ①模型 `finishReason=STOP` 无 tool call；②**最大轮次**（默认 10，`zify.chat.react.max-turns`）→ 落库已产出（`finishReason=MAX_TURNS`，视作截断不报错）；③token 接近窗口 → turn 级摘要压缩（§十），仍超则中断（`TIMEOUT`） |
| **死循环兜底** | 主兜底=最大轮次；增强=同一 `(toolName, args)` 连续重复达阈值（默认 3，`dup-tool-call-threshold`）→ 先回灌「检测到重复调用，请换方法」一轮，仍重复则中断 |
| **用户中断**（SSE 断连/停止） | 取消链：SSE 断连 → chat `Future.cancel` → engine 中断循环 + 取消进行中工具 → model 取消当前 stream。**部分落库**：已产出文本 + 已完成工具结果 → 落库（`finishReason=CANCELLED`）；进行中工具取消不落库 |
| **超时分层** | 单次 LLM stream 120s（P1 model 层）；单工具调用 30s 可配（B1）；**整轮循环 deadline 120s**（`zify.chat.react.loop-deadline`，从用户发消息起）。循环每轮检查剩余时间，不足 → 中断（`TIMEOUT`） |

### 8.3 并行工具 + 线程（C6）

- 模型一次返回多 tool call → **并行执行**（各 submit `ToolExecutor` + acquire 全局 `Semaphore` 50）→ `CompletableFuture.allOf` 等全部。各独立超时 + 熔断 + 共享循环 deadline；结果按 `toolCallId` 配对。
- **工具执行器**：独立 `ToolExecutor`（`newVirtualThreadPerTaskExecutor()` + 全局 `Semaphore`，`zify.tool.executor.max-concurrent=50`），与 `llmTaskExecutor` 隔离。per-tool 限流留二期。
- **同步阻塞模型**：engine 循环在虚拟线程上同步调 model+tool（阻塞 IO 不占 OS 线程）。
- **SSE 时序**：`tool_call_start(A/B)` 提交时发 → `tool_call_end` 各完成时发（乱序，前端按 `toolCallId` 配对）→ 全部完成 → 下一轮 `message_delta`。

---

## 九、对话流式生成扩展（chat + SSE）

### 9.1 SSE 事件协议（在 P1 三事件上加两事件，沿用两步协议）

端点不变：`GET /api/chat/stream?messageId={userMessageId}`（EventSource GET-only，决策 C5）。

| 事件 | 时机 | data 载荷 |
|------|------|----------|
| `message_delta`（既有） | 每轮 LLM token 流 | `{ conversationId, assistantMessageId, delta }` |
| **`tool_call_start`**（新增） | 模型决定调工具、engine 即将执行 | `{ conversationId, assistantMessageId, toolCallId, toolName, args(JSON) }` |
| **`tool_call_end`**（新增） | 工具执行完（成功/失败/熔断） | `{ conversationId, assistantMessageId, toolCallId, toolName, status(SUCCESS/ERROR), output, durationMs, toolCallLogId }` |
| `done`（既有） | 整轮 ReAct 结束 | `{ conversationId, assistantMessageId }`（=最终 ASSISTANT id） |
| `run_error`（既有） | 致命错误 | 不变 |

- **不需要 turn 事件**：靠 `assistantMessageId` 分段——多轮每轮 LLM 输出对应独立 assistantMessageId。
- 多轮 ReAct 里每轮 LLM 输出有独立 assistantMessageId；中间轮（带 toolCall）的 ASSISTANT 消息在生成完成时落库（与 P1「完成才 INSERT」一致），TOOL 消息在工具执行完成后由 chat 批量落库。

### 9.2 `TextStreamSink` 扩展（`com.zify.common.web.TextStreamSink`）

P1 为 `@FunctionalInterface`（仅 `onDelta`）。P2 升级为多方法接口（保留 `onDelta` 抽象，工具事件用 default 空实现，P1 调用方无需改动）：

```java
public interface TextStreamSink {
    void onDelta(String delta);
    default void onToolCallStart(String toolCallId, String toolName, String argsJson) {}
    default void onToolCallEnd(String toolCallId, String toolName, String status,
                               String output, long durationMs, String toolCallLogId) {}
}
```

> 用原生类型（String/long）而非业务 DTO，避免在 `common` 引入业务概念（`common` 不能出现 Tool 等业务类型）。`conversationId`/`assistantMessageId` 由 chat 层（持有会话上下文）在转 SSE 时补入。

流向：`chat`（`SseEmitter.send` 包装为 sink）→ `engine.runChatTurn(cmd, sink)` → 工具事件经 sink 回调 → chat 转 SSE 事件。

### 9.3 中断与取消（扩展 P1 §5.3）

```text
前端点「停止」/ 关闭页面 → EventSource.close()
   → chat 的 SseEmitter onCompletion/onError
   → chat 取消 engine 任务（Future.cancel(true)）
   → engine 中断循环 + 取消进行中的并行工具（ToolExecutor 任务 interrupt）
   → model 取消当前 stream；tool 的 RestClient/MCP 调用检测 interrupt 中止
```

取消后：

- 已产出文本 + 已完成工具结果 → 落库（最终 ASSISTANT `finishReason=CANCELLED`）。
- 进行中工具取消、不写 tool_call_log、不落 TOOL 消息。
- 发 `done` 结束流（保证前端关闭 EventSource）。

---

## 十、上下文管理扩展（A2，turn 级摘要）

P1 的「窗口预算 + 摘要压缩 + 尾部截断」（`docs-prd/phase-P1/02` §5.5）在 P2 扩展为**以 turn 为整体**处理工具消息：

- **turn 定义**：1 条 USER + 其后所有 ASSISTANT/TOOL（到下一条 USER）。
- **历史回放**：`loadActiveWindow` 扩展为 `role IN (USER, ASSISTANT, TOOL)`，按 turn 重建（与 SSE 实时流同一套渲染）。
- **摘要/截断以 turn 为整体**：折叠/截断**绝不拆散**工具消息序列（否则模型丢失 toolCall↔response 配对，多轮工具上下文断裂）。
- **token 压力**：工具结果（最多 32KB/条）吃上下文快；turn 级摘要更激进地折叠含大工具输出的旧 turn；`MAX_TURNS`(10) 也从轮次上兜底。
- 摘要生成仍在 engine（调 LLM）、存储在 chat（conversation 表），经 Facade 入参/出参流转（engine 不碰 conversation 表，P1 边界保持）。

---

## 十一、前端设计

### 11.1 路由（在 P1 基础上激活工具页 + MCP server 编辑页）

| 路由 | 页面 | 说明 |
|------|------|------|
| `/tools` | `pages/tools/ToolListPage.tsx` | 工具列表（按来源分组） |
| `/tools/create?type=http` | `pages/tools/ToolFormPage.tsx` | 创建 HTTP 工具（手动 / OpenAPI 导入，按 mode 切换） |
| `/tools/create?type=mcp` | `pages/tools/McpServerFormPage.tsx` | 连接 MCP Server（测试 + 发现预览） |
| `/tools/:id/edit` | `pages/tools/ToolFormPage.tsx` | 编辑 HTTP 工具配置 |
| `/tools/mcp/:serverId/edit` | `pages/tools/McpServerFormPage.tsx` | 编辑 MCP Server + 管理已发现工具 |

> 相对 `glm-docs/06` §11.3（仅 `/tools`、`/tools/create`、`/tools/:id/edit`）**新增** `/tools/mcp/:serverId/edit`（MCP server 是独立资源，单独编辑页管理连接 + 发现的工具）。建议同步更新 `06` §11.3。

### 11.2 工具列表页 `/tools`（ToolListPage）

```text
┌─────────────────────────────────────────────────────────┐
│ 工具管理                              [+ 新建工具 ▾]      │
│ [搜索框]  [来源: 全部 ▾]  [状态: 全部 ▾]                  │
├─────────────────────────────────────────────────────────┤
│ ▼ HTTP 工具                                              │
│   ┌─────────────────────────────────────────────────┐   │
│   │ get_user_info  查询用户信息            [HTTP]    │   │
│   │ GET https://api.example.com/users/{userId}       │   │
│   │ [启用●] [测试] [编辑] [删除]                       │   │
│   └─────────────────────────────────────────────────┘   │
│ ▼ MCP 工具                                               │
│   ┌─ GitHub MCP 🟢 ONLINE · 12 个工具 ───────────────┐   │
│   │ https://mcp.github.com/sse   [测试] [编辑] [禁用] │   │
│   │ ┌─ 已发现工具（展开）──────────────────────────┐ │   │
│   │ │ ☑ create_issue   [启用]                      │ │   │
│   │ │ ☑ list_repos     [启用]                      │ │   │
│   │ │ ☐ delete_repo    [禁用]                      │ │   │
│   │ └─────────────────────────────────────────────┘ │   │
│   └─────────────────────────────────────────────────┘   │
│ ▼ 工作流工具（P4 上线）                                   │
│   （暂无，工作流发布后自动注册）                            │
└─────────────────────────────────────────────────────────┘
```

- **新建工具下拉**：「HTTP 工具」「连接 MCP Server」；「工作流工具」项禁用并标注「P4 上线」。
- **HTTP 工具卡片**：name、description、method + endpoint、`HTTP` 标签、启用开关、测试/编辑/删除。
- **MCP server 卡片**：name、`base_url`、状态点（🟢ONLINE/⚫OFFLINE/🔴ERROR）+ 工具数、测试连接/编辑/启用禁用/删除；**可展开**显示已发现工具列表（逐个启用/禁用开关，不可编辑配置）。
- **工作流工具分组**：占位空状态（P4 发布工作流后自动填充）。

### 11.3 HTTP 工具表单页（ToolFormPage，手动配置）

`/tools/create?type=http&mode=manual` 与 `/tools/:id/edit`：

| 步骤 | 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|------|
| Step 1 基础 | `name` | 文本 | 是 | 工具名（LLM 可见，未删除唯一）；OpenAPI 导入时自动生成 |
| | `description` | 文本 | 是 | 工具描述（喂给 LLM） |
| Step 2 请求 | `method` | 单选 | 是 | GET/POST/PUT/DELETE/PATCH |
| | `endpoint` | 文本 | 是 | 完整 URL，支持 `{param}` 占位；保存时过 SSRF 校验 |
| | 参数表（可视化） | 动态行 | — | 每行：name / in(path,query,header,body) / type / required / description / secret；自动生成 `input_schema` |
| | `headersTemplate` | 键值表 | 否 | 固定/模板 Header；可标记 secret（值占位，运行时从鉴权注入） |
| | `bodyTemplate` | 多行 | 否 | POST/PUT/PATCH 请求体模板，`{{args.x}}`/`{{auth.x}}` 占位 |
| Step 3 鉴权 | `authType` | 单选 | 是 | NONE / API_KEY（headerName+值）/ BEARER（token） |
| | 凭据 | 密码 | 否 | 加密存储；编辑时显示 `••••••`，留空不改 |
| Step 4 高级 | `timeoutSeconds` | 数字 | 否 | 单次请求超时覆盖（空=全局 30s） |
| | `idempotent` | 开关 | 否 | 按 method 默认推断（GET=是），可覆盖 |
| Step 5 测试 | 测试调用 | 按钮 | — | 填示例 args → 真实执行 → 显示响应（验证配置）；写一条 `tool_call_log`（手动测试） |
| Step 6 确认 | — | — | — | 汇总保存 |

- **源码模式**：Step 2 参数表可切换到 JSON Schema 源码编辑（直接编辑 `input_schema`），切回尽量回填。
- **校验**：name 未删除唯一；endpoint 合法 + SSRF 校验；必填项。

### 11.4 HTTP 工具 OpenAPI 导入（ToolFormPage，导入模式）

`/tools/create?type=http&mode=openapi`：

```text
Step 1  上传 spec
        ├ [选择文件 .json/.yaml] 或 [粘贴 spec 文本]
        └ [解析] → 后端返回 operation 预览列表

Step 2  勾选 operation
        ┌──────────────────────────────────────────────┐
        │ baseUrl: https://api.example.com (可改)       │
        │ ☑ GET /users/{id}      getUser      [get_user]│  ← 名称可改
        │ ☑ GET /repos           listRepos    [list_repos]
        │ ☐ DELETE /repos/{id}   deleteRepo            │
        └──────────────────────────────────────────────┘

Step 3  鉴权（统一，应用到所有勾选工具）
        authType: [BEARER]  token: [••••••]

Step 4  确认导入 → 批量创建 → 跳回列表
```

- 预览列表每项：勾选框、method+path、operationId/summary、可编辑的 name（默认 operationId，缺失用 method_path）。
- 导入即批量创建工具（每 operation 一个），返回创建结果。
- 导入后每个工具等同手动配置工具，可单独编辑/禁用/删除。

### 11.5 MCP Server 表单页（McpServerFormPage）

`/tools/create?type=mcp` 与 `/tools/mcp/:serverId/edit`：

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `name` | 文本 | 是 | Server 名称（未删除唯一） |
| `description` | 文本 | 否 | — |
| `baseUrl` | 文本 | 是 | 连接地址；保存时过 SSRF 校验 |
| `transportType` | 单选 | 是 | STREAMABLE_HTTP（默认）/ SSE |
| `authType` | 单选 | 是 | NONE / API_KEY（headerName+值）/ BEARER（token） |
| 凭据 | 密码 | 否 | 加密存储，编辑时 `••••••` |
| **测试连接** | 按钮 | — | 立即建连 → 返回发现的工具列表预览（不持久化连接） |
| **发现的工具**（测试/编辑后展示） | 列表 | — | 每个 remote tool：name、描述、启用开关；保存时按勾选状态写 `tool` 表 |
| **刷新发现**（编辑页） | 按钮 | — | 重新 `listTools`，增量同步 `tool` 表（新增/移除，保留已存在工具 enabled） |

- 创建流程：填配置 → 测试连接 → 看到发现工具 → 保存（持久化 server + 工具，建立常驻连接）。
- 编辑流程：改配置/重新测试/刷新发现/逐个启用禁用已发现工具。

### 11.6 Agent 表单工具绑定（Step 3 激活）

P1 Agent 表单 Step 3「能力配置」新增工具绑定多选区（`features/tool/components/ToolBinder.tsx`）：

- 按来源分组展示所有可用工具（HTTP 工具 / 按 MCP Server 分组），多选。
- 只列 `enabled=1` 且来源可用的工具；MCP server `OFFLINE/ERROR` 的工具灰显并标注「Server 不可用」。
- 选中的 toolIds 随表单保存（`PUT /api/agents/{id}/tools`）。

### 11.7 对话区工具调用展示（ToolCallTrace）

对话页消息流渲染（`pages/chat/components/ToolCallTrace.tsx`）：

- **实时流**：
  - `tool_call_start` → 在所属 assistantMessageId 文本段下方插入「🔧 调用 `toolName`…」加载态卡片。
  - `tool_call_end` → 更新卡片：状态（✅SUCCESS/❌ERROR）、耗时、可折叠 output。
- **历史回放 = 实时流同渲染**：从 `message` 表（role + metadata）重建——
  - `ASSISTANT` + `metadata.toolCalls` 非空 → 文本段 + 下方工具卡片（每个 toolCall 一张）。
  - `TOOL` + `metadata.toolCallId/toolCallLogId` → 工具结果（并入对应卡片，按 toolCallId 配对）。
  - `ASSISTANT` 无 toolCalls → 纯文本段（中间推理或最终回复）。
- **下钻**：工具卡片可展开看 output；点 `toolCallLogId` → 抽屉/侧栏显示完整 `tool_call_log`（input/output/error/status/duration，调 `GET /api/tool/call-logs/{id}`）。
- **折叠默认**：工具卡片默认折叠，避免长 output 刷屏。

### 11.8 文件清单（按 `glm-docs/06` §11 结构，P2 增量）

```text
src/
├── api/
│   ├── toolApi.ts            # MCP Server CRUD + test/refresh；Tool CRUD + parse/import-openapi + test；call-logs
│   ├── agentApi.ts           # (扩展) bindTools / getBoundTools
│   └── chatApi.ts            # (扩展) openChatStream handlers 增 onToolCallStart/onToolCallEnd
├── types/
│   ├── tool.ts               # MCP Server / Tool / OpenAPI 预览 / call-log HTTP 契约类型
│   ├── chat.ts               # (扩展) ChatStreamEvent 增 tool_call_start/tool_call_end；MessageView 增 toolCalls
│   └── agent.ts              # (扩展) AgentDetail 增 toolIds/toolSummaries
├── features/
│   ├── tool/components/
│   │   ├── ToolBinder.tsx        # Agent 表单工具绑定多选
│   │   ├── HttpToolForm.tsx      # HTTP 工具手动表单（含可视化参数表单 + 源码模式）
│   │   ├── OpenApiImportWizard.tsx # OpenAPI 上传→预览→勾选→导入
│   │   ├── McpServerForm.tsx     # MCP Server 表单（测试+发现）
│   │   ├── ParamSchemaEditor.tsx # 可视化参数行表 ↔ JSON Schema 源码切换
│   │   └── DiscoveredToolList.tsx# MCP server 已发现工具列表（启用开关）
│   └── chat/hooks/
│       └── useChatStream.ts      # (扩展) 工具事件分支
├── pages/
│   ├── tools/
│   │   ├── ToolListPage.tsx
│   │   ├── ToolFormPage.tsx          # HTTP 工具（手动/编辑）
│   │   ├── McpServerFormPage.tsx     # MCP server（创建/编辑）
│   │   └── components/{ToolCard,McpServerCard,ToolCallLogDrawer}.tsx
│   └── chat/components/
│       └── ToolCallTrace.tsx         # 对话区工具卡片渲染
```

### 11.9 状态管理（Zustand）

- `chatStore`（P1 已有）扩展：`messages` 中 `MessageView` 增 `toolCalls?: ToolCallView[]`；新增 action `appendToolCall(assistantMessageId, toolCall)` / `updateToolCall(assistantMessageId, toolCallId, patch)`。
- 工具管理页**不进 Store**：列表/表单/弹窗状态用组件本地 `useState`（同模型管理页）。

### 11.10 SSE 调用扩展（chatApi.ts）

```typescript
// openChatStream 的 handlers 增两个工具事件
type ChatStreamHandlers = {
  onMessageDelta: (e) => void;
  onToolCallStart: (e: Extract<ChatStreamEvent, { type: 'tool_call_start' }>) => void;
  onToolCallEnd:   (e: Extract<ChatStreamEvent, { type: 'tool_call_end' }>) => void;
  onDone: (e) => void;
  onRunError: (e) => void;
};
```

`ChatStreamEvent`（`types/chat.ts`）扩展：

```typescript
export type ChatStreamEvent =
  | { type: 'message_delta'; conversationId: string; assistantMessageId: string; delta: string }
  | { type: 'tool_call_start'; conversationId: string; assistantMessageId: string; toolCallId: string; toolName: string; args: string }
  | { type: 'tool_call_end'; conversationId: string; assistantMessageId: string; toolCallId: string; toolName: string; status: 'SUCCESS' | 'ERROR'; output: string; durationMs: number; toolCallLogId: string }
  | { type: 'done'; conversationId: string; assistantMessageId: string }
  | { type: 'run_error'; message: string };
```

---

## 十二、后端 API 设计（HTTP）

统一响应 `Result<T>`（`com.zify.common.web.Result`）。错误经 `BusinessException` + `ErrorCode`。测试类接口（连接测试/调用测试）通过 `data.success` 返回结果，不抛异常（对齐模型管理测试接口风格）。SSE 端点返回 `SseEmitter`。

### 12.1 MCP Server 接口（`/api/tool/mcp-servers`，tool 模块 `adapter/web/McpServerController`）

#### 创建 MCP Server（连接 + 发现）

`POST /api/tool/mcp-servers`

```jsonc
// 请求
{ "name": "GitHub MCP", "description": "GitHub 工具集",
  "baseUrl": "https://mcp.github.com/sse", "transportType": "SSE",
  "authType": "BEARER", "credential": "ghp_xxxx" }
// 响应 data：McpServerDetailResponse（见下，含 discoveredTools）
```

规则：`name` 未删除唯一；`baseUrl` SSRF 校验；创建即建连 + `listTools` → 写 `tool` 表（默认全启用）+ 置 `status=ONLINE`；`credential` 加密存 `auth_config`，响应不返回。

#### MCP Server 列表

`GET /api/tool/mcp-servers?page=1&pageSize=20&enabled=&status=`
→ `OffsetPageResponse<McpServerSummaryResponse>`：`{ id, name, description, baseUrl, transportType, authType, hasAuth, enabled, status, toolsCount, createdAt }`。小表 OFFSET。`toolsCount` 子查询聚合；不返回 `auth_config`。

#### MCP Server 详情

`GET /api/tool/mcp-servers/{id}` → `McpServerDetailResponse`：summary 字段 + `discoveredTools: [{ id, name, description, enabled, sourceType:MCP }]`。

#### 更新

`PUT /api/tool/mcp-servers/{id}` — 可改 `name`/`description`/`baseUrl`/`transportType`/`authType`/`credential`/`enabled`。改 `baseUrl`/认证 → 重连 + 重新发现（同步 `tool` 表）；`credential` 留空不改。

#### 删除

`DELETE /api/tool/mcp-servers/{id}` — 软删 server + 关闭连接 + 软删其下所有 `tool`（`is_deleted=1`）。`agent_tool` 关联不自动删（运行时过滤）。

#### 启用/禁用

`PUT /api/tool/mcp-servers/{id}/enabled` body `{ "enabled": false }`。`enabled=0` → 断连 + `status=OFFLINE`，其下工具 `listAvailableTools` 不可见。

#### 测试连接

`POST /api/tool/mcp-servers/{id}/test` （也支持未保存配置的测试：`POST /api/tool/mcp-servers/test` body 含完整配置）
→ `{ success: bool, message, latencyMs, discoveredTools: [{ name, description, inputSchema }] }`。不持久化连接/工具；超时 15s（MCP 握手）。

#### 刷新发现

`POST /api/tool/mcp-servers/{id}/refresh` — 重新 `listTools`，增量同步 `tool` 表（新增工具默认启用、已移除工具软删、已存在工具保留 `enabled`）。返回最新 `discoveredTools`。

#### 单个 MCP 工具启用/禁用

`PUT /api/tool/tools/{id}/enabled` body `{ "enabled": false }` — 对 MCP 工具仅切 `tool.enabled`（不可编辑配置）。

### 12.2 HTTP 工具接口（`/api/tool/tools`，tool 模块 `adapter/web/ToolController`）

#### 创建 HTTP 工具（手动）

`POST /api/tool/tools`

```jsonc
// 请求
{ "name": "get_user_info", "description": "查询用户信息",
  "method": "GET", "endpoint": "https://api.example.com/users/{userId}",
  "inputSchema": "{...JSON Schema...}",     // 由前端可视化参数表单生成
  "configJson": { "paramsMapping":[...], "headersTemplate":[...], "bodyTemplate":null },
  "authType": "BEARER", "credential": "sk-xxx",
  "timeoutSeconds": null, "idempotent": null }  // idempotent null → 按 method 推断
// 响应 data：ToolDetailResponse
```

规则：`name` 未删除唯一；`endpoint` SSRF 校验；`credential` 加密存 `auth_config`；`idempotent` 为 null 时 service 按 method 推断写入（GET/HEAD=1，其余=0）。

#### 解析 OpenAPI（预览，不持久化）

`POST /api/tool/tools/parse-openapi` （multipart 文件 或 `{ "spec": "<spec 文本>" }`）
→ `{ baseUrl, operations: [{ operationId, method, path, summary, suggestedName, hasRequestBody }] }`。解析失败 → `OPENAPI_PARSE_FAILED`。

#### 导入 OpenAPI（批量创建）

`POST /api/tool/tools/import-openapi`

```jsonc
// 请求
{ "baseUrl": "https://api.example.com",       // 可覆盖 spec 的 server
  "authType": "BEARER", "credential": "sk-xxx",
  "operations": [
    { "operationId": "getUser", "name": "get_user", "selected": true },
    { "operationId": "listRepos", "name": "list_repos", "selected": true },
    { "operationId": "deleteRepo", "selected": false }
  ],
  "spec": "<原 spec 文本，后端复用解析>" }
// 响应 data：{ created: [ToolDetailResponse...], skipped: [...] }
```

规则：仅创建 `selected=true` 的 operation；name 冲突加序号后缀并校验唯一；每 operation → 一个 HTTP 工具（`input_schema`/`config_json` 由参数生成）；`credential` 加密。

#### 工具列表

`GET /api/tool/tools?page=1&pageSize=20&sourceType=&mcpServerId=&enabled=`
→ `OffsetPageResponse<ToolSummaryResponse>`：`{ id, name, description, sourceType, mcpServerId, mcpServerName, enabled, method, endpoint, status(可用性), createdAt }`。小表 OFFSET；不返回 `input_schema`/`config_json`/`auth_config`。

#### 工具详情

`GET /api/tool/tools/{id}` → `ToolDetailResponse`：含 `inputSchema`/`configJson`/`authType`/`hasAuth`/`timeoutSeconds`/`idempotent`（不返回 `auth_config` 密文）。

#### 更新（仅 HTTP 工具）

`PUT /api/tool/tools/{id}` — 可改 `name`/`description`/`method`/`endpoint`/`inputSchema`/`configJson`/`authType`/`credential`/`timeoutSeconds`/`idempotent`/`enabled`。`endpoint` 改动重过 SSRF。MCP 工具不可编辑配置（只能切 `enabled`，走 `PUT /enabled`）。

#### 删除

`DELETE /api/tool/tools/{id}` — 软删。若被 Agent 绑定，提示「N 个 Agent 引用，删除后这些 Agent 将失去该工具」（不阻断，`agent_tool` 保留，运行时过滤）。

#### 启用/禁用

`PUT /api/tool/tools/{id}/enabled` body `{ "enabled": false }`。

#### 测试调用（手动执行验证）

`POST /api/tool/tools/{id}/test` body `{ "args": { "userId": "123" } }`
→ `{ success: bool, status, output, durationMs, error?, toolCallLogId }`。真实执行（受超时/SSRF/熔断约束），**写一条 `tool_call_log`**（`conversationId`/`agentId`/`turn` 均为 NULL，标记手动测试）。失败按 B1 回灌规则返回 `status=ERROR`（不抛异常）。

### 12.3 工具调用日志接口（`/api/tool/call-logs`，tool 模块 `adapter/web/ToolCallLogController`）

#### 单条日志详情（下钻）

`GET /api/tool/call-logs/{id}` → `ToolCallLogDetailResponse`：`{ id, toolId, toolName, sourceType, agentId, conversationId, turn, toolCallId, input, output, status, durationMs, error, createdAt }`。

#### 日志列表（Keyset）

`GET /api/tool/call-logs?conversationId=&agentId=&toolId=&cursor=&limit=20`
→ `CursorPageResponse<ToolCallLogSummaryResponse>`：`{ id, toolName, sourceType, status, durationMs, createdAt }`（不返回 `input`/`output` 大字段，按主键详情取）。至少传一个过滤维度（`conversationId`/`agentId`/`toolId`），禁止全表扫。

### 12.4 Agent 工具绑定接口（`/api/agents`，agent 模块 `adapter/web/AgentController` 扩展）

#### 查询 Agent 绑定的工具

`GET /api/agents/{id}/tools` → `{ toolIds: [...], tools: [{ id, name, description, sourceType, enabled, available }] }`。

#### 更新工具绑定

`PUT /api/agents/{id}/tools` body `{ "toolIds": ["uuid", "..."] }`
→ `{ toolIds: [...] }`。全量覆盖绑定：service 校验每个 toolId 存在 + enabled（经 `ToolFacade.listAvailableTools` 比对，缺失/禁用者 `TOOL_NOT_AVAILABLE`），事务内重置 `agent_tool`（软删旧的、插入新的）。

> `GET /api/agents/{id}` 详情响应（P1）扩展返回 `toolIds` + `toolSummaries`，供表单预填。

### 12.5 SSE 流式（chat 模块，路径不变）

`GET /api/chat/stream?messageId={userMessageId}` — 新增 `tool_call_start`/`tool_call_end` 事件（见 §9.1）。两步协议、中断逻辑同 P1。

---

## 十三、后端 Facade 契约汇总

### 13.1 `ToolFacade`（tool 模块，新建）

```java
List<ToolViewDTO> listAvailableTools(Collection<String> toolIds);   // 过滤可用：enabled+未删+(HTTP 或 MCP server ONLINE)
ToolExecutionResultDTO executeTool(ToolExecutionCommand command);   // 内部写 tool_call_log；返回 DTO 不抛
```

- `listAvailableTools`：供 engine（取 Agent 可用工具视图）与 agent（绑定校验时比对）调用。返回 DTO，不返回 Entity。
- `executeTool`：供 engine（ReAct 循环）调用。内部完成 SSRF 运行时校验、超时/重试/熔断、截断、写 `tool_call_log`，返回中立 DTO；失败返回 `status=ERROR`（不抛），仅致命错误由调用方处理。

### 13.2 `ModelFacade.chatStream`（model 模块，扩展）

```java
ChatCompletionResult chatStream(ChatCompletionCommand command, TextStreamSink sink);
```

- `ChatCompletionCommand` 增 `toolDefinitions: List<ToolDefinitionDTO>`（可空，空 = P1 零工具行为不变）。
- `ChatCompletionResult` 增 `toolCalls: List<ToolCallDTO>`（可空，`finishReason=TOOL_CALLS` 时非空）。
- `ChatMessage` 支持 `role=TOOL`（带 `toolCallId`）+ ASSISTANT 带 `toolCalls`。
- Spring AI 类型（`ToolDefinition`/`ToolResponseMessage`/`ChatResponse`）全部封装在 model 内部。

中立 DTO（`com.zify.model.api.dto.chat`）：

| DTO | 字段 |
|-----|------|
| `ToolDefinitionDTO` | `name`, `description`, `inputSchema`(String JSON) |
| `ToolCallDTO` | `id`, `name`, `args`(String JSON) |
| `ChatMessage` | `role`(USER/ASSISTANT/SYSTEM/TOOL), `content`, `toolCalls`(List<ToolCallDTO>?, ASSISTANT), `toolCallId`(String?, TOOL) |

### 13.3 `EngineFacade.runChatTurn`（engine 模块，扩展）

```java
ChatTurnResult runChatTurn(ChatTurnCommand command, TextStreamSink sink);
```

- `ChatTurnCommand`：`agentId`, `history: List<ChatMessage>`, `assistantMessageId`, `summary`, `summaryCoveredMessageId`（同 P1；工具列表由 engine 内部 `AgentFacade.getBoundToolIds` + `ToolFacade.listAvailableTools` 取，不入 command）。
- `ChatTurnResult`：`content`(最终文本), `finishReason`(STOP/LENGTH/MAX_TURNS/TIMEOUT/CANCELLED), `usage`, `newMessages: List<ChatMessage>`(本轮新增消息序列，含 ASSISTANT-toolCall + TOOL + 最终 ASSISTANT), `newSummary`/`newSummaryCoveredMessageId`(仅触发压缩时非空)。
- engine **不碰任何 DB**（P1 §2.1 保持）；工具事件经 `sink.onToolCallStart/End` 推送；失败抛异常由 chat 发 `run_error`。
- 中断：`Future.cancel(true)` 透传到 model + 进行中工具。

### 13.4 `AgentFacade`（agent 模块，扩展）

```java
AgentConfigDTO getAgentConfig(String agentId);   // 扩展：增 boundToolIds: List<String>
List<String> getBoundToolIds(String agentId);     // 新增：供 engine 取绑定
```

### 13.5 跨模块数据流总览

```text
chat (持久化/HTTP/SSE)
  └─ EngineFacade.runChatTurn (编排，无 DB)
       ├─ AgentFacade.getAgentConfig → boundToolIds
       ├─ ToolFacade.listAvailableTools(boundToolIds) → 工具视图（首轮缓存）
       ├─ ModelFacade.chatStream(msgs, toolDefs, sink) → toolCalls / token 流
       └─ ToolFacade.executeTool(toolId, args, ctx) → 结果 DTO（tool 内部写 tool_call_log）
            结果作为 TOOL 消息回灌，进入 newMessages
  chat 落库 newMessages（USER/ASSISTANT-toolCall/TOOL/最终ASSISTANT）
```

---

## 十四、业务规则汇总

### 14.1 MCP Server 规则

| 编号 | 规则 |
|------|------|
| MS-01 | MCP Server 名称在未删除数据中唯一 |
| MS-02 | `base_url` 保存时 + 运行时过 SSRF 黑名单 |
| MS-03 | 传输只支持 STREAMABLE_HTTP / SSE（不做 stdio） |
| MS-04 | 认证凭据加密存储（`SecretEncryptor`），不记录、不返回 |
| MS-05 | `enabled=0` → 断连且不重连，置 `status=OFFLINE`，其下工具不可见 |
| MS-06 | `status` 由连接生命周期驱动（ONLINE/OFFLINE/ERROR），非用户设置 |
| MS-07 | 删除 server → 软删其下所有工具 + 关闭连接；`agent_tool` 不自动删 |
| MS-08 | 连接常驻保活；`toolsChangeConsumer` 增量同步工具；断连自动重连 |

### 14.2 工具规则

| 编号 | 规则 |
|------|------|
| T-01 | 工具名（`name`）全局未删除唯一（LLM 可见标识） |
| T-02 | HTTP 工具名用户起名校验唯一；MCP 工具冲突加 `mcpServerName__toolName` 前缀；OpenAPI 取 operationId，缺失用 method_path，冲突加序号 |
| T-03 | `source_type`：HTTP / MCP（P2）；WORKFLOW（P4）。创建后不可改 |
| T-04 | `input_schema` 定义时一次性生成入库，运行时原样透传给 LLM |
| T-05 | HTTP 工具两种定义（手动/OpenAPI）底层同构；OpenAPI 一 operation → 一 tool |
| T-06 | HTTP 鉴权凭据加密存储，不记录、不返回 |
| T-07 | `idempotent` 按 method 默认推断（GET/HEAD=1，POST/PUT/DELETE/PATCH=0），用户可覆盖；MCP 默认 0 |
| T-08 | `enabled=0`/软删 → `listAvailableTools` 不返回（本轮 LLM 看不到） |
| T-09 | MCP 工具不可编辑配置（仅切 enabled），配置变更走 server 刷新发现 |
| T-10 | 可用性 = `enabled=1 AND is_deleted=0 AND (source_type=HTTP OR mcp_server.status=ONLINE)` |
| T-11 | 工具执行失败返回 `status=ERROR`（不抛），engine 回灌模型，不中断循环（致命错误除外） |
| T-12 | 工具执行有超时（连接 10s/单次 30s 可配/总 ≤60s）；重试幂等驱动；per-tool_id 熔断 |

### 14.3 Agent 工具绑定规则

| 编号 | 规则 |
|------|------|
| AT-01 | 一个 Agent 可绑定 0~N 个工具（P1 零工具仍合法） |
| AT-02 | 绑定保存时校验每个 toolId 存在 + enabled（经 ToolFacade，即时反馈） |
| AT-03 | 运行时 `listAvailableTools` 再校验可用性（对齐 P1 A-07） |
| AT-04 | 工具禁用/删除/断连 → `agent_tool` 关联不自动删，仅运行时过滤 |
| AT-05 | Agent 所有工具不可用 → 模型无工具能力，正常回答 |

### 14.4 ReAct 循环规则

| 编号 | 规则 |
|------|------|
| R-01 | 每轮 LLM 调用经 `ModelFacade.chatStream`（复用 P1 网关：超时/重试/隔离/解密） |
| R-02 | 终止：STOP / 最大轮次（默认 10，截断不报错）/ token 超窗（摘要压缩，仍超中断） |
| R-03 | 死循环兜底：同 `(toolName,args)` 连续重复达阈值（默认 3）→ 回灌提示一轮，仍重复中断 |
| R-04 | 用户中断取消链：SSE 断连 → 取消 engine 循环 + 进行中工具 + model stream |
| R-05 | 中断部分落库：已产出文本 + 已完成工具结果（`CANCELLED`）；进行中工具取消不落库 |
| R-06 | 整轮循环 deadline 120s，每轮检查剩余时间 |
| R-07 | 并行工具：多 tool call 并行执行（独立超时+熔断+共享 deadline），按 toolCallId 配对 |

### 14.5 持久化与上下文规则

| 编号 | 规则 |
|------|------|
| P-01 | 工具调用过程（ASSISTANT toolCall + TOOL）落 `message` 表（不改列，扩 metadata） |
| P-02 | `tool_call_log` 归 tool 模块，由 `ToolFacade.executeTool` 内部写（执行点即记录点） |
| P-03 | `message`(TOOL).metadata.toolCallLogId ↔ tool_call_log.id 双向关联 |
| P-04 | engine 保持纯编排（不碰 DB）；`ChatTurnResult.newMessages` 由 chat 批量落库（短事务） |
| P-05 | 事务只包 DB 写（落库消息/日志），禁止包 LLM/工具调用 |
| P-06 | 上下文重建 `role IN (USER, ASSISTANT, TOOL)`；摘要/截断以 turn 为整体，不拆散工具消息序列 |
| P-07 | 工具 input/output 截断后存（32KB 阈值）；回灌模型/存 message/存 log 用同一份 |

### 14.6 流式与安全规则

| 编号 | 规则 |
|------|------|
| S-01 | SSE 沿用 P1 两步协议（POST 消息 + GET stream），无新端点 |
| S-02 | 新增 `tool_call_start`/`tool_call_end` 事件，靠 assistantMessageId 分段（无 turn 事件） |
| S-03 | SSE 断连/超时/发送失败必须取消上游（LLM + 工具） |
| S-04 | `TextStreamSink` 扩展工具事件回调（default 空实现，P1 调用方不破坏） |
| S-05 | SSRF 黑名单（默认开）：禁内网/保留地址 + 基础 DNS 检查，保存时 + 运行时校验 |
| S-06 | 响应 32KB 截断（标 truncated）；请求体 1MB 上限；敏感 Header 脱敏不记入日志/output |
| S-07 | API Key/鉴权凭据全链路不记录、不返回、不入异常 |

---

## 十五、配置项（application.yml 增量）

```yaml
spring:
  ai:
    mcp:
      client:
        type: SYNC                       # MCP Client 同步风格
        toolcallback:
          enabled: false                 # 关闭 ToolCallback auto-config（C1）

zify:
  chat:
    react:                               # ReAct 循环控制（C4）
      max-turns: 10                      # 最大轮次
      loop-deadline: 120s                # 整轮循环 deadline
      dup-tool-call-threshold: 3         # 重复调用检测阈值
  tool:
    timeout:                             # 工具调用超时（13 §4，B1）
      connect: 10s
      mcp-handshake: 15s
      request-default: 30s               # tool.timeout_seconds 未设时兜底
      total-cap: 60s
    circuit-breaker:                     # 熔断（13 §6.1）
      failure-threshold: 5
      open-duration: 60s
    executor:                            # 工具执行器（C6）
      max-concurrent: 50                 # 全局 Semaphore
    security:                            # 安全（C7）
      ssrf:
        enabled: true                    # SSRF 黑名单开关
        allow-private: false             # 是否允许内网（内网部署可开）
      response-max-bytes: 32768          # 响应截断阈值 32KB
      request-max-bytes: 1048576         # 请求体上限 1MB
```

新依赖：

- `zify-tool` pom 增 `spring-ai-starter-mcp-client`（MCP Client，C1）。
- `zify-tool` pom 增 `io.swagger.parser.v3:swagger-parser-v3`（OpenAPI 解析，C3）。

新 Bean：tool 模块 `ToolExecutor`（`Executors.newVirtualThreadPerTaskExecutor()` + 全局 `Semaphore`，C6 例外，对齐 `07` §3.2）。

---

## 十六、错误码（`ErrorCode` 枚举增量，14xx 工具段）

| 错误码 | 含义 | 触发 |
|--------|------|------|
| `TOOL_NOT_FOUND`（已有 1401） | 工具不存在/已删除 | 引用校验 |
| `MCP_SERVER_NOT_FOUND`（新增 1402） | MCP Server 不存在/已删除 | 引用校验 |
| `TOOL_NAME_DUPLICATE`（新增 1403） | 工具名已存在 | 创建/改名/导入冲突 |
| `MCP_SERVER_NAME_DUPLICATE`（新增 1404） | MCP Server 名称已存在 | 创建/改名 |
| `OPENAPI_PARSE_FAILED`（新增 1405） | OpenAPI 解析失败 | 导入解析 |
| `TOOL_SSRF_BLOCKED`（新增 1406） | URL 命中 SSRF 黑名单 | 保存/运行时校验 |
| `TOOL_CONFIG_INVALID`（新增 1407） | 工具配置非法（endpoint/必填缺失等） | 创建/更新 |
| `TOOL_NOT_AVAILABLE`（新增 1408） | 工具不可用（禁用/删除/断连） | 绑定校验 |

> **工具执行失败不抛 ErrorCode**：`executeTool` 的失败（超时/熔断/4xx/非幂等失败）按 B1 §6.2 返回 `ToolExecutionResultDTO.status=ERROR`（回灌模型），不作为业务异常。仅配置/CRUD 校验类错误走 `BusinessException` + `ErrorCode`。测试类接口（连接测试/调用测试）通过 `data.success` 返回，不抛。

---

## 十七、一致性说明与对既有文档的偏离

| 项 | 既有文档 | P2 决策 | 处理 |
|----|---------|---------|------|
| `ToolFacade.listBoundTools(agentId)` | `00-tech` §3.1 | **拆为** `AgentFacade.getBoundToolIds` + `ToolFacade.listAvailableTools(ids)` | 边界修正：`agent_tool` 归 agent，tool 不能跨模块读。engine 编排两步（均合法依赖） |
| `tool_call_log` 归属 | `glm-docs/11` 原归 engine | 改归 tool 模块 | ✅ 已回写 doc 11（A2 决策 1） |
| `message` ↔ `tool_call_log` 关系 | doc 11 `message 1:N tool_call_log` | `message(TOOL) 0..1:1 tool_call_log`（经 metadata） | ✅ 已回写 doc 11（A2 决策 5） |
| `tool.status`/`tool.tool_type` | `10` §4.6 | 用 `source_type` + `enabled`（决策 B2/D2） | ⏳ 建议更新 `10` §4.6 |
| `message.metadata` 结构 | P1 `01` §4.1 | 扩展 toolCalls/toolCallId/toolCallLogId | ⏳ P2 编码时落实 P1 `01` §四 |
| SSE 协议表 / `ChatStreamEvent` | P1 `02` §5.2 / `06` §11.7 | 增 `tool_call_start`/`tool_call_end` | ⏳ P2 编码时落实（C5） |
| 摘要压缩 turn 边界 | P1 `02` §5.5 | turn 级整体折叠（A2） | ⏳ P2 编码时落实 |
| 前端路由 | `06` §11.3 无 MCP server 编辑页 | 新增 `/tools/mcp/:serverId/edit` | ⏳ 建议更新 `06` §11.3 |
| `glm-docs/02` §1 ReAct 澄清 | 已有脚注 | ✅ 已回写 | — |
| `glm-docs/07` §一 指向 13 | 已声明工具调用单独定义 | ✅ 已回写 + `13` 已建 | — |

---

## 十八、实现顺序与验收

### 18.1 实现顺序（对齐 `CLAUDE.md` §2）

1. **tool 基座**：迁移 V6/V7 → `mcp_server`/`tool`/`tool_call_log`/`agent_tool` → `ToolFacade` + 中立 DTO + 内部 Tool 接口 → `ToolExecutor`（虚拟线程+Semaphore）+ `ToolException` 体系 + 工具调用规范（超时/重试/熔断/SSRF/大小限制，`13`）。
2. **HTTP 工具**：手动配置（可视化参数表单→inputSchema）+ OpenAPI 解析（Swagger Parser，预览+勾选导入）+ 参数映射 + 鉴权加密（SecretEncryptor）+ SSRF/大小限制。
3. **MCP 工具**：spring-ai-mcp-client 接入（SYNC）+ 关闭 ToolCallback + 自适配 + 连接生命周期（常驻/toolsChange/状态标记/降级）。
4. **model 扩展**：`ModelFacade.chatStream` 带工具（`toolDefinitions`/`toolCalls`）+ `ChatMessage` 支持 TOOL/toolCalls。
5. **engine 扩展**：ReAct 循环（手动驱动）+ 循环控制（C4）+ 并行工具（C6）+ `ChatTurnResult.newMessages`。
6. **chat 扩展**：工具消息持久化（`persistTurn` 批量落库）+ SSE 协议（`tool_call_start`/`tool_call_end`）+ `TextStreamSink` 扩展 + 上下文重建（含 TOOL/turn 摘要）。
7. **agent 扩展**：`agent_tool` 绑定步骤 + `getBoundToolIds`。
8. 全链路联调 + 过 `CLAUDE.md` §10 检查清单 + A1 双 provider spike（验证 stream + tool calling）。

### 18.2 验收标准（DoD，对齐 `glm-docs/12` §五）

1. 创建一个 HTTP 工具（手动配置）并绑定到 Agent，Agent 对话中自主调用并基于返回结果回答。
2. 通过 OpenAPI 导入一批工具，绑定后 Agent 可选用其中已启用工具。
3. 连接一个 MCP Server，自动发现工具，Agent 可调用其中已启用工具；server 断连后其工具降级不可见。
4. `tool_call_log` 记录每次调用输入输出/耗时/状态，前端工具卡片可下钻。
5. 工具调用有超时；ReAct 循环可中断（用户停止/SSE 断连取消上游 LLM + 工具）；并行工具调用正常；死循环兜底生效。
6. 跨模块只走 Facade（engine 经 ToolFacade/AgentFacade/ModelFacade）；事务未覆盖 LLM/工具调用；API Key/鉴权凭据未泄露；SSRF 防护生效（内网/元数据地址被拒）。
7. 大表 `tool_call_log` 用 Keyset 分页；列表接口未返回 `input`/`output` 大字段。
