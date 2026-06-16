# P2（工具能力 + ReAct 多轮循环）完成总结

> 对齐 `docs-prompt/prompt-05-tool-capability-and-react.md` §八输出要求。

## 一、DoD 逐条核对（对齐 02-functional-spec.md §18.2）

| # | DoD | 状态 | 说明 |
|---|-----|------|------|
| 1 | HTTP 工具绑定 Agent，对话中自主调用 + 基于结果回答 | ⚠️ 待联调 | 全链路已实现（chat→engine ReAct→model tool-calling→ToolFacade→HttpTool→log→persist+SSE）并经构建/启动/分件烟测；**自主调用闭环需配置真实 tool-calling LLM provider 联调**（当前 DB 无 provider） |
| 2 | OpenAPI 导入工具可被 Agent 选用 | ✅ | 后端 parse/import 经 petstore spec 烟测（19 operation、命名去重、Pet schema 字段正确内联）；前端向导 + ToolBinder 就绪 |
| 3 | MCP Server 发现工具可调用；断连降级 | ✅ 实现/⚠️ 待真实 MCP server 联调 | McpConnectionManager 程序化建连+发现+重连；listAvailableTools 过滤 offline；McpTool 断连降级返回 ERROR 不抛 |
| 4 | tool_call_log 记录输入输出/耗时/状态，前端卡片可下钻 | ✅ | GET /call-logs/{id} 详情含 input/output；列表 Keyset 轻量字段；ToolCallTrace 卡片 + 日志抽屉下钻（实测 DB 通过） |
| 5 | 工具有超时；ReAct 可中断（取消上游）；并行工具；死循环兜底 | ✅ 实现/⚠️ 中断+并行待 LLM 联调 | 超时分层（连接10s/单次30s/总≤60s/循环120s）+ retry+breaker；ReAct 并行 toolExecutor + allOf；死循环 (name,args) 达阈值回灌提示→MAX_TURNS；SSE 断连→future.cancel→dispose |
| 6 | 跨模块只走 Facade；事务未覆盖 LLM/工具；凭据未泄露；SSRF 生效 | ✅ | engine api/domain 无 Mapper/Entity（只走 Agent/Tool/ModelFacade）；网络调用在事务外；凭据加密+不记+不返；SSRF 内网/元数据→1406（实测） |
| 7 | tool_call_log Keyset；列表不返回大字段 | ✅ | Keyset（created_at DESC,id DESC）+ .select 轻量列；不带过滤维度→400（实测） |

> ⚠️ = 功能已完整实现并通过构建/启动/分件烟测，仅差真实 LLM/MCP server 的端到端联调（环境无可用凭据）。

## 二、tool/agent 增量 API + SSE 接口清单

### MCP Server（/api/tool/mcp-servers）
POST（创建+连+发现）｜ GET 列表(OFFSET) ｜ GET /{id} ｜ PUT /{id} ｜ DELETE /{id} ｜ PUT /{id}/enabled ｜ POST /{id}/test ｜ POST /{id}/refresh ｜ POST /test（未保存配置测试）

### HTTP 工具（/api/tool/tools）
POST（手动创建）｜ POST /parse-openapi ｜ POST /import-openapi ｜ GET 列表(OFFSET) ｜ GET /{id} ｜ PUT /{id}（仅 HTTP）｜ DELETE /{id} ｜ PUT /{id}/enabled ｜ POST /{id}/test

### 工具调用日志（/api/tool/call-logs）
GET /{id}（详情含 input/output）｜ GET ?conversationId=&agentId=&toolId=&cursor=&limit=（Keyset，至少一维过滤）

### Agent 工具绑定（/api/agents）
GET /{id}/tools ｜ PUT /{id}/tools（全量覆盖）；GET /{id} 详情增 toolIds/toolSummaries

### SSE（/api/chat/stream?messageId=，不变）
事件：message_delta ｜ **tool_call_start**（新增）｜ **tool_call_end**（新增）｜ done ｜ run_error

## 三、跨模块 Facade 使用点

engine ReActLoop（纯编排，无 DB）：`AgentFacade.getAgentConfig`（→boundToolIds）→ `ToolFacade.listAvailableTools` + `ToolFacade.executeTool` → `ModelFacade.chatStream`（下发 tool 定义）。
agent AgentToolService → `ToolFacade.listAvailableTools`（绑定校验）+ `listToolBindings`（绑定视图）。
chat → `EngineFacade.runChatTurn`（ReAct）；chat 仅引用 model 的异常类型（LlmException，Facade 契约）。
Spring AI 类型（ToolDefinition/AssistantMessage/ToolResponseMessage/ChatResponse/McpSyncClient）全部封装在 model/tool 模块内部，不跨 Facade。

## 四、Spring AI tool-calling 实测结论（spike 记录）

**未做 live spike**（环境无可用 LLM 凭据），按 Spring AI 2.0 API 形态实现并在 `AbstractSpringAiChatClient` 注释记录：
- OpenAiChatOptions/AnthropicChatOptions 经 `ToolCallingChatOptions.Builder.toolCallbacks(List<ToolCallback>)` 附加 ad-hoc ToolCallback（DefaultToolDefinition，name/description/inputSchema）；2.0 `ChatModel.stream()` **不自动执行**（internalToolExecutionEnabled 已移除），仅下发工具定义。
- 用 `MessageAggregator.aggregate(flux, consumer)` 聚合跨 chunk 的 tool_calls；最终 `AssistantMessage.getToolCalls()` → `List<ToolCallDTO>`（finishReason=TOOL_CALLS）；consumer 逐 chunk 把 delta 流给 sink。
- 输入映射：role=TOOL → ToolResponseMessage；ASSISTANT(toolCalls) → AssistantMessage.builder().toolCalls(...)。
- `toolDefinitions=null` 时行为与 P1 完全一致（不附加 toolCallbacks）。
- dispose 取消：保留 P1 的 CountDownLatch + disposable.dispose() 模式，超时/中断取消上游。
- **联调时若行为不符以实测为准修正**（建议：配一个 OpenAI 兼容 provider + 工具，触发一轮对话验证 tool_call_start→tool_call_end→message_delta→done）。

## 五、P3/P4 接入复用点

- **ToolFacade 中立边界**：P3 RAG（若走工具形态）/ P4 工作流 Tool 节点直接复用 `ToolFacade.executeTool(command)` —— 统一超时/重试/熔断/SSRF/截断/tool_call_log 写入。新增 `source_type=WORKFLOW` 工具 + 实现 `WorkflowAsTool implements Tool` 即可被 engine/工作流复用，无需改 Facade。
- **tool_call_log 复用**：`workflow_run_id`/`workflow_node_run_id` 列已建（P2 恒 null），P4 工作流经 `ToolExecContext`（扩字段或新建 WorkflowToolExecContext）传入即可记录；`ToolFacadeImpl.executeTool` 不变。
- **Tool 接口扩展**：P4 Workflow-as-Tool 新增 `domain/workflow/WorkflowTool implements Tool`，`ToolFacadeImpl.buildTool` 按 source_type=WORKFLOW 分派。
- **model chatStream 复用**：工作流 Tool 节点如需 LLM 决策可复用 `ModelFacade.chatStream`（带 tool 定义）。

## 六、构建/启动状态

- 后端 `mvn package -DskipTests` ✅；应用启动 ✅（Flyway V6/V7 建表成功，全部 bean 接线，MCP 启动扫描运行）。
- 前端 `cd zify-web && npm run build` ✅（tsc + vite）。
- 前端 `npm run lint`：16 error 为 `react-hooks/set-state-in-effect`，**P2 前既有**（AgentListPage/ModelPage/ChatPanel 等存量页面同样模式），非 P2 回归。

## 七、未完成/待联调

1. 真实 tool-calling LLM provider 端到端联调（DoD #1/#5 的自主调用闭环 + 中断取消上游实测）。
2. 真实 MCP server 联调（DoD #3 的发现+调用+断连降级实测）。
3. live spike 验证 Spring AI 2.0 stream+tool 的实际 API 形态（§四，若不符以实测修正）。
