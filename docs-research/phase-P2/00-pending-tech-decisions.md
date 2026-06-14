# P2 工具能力 + ReAct 多轮循环 — 待决技术议题清单

> 本文件是 P2（工具能力 + ReAct 多轮循环）开发前的**前置讨论登记表**。
> P2 的正式文档 `01-data-model.md` / `02-functional-spec.md` 待本清单议题全部 ✅ 后再动笔；届时本清单成为它们的「决策溯源」。
> 依据：`glm-docs/02` §1 §3（模块/工具系统）、`glm-docs/07` §一 §3.1（LLM 调用规范）、`glm-docs/12` §五（路线图 P2）、`docs-prd/phase-P1/*`（P1 已定的边界与协议）。

---

## 一、使用方法

1. **逐个推进**：按下文「建议讨论顺序」从 A1 开始，一次讨论一个议题。
2. **回填决策**：讨论结束后，把结论写入该议题卡片的「**决策结果**」区，并把总览表（§三）状态列改为 ✅。
3. **联动正式文档**：决策若影响 `glm-docs/*` 或 P1 spec，在卡片「需回写的正式文档」列明，事后更新（对齐 `CLAUDE.md` §1「需求与本文冲突，先更新决策文档再写代码」）。
4. **新增议题**：讨论中冒出的新技术点，按 `P2-{档位}{序号}` 追加，并在总览表补行。

---

## 二、状态与优先级图例

**状态**：⬜ 待讨论 ｜ 🔄 讨论中 ｜ ✅ 已决 ｜ ⏸️ 搁置（二期）

**优先级**：🔴 关键路径（不先决，后续议题全部受阻）｜ 🟠 前置必要（P2 动手前必须定）｜ 🟡 实现期细化

---

## 三、议题总览

| 编号 | 标题 | 档位 | 优先级 | 状态 | 建议顺序 | 前置依赖 |
|------|------|------|--------|------|----------|----------|
| **P2-A1** | ReAct 循环实现机制（Spring AI Tool Calling vs 手动编排） | 架构级 | 🔴 | ✅ | 1 | — |
| **P2-A2** | 工具调用结果持久化与上下文重建（含 tool_call_log 归属） | 架构级 | 🟠 | ✅ | 3 | A1 |
| **P2-B1** | HTTP/MCP 工具调用的超时/重试/容错规范 | 规范级 | 🟠 | ✅ | 4 | — |
| **P2-B2** | 统一 Tool 接口输入输出契约 + ToolFacade DTO + Schema 下发 | 规范级 | 🟠 | ✅ | 2 | A1 |
| **P2-C1** | MCP Client 传输方式范围 + 是否采用 spring-ai-mcp | 实现级 | 🟡 | ✅ | 6 | B1, B2 |
| **P2-C2** | MCP 连接生命周期（常驻/按需、发现时机、断连重连） | 实现级 | 🟡 | ✅ | 7 | C1 |
| **P2-C3** | HTTP 工具 OpenAPI 解析范围 + 鉴权凭据加密 | 实现级 | 🟡 | ✅ | 8 | B1, B2 |
| **P2-C4** | ReAct 循环控制（终止/最大轮次/中断/死循环兜底） | 实现级 | 🟡 | ✅ | 9 | A1 |
| **P2-C5** | 多轮 + SSE 事件协议扩展（在 P1 协议上加工具事件） | 实现级 | 🟠 | ✅ | 5 | A1, A2 |
| **P2-C6** | 并行工具调用 + 线程模型（不阻塞 reactive 流） | 实现级 | 🟡 | ✅ | 10 | A1, B1 |
| **P2-C7** | 工具安全（SSRF 防护、请求/响应大小限制） | 实现级 | 🟡 | ✅ | 11 | — |
| **P2-D1** | `tool_call_log`(19) 大表字段与存储策略 | 数据级 | 🟡 | ✅ | 12 | A2 |
| **P2-D2** | 工具生命周期与运行时校验（禁用/断连/命名冲突） | 数据级 | 🟡 | ✅ | 13 | B2 |

---

## 四、依赖关系与建议讨论顺序

```text
                         ┌─────────────┐
                    ┌───►│  A1 ReAct机制 │◄──────────── 关键路径，最先做（含 Spring AI 可行性 spike）
                    │    └──────┬──────┘
                    │           │
              ┌─────┴────┐ ┌────┴──────┐
              ▼          ▼ ▼           │
        ┌──────────┐ ┌──────────┐      │
        │B2 Tool接口│ │A2 持久化 │      │
        └────┬─────┘ │ /tool_log│      │
             │       └────┬─────┘      │
             │            │            │
      ┌──────┼──────┬─────┼──────┬─────┤
      ▼      ▼      ▼     ▼      ▼     ▼
    C1(MCP) C3(HTTP) B1(超时) C5(SSE) C6(并发)  C7(安全,独立)
      │             │
      ▼             ▼
    C2(MCP生命周期) D1(大表,依赖A2) → D2(生命周期)
             │
             ▼
          C4(循环控制)
```

**说明**：
- **A1 是命门**——它的结论直接决定 A2（持久化）、B2（接口契约）、C5（SSE 事件）、C6（线程模型）的形态。先做一次 Spring AI 2.0「stream + tool calling」可行性核查再拍板。
- B1（工具调用规范）相对独立，可和 A2/B2 阶段并行起草。
- C7（安全）无前置，可随时插入，但 HTTP 工具落地前必须有一版基本 SSRF 防护。
- C 类多数依赖 B1/B2，所以规范级先于实现级。

---

## 五、议题详情

### A 架构级（必须先拍板，决定整个 P2 形态）

---

#### P2-A1　ReAct 循环实现机制（Spring AI Tool Calling vs 手动编排）

| 项 | 内容 |
|----|------|
| 状态 | ✅ 已决 |
| 优先级 | 🔴 关键路径 |
| 前置依赖 | — |

**背景**：P1 的 engine 是「零工具、单轮 LLM → 流式返回」。P2 要升级为真正的多轮 ReAct：LLM 决策 → 调 `ToolFacade.execute` → 观察回灌 → 再决策 … → 结束（`glm-docs/12` §五）。

**现状/易混淆点**：`glm-docs/02` §1（第 65 行）写「不做 Function Calling 策略，ReAct 已覆盖该场景（循环一轮即结束）」。需澄清：这是**产品形态**层面的话（不单独做「单轮 FC 应用」），**不等于**禁止使用 Spring AI 的 tool-calling 机制。本议题要明确技术实现路径。

**关键问题**：
1. 用 Spring AI 2.0 内置 Tool Calling（注册 `ToolCallback`，框架自动跑「决策→执行→回灌→再决策」循环），还是 engine 手动解析模型输出、自己拼 Observation、自己控循环？
2. 若用 Spring AI 自动循环：在 **stream + tool calling** 模式下，能否把每次工具调用以 SSE 事件暴露给前端？能否中途 `dispose` 取消整轮（含进行中的工具执行）？最大轮次/终止条件能否注入？
3. 若用手动 ReAct：prompt 协议（Thought/Action/Observation vs 约定 JSON）、解析容错、回灌格式的实现成本。

**备选方案**：
- **方案① Spring AI 自动循环**：engine 代码量最小，循环逻辑由框架承担；风险是细粒度控制（自定义事件、取消、轮次上限）依赖框架能力，需 spike 验证。
- **方案② 手动 ReAct 编排**：可控性最高，事件/取消/轮次完全自定；代价是自己实现协议解析与回灌，工作量大、容错面广。
- **方案③ 混合**：用 Spring AI 的 `ToolCallback`/Schema 体系（省去手撸 JSON Schema 下发），但循环由 engine 手动驱动（每轮显式调 `ModelFacade.chatStream`，自行解析 tool call、调 `ToolFacade.execute`、拼回下一轮）。

**影响面**：A2、B2、C4、C5、C6 的形态全部由此决定；engine 模块的核心类设计与代码量。

**关联文档**：`glm-docs/07` §3.1（Spring AI stream / 取消=dispose）、`docs-prd/phase-P1/02` 第八章（ModelFacade.chatStream）。

**决策结果**：✅ 已决（2026-06-14）
> **决定**：采用**方案③**——基于 Spring AI 2.0 **User-Controlled Tool Execution（streaming 模式）**。复用 P1 的 `ChatModel.stream()`，engine 用 `ToolCallingManager` 手动驱动 ReAct 多轮循环。
>
> **落地形态**：
> 1. `ChatModel` 直接调用（2.0 起 `internalToolExecutionEnabled` 已移除，`ChatModel.stream()` 默认只下发 tool 定义、**不自动执行** tool call）。
> 2. 每轮 `chatModel.stream(prompt)` 用 `MessageAggregator` 聚合 chunks，token 流 forward 到 `TextStreamSink`（SSE）。
> 3. 聚合后若 `chatResponse.hasToolCalls()` → `toolCallingManager.executeToolCalls(prompt, chatResponse)` 执行；Zify Tool 实现 Spring AI `ToolCallback` 接口（`call()` 内部走 tool 模块的 HTTP/MCP 执行）。
> 4. 用 `ToolExecutionResult.conversationHistory()` 作为下一轮 `Prompt`，`while(hasToolCalls())` 循环至无 tool call。
>
> **理由**：
> - **与 P1 零返工**：`AbstractSpringAiChatClient` 的 reactive→`TextStreamSink` 桥接、deadline、`dispose` 取消、异常分类全部保留；`LlmChatGateway` 的 `ProviderBulkhead`（每 Provider 并发隔离）+ `LlmRetryWrapper`（首 chunk 前重试）+ 解密 Key + 结构化日志全部保留——每轮 ReAct 仍是一次 `ModelFacade.chatStream`，per-call 超时/重试/隔离正合适。方案①（ChatClient+ToolCallingAdvisor 自动循环）会绕过这套封装、需整体重接，不取。
> - **官方一等支持**：Spring AI 2.0 文档「User-Controlled Tool Execution → With ChatModel (streaming)」有完整示例，原话 *"giving you full control over what is emitted at each step"*。
> - **控制粒度最细**：SSE 工具事件（`executeToolCalls` 前后自定）、取消（每轮 cancel 透传）、最大轮次、死循环兜底全在 engine 自控——正好满足 C4/C5。
> - **放弃方案②**（文本 Thought/Action/Observation prompt ReAct）：放弃 Provider 原生 function calling 的准确性，容错面广，无谓。
> - **衔接 B2 干净**：Zify Tool 实现 `ToolCallback`（`getToolDefinition()` 返回 name/description/inputSchema，`call()` 执行），Schema 下发 Spring AI 自动处理；依赖方向合法（engine→tool）。
>
> **澄清 `glm-docs/02` §1 第 65 行**：「不做 Function Calling 策略，ReAct 已覆盖（循环一轮即结束）」是**产品形态**层面（不单独做单轮 FC 应用），**不禁止**用 Provider 原生 tool calling 作实现手段。Zify 的 ReAct = **Provider 原生 tool calling + engine 手动驱动多轮循环**，与该句不冲突。
>
> **连带影响**（标注给后续议题）：
> 1. `ModelFacade.chatStream` 的 `ChatCompletionResult` 需扩展 tool call 信息（`finishReason=TOOL_CALLS` + toolCalls 列表），从 Spring AI `AssistantMessage.toolCalls` 转换；**不把 Spring AI domain 类型泄漏到 api 层**（守 `CLAUDE.md` §3 Facade 边界）→ **A2 / B2** 跟进。
> 2. tool 执行归属：engine 持 `ToolCallingManager`，Zify Tool 实现 `ToolCallback`，`call()` 内部走 tool 模块（HTTP/MCP）→ 具体签名在 **B2** 定。注意：**非**清单原方案③ 所述「engine 自行解析 tool call + 直接调 `ToolFacade.execute`」，改为复用 `ToolCallingManager.executeToolCalls`（B2 确认）。
> 3. SSE 事件：`executeToolCalls` 前后发 `tool_call_start` / `tool_call_end` → **C5** 定字段。
> 4. 上下文管理：工具结果（`ToolResponseMessage`）进历史后 token 占用大，P1 摘要压缩策略（`02` §5.5）需把「一个 tool turn 视为整体折叠」→ **A2 / C4** 复核。
>
> **落地前提（实现前 spike）**：双 provider（OpenAI 兼容 + Anthropic）验证 `ChatModel.stream()` + `MessageAggregator` 能正确聚合出 `hasToolCalls()` 且 token 逐块流到 `TextStreamSink`；同时验证 `dispose` 能取消当前轮 stream。半天可验。`OpenAiChatModel.stream()` 的 tool call 丢失 bug 已在 2.0 修复（RC1 release notes）。
>
> **需回写的正式文档**：
> - `glm-docs/02` §1：加澄清脚注（ReAct 实现机制 = 原生 tool calling + 手动循环）。
> - `glm-docs/07` §3.1：补充 tool calling streaming 的循环模式说明（或留到 P2 `02-functional-spec.md`）。
> - `docs-prd/phase-P1/02` §5.2：SSE 协议预留 `tool_call` 分支 → C5 扩展时落实。
>
> **决策日期**：2026-06-14
>
> ---
>
> **🔴 精度修正（B2 落地后补，2026-06-14）**：深入 B2 设计后发现，原决策中「engine 持 `ToolCallingManager`、Zify Tool 实现 `ToolCallback`」的实现措辞需修正为**中立化边界**：
> - **engine 不使用** Spring AI `ToolCallingManager`——它需 `ChatResponse`（Spring AI domain 类型），按 §3 Facade 边界不能跨 Facade 泄漏到 engine。engine 改为在**中立 DTO 层面**解析 tool call 请求 → 调 `ToolFacade.executeTool` → 拼 tool 响应消息。
> - **Zify Tool 不实现** Spring AI `ToolCallback`——tool 模块保持中立（pom 不引入 spring-ai）。Spring AI 的 tool 类型（`ToolDefinition`/`ToolResponseMessage`/`ChatResponse`）**全部封装在 model 模块内部**。
> - **循环主体仍在 engine**（不变），但每轮通过 `ModelFacade.chatStream`（单轮、带 tool 定义下发）调用；model 只做单轮（构造 `ChatModel`、解密、bulkhead、retry、stream、返回 tool call 请求 DTO）。
> - **核心结论不变**：方案③、手动驱动循环、复用 P1 网关封装、Provider 原生 function calling。仅 Spring AI 类型的封装位置从「engine/tool」收敛到「model」。详见下文 P2-B2 卡片决策。

---

#### P2-A2　工具调用结果持久化与上下文重建（含 tool_call_log 归属）

| 项 | 内容 |
|----|------|
| 状态 | ✅ 已决 |
| 优先级 | 🟠 前置必要 |
| 前置依赖 | A1 |

**背景**：路线图同时说「工具结果作为消息历史一部分」和「写入 `tool_call_log`(19)」，二者关系未定。

**⚠️ 与 P1 已定边界的张力**：`docs-prd/phase-P1/02` §2.1 明确「**engine 是纯编排，不读写任何数据库表**」。但路线图 P2 §五写「engine（扩）：写入 `tool_call_log`(19)」。这两者冲突，**必须先澄清 tool_call_log 由谁落库**。

**关键问题**：
1. 工具调用过程是否进 `message` 表（作为对话历史一部分，供刷新/继续对话时重建上下文），还是只进 `tool_call_log`、重建时按 turn 关联两张表拼装？
2. `tool_call_log` 由谁写？
   - 方案A：engine 写（需修订 P1 §2.1 的「engine 不碰 DB」边界）；
   - 方案B：chat 写（保持 engine 纯编排，工具事件经 `EngineFacade` 回传给 chat 落库，类比 ASSISTANT 消息落库）；
   - 方案C：`ToolFacade.execute` 内部自写（tool 模块自包含日志）。
3. 工具结果进入历史后，对 P1 已定的**上下文管理**（摘要压缩 + 尾部截断，`02` §5.5）有何影响？工具调用/结果通常 token 占用大，摘要策略是否要把 tool turn 视为一个整体折叠？
4. Spring AI 的 `AssistantMessage.toolCalls` + `ToolResponseMessage` 如何映射到 P1 的 `message` 表结构（是否需扩字段存 toolCalls JSON）？

**影响面**：P1 `message` 表是否扩展、上下文重建逻辑、P1 §2.1 边界是否修订、D1（大表字段）。

**关联文档**：`docs-prd/phase-P1/02` §2.1、§4.5、§5.5（上下文管理）、`01-data-model.md`（message 表）。

**决策结果**：✅ 已决（2026-06-14）
> **决定 1 — `tool_call_log` 归 tool 模块**（修订 `glm-docs/11`：engine→tool）：写入点 = `ToolFacade.executeTool` 内部（执行点即记录点）。
> - 理由：tool_call_log 须同时服务对话（engine 经 ToolFacade）与工作流（workflow 经 ToolFacade，P4）；**唯一能被两者依赖的是 tool 模块**（`engine→tool`、`workflow→tool` 均允许）。归 engine 则工作流场景无人能写（workflow 不依赖 engine，依赖图无 engine）。
> - 澄清：glm-docs/11 §五「可由对话引擎或工作流触发」**保留正确**——触发方≠归属模块。
> - 上下文：`conversation_id`（对话场景）/ `workflow_run_id`（工作流，P4）由 `ToolExecContext`（B2）传入，皆 nullable。
>
> **决定 2 — 工具过程进 message 表**（方案 X）：ASSISTANT toolCall 请求 + TOOL 响应都落 message 表。
> - 硬要求：模型继续对话时 Spring AI 需之前的 `AssistantMessage(toolCalls)`+`ToolResponseMessage` 才能理解多轮工具上下文；只存最终 ASSISTANT 会让上下文断裂。
> - P1 message 表 role 已预留 TOOL，**无需改列**；扩展 `metadata` JSON：
>   - `ASSISTANT`(toolCall)：增 `toolCalls:[{id,name,args(JSON)}]`；
>   - `TOOL`：`content`=工具结果 output，metadata=`{toolCallId,toolName,toolCallLogId}`；
>   - 最终 ASSISTANT 文本：metadata 不变（modelId/tokens/finishReason）。
> - 职责分离：message=对话流（前端+上下文重建），tool_call_log=详细执行日志（调试），经 `toolCallLogId` 关联。
>
> **决定 3 — engine 保持纯编排**（P1 §2.1 **不改**）：engine 不碰任何 DB（message/conversation 归 chat，tool_call_log 归 tool）。三处写入点清晰互不越界。
> - `ChatTurnResult` 扩展：从「单 content」→ 返回本轮新增消息序列 `List<ChatMessage> newMessages`（ASSISTANT toolCall + TOOL + 最终 ASSISTANT）；`content` 保留（=最终文本，供 SSE `done`）。
> - `persistAssistantTurn`(单条) → `persistTurn`(批量落库本轮序列，短事务、事务内只 DB 写)。
> - message 表是否扩展：**不新增列**，仅扩 `metadata` JSON 结构（见决定 2）。
>
> **决定 4 — 摘要压缩以 turn 为单位**（扩展 P1 §5.5）：
> - turn = 1 USER + 其后所有 ASSISTANT/TOOL（到下一 USER）。
> - 摘要折叠 / 尾部截断**以 turn 为整体**，绝不拆散工具消息序列（否则模型丢失 toolCall↔response 配对）。
> - `loadActiveWindow` 扩展：`role IN (USER, ASSISTANT, TOOL)`，按 turn 重建。（对照 Spring AI 2.0 RC1 `MessageWindowChatMemory` 的 turn-boundary snapping，Zify 自管上下文自行保证。）
>
> **决定 5 — `tool_call_log` ↔ TOOL message 关联**：TOOL message `metadata.toolCallLogId` 指向 tool_call_log 主键，前端可下钻查看详细输入/输出/耗时/状态（C5 的 `tool_call_end` 事件同带此 id）。
>
> **解决 P1 §2.1 张力**：原张力（路线图「engine 写 tool_call_log」vs P1 §2.1「engine 不碰 DB」）由「tool_call_log 归 tool 模块、tool 写」彻底化解——engine 仍是纯编排，**P1 §2.1 无需修订**。
>
> **连带影响 / 需回写**：
> 1. `glm-docs/11` §一/§四表 19：tool_call_log 归属 **engine→tool**；§二/§三关系 `message 1:N tool_call_log` → `message(TOOL) 0..1:1 tool_call_log`（经 metadata 关联）；§五归属澄清。
> 2. P1 `01-data-model` §四 `message.metadata`：JSON 结构扩展（toolCalls/toolCallId/toolCallLogId）。
> 3. P1 `02-functional-spec` §5.5：摘要压缩 turn 边界语义；§2.1 保持。
> 4. `ChatTurnCommand/Result`、`ChatStreamService.runTurn`、`MessageService.persistAssistantTurn`→`persistTurn`、`loadActiveWindow` 扩展。
> 5. D1：tool_call_log 增 `conversation_id`/`workflow_run_id`（nullable）+ `tool_call_id`（关联 TOOL message）。
>
> **决策日期**：2026-06-14

---

### B 规范级（文档留白，P2 硬前置）

---

#### P2-B1　HTTP/MCP 工具调用的超时/重试/容错规范

| 项 | 内容 |
|----|------|
| 状态 | ✅ 已决 |
| 优先级 | 🟠 前置必要 |
| 前置依赖 | — |

**背景**：`glm-docs/07` §一明确「**不覆盖** HTTP 工具调用、MCP Server 调用……后续按类似原则单独定义」。而 `CLAUDE.md` §9 要求「所有外部调用必须有超时」、§6 要求「SSE 断开/超时时必须取消上游」。P2 引入工具调用，这套规范必须先定义，否则硬约束无法落地。

**关键问题**：
1. HTTP 工具、MCP 工具各自的**连接超时 / 读取超时 / 总 deadline** 取值；MCP 连接（含 initialize 握手）超时。
2. **重试策略**：哪些错误可重试（连接超时、5xx、429）、最大次数、是否区分幂等；MCP 调用重试是否会触发 Server 端副作用重复。
3. **熔断 / 降级**：某工具连续失败是否短期熔断、ReAct 循环中工具失败如何表达给 LLM（错误作为 Observation 回灌 vs 直接中断整轮）。
4. 输出规范：是否新建 `glm-docs/13-zify-tool-calling-spec.md`，还是扩写进 `07`。

**影响面**：C1–C3 的实现、C6（并发）、CLAUDE.md §10 检查清单「外部调用有超时」项。

**关联文档**：`glm-docs/07` 全文（参照其结构）、`CLAUDE.md` §6 §9。

**决策结果**：✅ 已决（2026-06-14）
> **核心差异**：工具调用与 LLM 的根本区别在**幂等性**——LLM 天然幂等可自由重试，工具多数非幂等（POST/写操作）重试会重复副作用。B1 全部策略围绕此展开。
>
> **超时（分层 + 可配）**：
> | 类型 | 默认 | 说明 |
> |---|---|---|
> | 连接超时 | 10s | TCP/TLS 建连；MCP 含 initialize 握手 15s |
> | 单次请求超时 | 30s | 单次 HTTP 完整请求+读取；**工具定义 `timeout_seconds` 可覆盖** |
> | 总 deadline | ≤60s | 含重试；且不超过 ReAct 循环剩余时间（衔接 C4） |
> - YAML：`zify.tool.timeout.{connect:10s, mcp-handshake:15s, request-default:30s, total-cap:60s}`。
>
> **重试（幂等性驱动，显式 wrapper，不用 `@Retryable`）**：
> | 失败时机 | 幂等工具(GET/HEAD 或声明 idempotent) | 非幂等工具(POST/PUT/DELETE/PATCH 默认) |
> |---|---|---|
> | 建连失败（连接超时/被拒） | ✅可重试 | ✅**可重试**（请求未送达，无副作用） |
> | 请求已发出后失败（读超时/5xx/429） | ✅可重试 | ❌**不重试**（不确定是否已执行） |
> | 4xx（参数/认证/404） | ❌不重试 | ❌不重试 |
> | 熔断中 | ❌直接失败 | ❌直接失败 |
> - 参数对齐 07 §5.3（max 2 次、退避倍率 2、jitter 20%、遵守总 deadline）。
> - **tool 表新增 `idempotent` 字段**：HTTP 按 method 推断默认（GET=true, POST=false），用户可覆盖；MCP 默认 false，用户显式声明。（D1 落字段）
>
> **熔断（per `tool_id`）**：对齐 07 §6.2 进程内轻量熔断，键改 `tool_id`。连续 5 次可重试失败 → OPEN 60s → HALF_OPEN 探测 1 次；4xx 不计入。熔断中该工具本轮不可用。
>
> **并发保护（原则，线程池细节归 C6）**：全局有界工具执行线程池（虚拟线程/有界 ThreadPoolExecutor）；一期先全局池，per-tool 并发上限留二期。
>
> **失败回灌（衔接 B2，`ToolFacade.executeTool` 返回 status=ERROR 不抛）**：
> | 失败类型 | output（回灌模型） |
> |---|---|
> | 可重试故障重试耗尽 | `工具 <name> 暂时不可用，请稍后重试或换一种方式` |
> | 参数/认证错误(4xx) | `调用工具 <name> 失败：<精简错误>` |
> | 熔断中 | `工具 <name> 当前不可用` |
> | 非幂等执行失败 | `工具 <name> 执行失败：<精简错误>` |
> - 模型据此自主决策（换工具/改参数/直接回答），**不中断 ReAct 循环**（致命错误除外，C4 定）。
>
> **异常/HTTP client**：HTTP 工具用 RestClient（同步，对齐 07 §3.1）；MCP 用 spring-ai-mcp-client（C1 定）。tool 模块 `infrastructure/` 定义 `ToolException` 体系（Retryable/NonRetryable/Timeout/Busy/CircuitOpen），**对 engine 只暴露 DTO**（B2 中立边界），异常内部消化成 status=ERROR。
>
> **规范落点**：新建 `glm-docs/13-zify-tool-calling-spec.md`（07 §一已声明工具调用单独定义；幂等性使重试逻辑与 LLM 根本不同，单独成篇）。07 保持 LLM 专注。
>
> **连带影响**：
> 1. `glm-docs/13` 新建（按 07 结构：超时/重试/熔断/异常/日志）。
> 2. tool 表新增 `timeout_seconds`/`idempotent` 字段（D1）。
> 3. C1–C3 实现遵循本规范；C6 定线程池细节；C4 定循环总 deadline 与单工具超时的衔接。
> 4. CLAUDE.md §10 检查清单「外部调用有超时」项适用于工具调用。
>
> **决策日期**：2026-06-14

---

#### P2-B2　统一 Tool 接口输入输出契约 + ToolFacade DTO + Schema 下发

| 项 | 内容 |
|----|------|
| 状态 | ✅ 已决 |
| 优先级 | 🟠 前置必要 |
| 前置依赖 | A1 |

**背景**：`glm-docs/02` §3 给了统一 Tool 接口签名 `name / description / parameters / execute()`，但运行时契约未定。engine 既要「执行工具」，又要「把 Agent 绑定工具的参数 Schema 喂给 LLM」。

**关键问题**：
1. `parameters` 是否就是喂给 LLM 的 **JSON Schema**？由谁生成：手填（HTTP 工具）／OpenAPI 解析（HTTP 工具）／MCP `tools.list` 返回的 schema（MCP 工具）？
2. `execute()` 的**入参**（`Map<String,Object>`？）、**返回值结构**（成功/失败/结构化数据如何统一表达，便于回灌为 Observation）、**异常**如何转化为「给模型的文本」。
3. **ToolFacade 方法划分**：除 `execute(toolId, args)` 外，是否单列「按 agentId 取绑定工具的 Schema 列表」方法，供 engine 下发给 LLM？
4. 跨模块 Facade 不能返回 Entity/分页（`CLAUDE.md` §3）：ToolFacade 的 DTO 边界如何画（`ToolDefinitionDTO` / `ToolExecutionResultDTO`）。
5. 与 A1 选定机制的衔接：若用 Spring AI `ToolCallback`，统一接口是否直接实现 `ToolCallback`；若手动 ReAct，接口如何独立。

**影响面**：tool 模块核心抽象、engine 调用方式、agent 工具绑定接口、C1–C3 全部。

**关联文档**：`glm-docs/02` §3、`CLAUDE.md` §3（Facade 边界）、`docs-prd/phase-P1/02` §九（Facade 契约风格）。

**决策结果**：✅ 已决（2026-06-14）
> **核心：中立化边界**（深入设计后修正了 A1 的实现措辞，A1 核心结论不变）
>
> | 模块 | 职责 | 碰 spring-ai 类型？ |
> |------|------|------------------|
> | **tool** | 工具定义存储 + HTTP/MCP 执行 + tool_call_log | ❌ 完全中立（pom 不加 spring-ai） |
> | **engine** | ReAct 循环编排（轮次/中断/SSE 事件/上下文） | ❌ 只用中立 DTO |
> | **model** | 单轮带 tool 的流式 LLM 调用 | ✅ 封装 `ToolDefinition`/`ToolResponseMessage`/`ChatResponse` |
>
> **为何中立化（约束驱动，唯一合规解）**：
> - engine 不能用 Spring AI `ToolCallingManager`——其 `executeToolCalls(prompt, chatResponse)` 需 `ChatResponse`，按 §3 不能跨 Facade 泄漏。
> - engine 不能直接 `chatModel.stream()`——会持有解密后的 `ChatModel`（含 API Key），违反 §6 + 绕过 `LlmChatGateway`。
> - Tool 不实现 `ToolCallback`——避免给中立 tool 模块引入 spring-ai。
> - 循环必须在 engine（engine=编排；放 model 会把 ReAct 编排 + SSE 事件 + 上下文管理塞进 model，违背模块职责），且每轮须走 `ModelFacade.chatStream` 以复用网关。
>
> **`ToolFacade` 契约**（`com.zify.tool.api.ToolFacade`，中立）：
> ```java
> List<ToolViewDTO> listBoundTools(String agentId);          // engine 首轮取全量、内存缓存
> ToolExecutionResultDTO executeTool(ToolExecutionCommand command);
> ```
> - `listBoundTools` 只返回 `enabled=1` 且来源可用（MCP 在线等）的工具；`name` 全局唯一（D2）。
>
> **DTO**（`com.zify.tool.api.dto`，全部中立）：
> - `ToolViewDTO { id, name, description, inputSchema(String JSON), sourceType(HTTP/MCP/WFT) }`
> - `ToolExecutionCommand { toolId, args: Map<String,Object>, context: ToolExecContext }`
> - `ToolExecContext { conversationId, agentId, turn }`（不发给模型，纯审计）
> - `ToolExecutionResultDTO { status(SUCCESS/ERROR), output(String), durationMs, error? }`
>
> **tool 模块内部统一接口**（`domain`，中立，不跨 Facade）：
> ```java
> interface Tool {
>     ToolView toView();
>     ToolExecutionResult execute(Map<String,Object> args, ToolExecContext ctx);
> }
> ```
> HTTP/MCP/Workflow-as-Tool 各自实现；`ToolFacadeImpl` 按 toolId 选实现执行。与 §3「Facade 不返回 Entity」不冲突（对外只暴露 DTO）。
>
> **inputSchema**：`tool` 表存 `input_schema` 列（JSON Schema 字符串）；运行时**不生成**，只在「定义工具时」生成一次入库（HTTP 手填/OpenAPI 解析→C3；MCP `tools.list`→C1）；`ToolViewDTO.inputSchema` 原样透传 → model 内部转 Spring AI `ToolDefinition.inputSchema()`。
>
> **异常与错误回灌**：`executeTool` 内部捕获异常 → 返回 `status=ERROR`（output=错误描述），不向 engine 抛；engine 见 ERROR → 把 output 作为 TOOL 消息回灌（对齐 Spring AI `spring.ai.tools.throw-exception-on-error=false`）。仅致命错误（绑定失效/超轮次）才中断整轮发 `run_error`（C4 定）。
>
> **衔接 A1（已同步落 A1 精度修正）**：engine 跑循环但**不用** `ToolCallingManager`、Tool **不实现** `ToolCallback`；spring-ai tool 类型全封装在 model。model 侧接口扩展（`ChatCompletionCommand` 增 `toolDefinitions`、`ChatCompletionResult` 增 `toolCalls`、`ChatMessage` 支持 role=TOOL/toolCallId）方向已定，具体字段归 A2/B2 共管落 P2 spec。
>
> **连带影响**：
> 1. model 模块：`ModelFacade.chatStream` + `ChatCompletionCommand/Result`/`ChatMessage` 扩展（中立 DTO，spring-ai 类型封装在内）→ **A2** 定持久化时一起定字段。
> 2. agent 模块：`agent_tool` 关联表（P2 新建）+ Agent 表单工具绑定步骤 → 归 P2 spec。
> 3. C1/C3：inputSchema 的「生成并存库」具体方式。
>
> **需回写的正式文档**：P2 `01-data-model.md`（tool/agent_tool 表）、`02-functional-spec.md`（ToolFacade 契约 + 三模块分工图）。
>
> **决策日期**：2026-06-14
>
> ---
>
> **⚠️ 精度修正（C1 落地后补，2026-06-14）**：本决策「tool 模块中立（pom 不加 spring-ai）」需精确为「**接口中立**」——`ToolFacade`/`Tool` 接口不含 spring-ai 的 **LLM 抽象**类型（`ChatModel`/`ToolCallback`/`ChatResponse`），但 tool 模块 **infrastructure 层**可用 `spring-ai-mcp-client`（MCP 协议实现，核心类型 `McpClient`/`McpSchema.Tool`，非 LLM 抽象，与 HTTP 工具用 RestClient 同性质）。上方表中「完全中立（pom 不加 spring-ai）」据此理解为「接口零 spring-ai LLM 抽象」。核心不变：Tool 不实现 `ToolCallback`、engine 不碰 spring-ai tool 类型。详见下文 P2-C1 决策。

---

### C 实现级（范围与策略，动手前明确）

---

#### P2-C1　MCP Client 传输方式范围 + 是否采用 spring-ai-mcp

| 项 | 内容 |
|----|------|
| 状态 | ✅ 已决 |
| 优先级 | 🟡 实现期细化 |
| 前置依赖 | B1, B2 |

**关键问题**：
1. MCP 标准传输有 stdio / SSE / Streamable HTTP 三种。一期是否**只做 SSE + Streamable HTTP、不做 stdio**（Zify 是 Web 服务，stdio 多用于本地）？
2. 是否采用 `spring-ai-mcp-client`（Spring AI 2.0 生态）承接连接/发现/调用，还是自实现 MCP 协议（initialize / tools.list / tools.call）？
3. 工具发现的粒度：一个 MCP Server 下的多个 tool 如何注册进统一 Tool 体系（每个 remote tool → 一条 `tool` 记录？来源标记 `mcp_server_id`）。

**关联文档**：`glm-docs/02` §3、`glm-docs/12` §五、`glm-docs/07` §3.1（客户端选型一致性）。

**决策结果**：✅ 已决（2026-06-14）
> **决定 1 — 传输范围：只做 Streamable-HTTP + SSE，不做 stdio**。
> - Zify 是 Web 服务（Docker/K8s），stdio 需启动 Server 子进程 + 管理进程生命周期，一期复杂、部署不优雅；远程 Server 用 HTTP/SSE 是主流（Streamable-HTTP 是 SSE 的现代替代）。
> - 用 `spring-ai-starter-mcp-client`（HttpClient 版，激活 HTTP/SSE + Streamable-HTTP）。
>
> **决定 2 — MCP 客户端：用 spring-ai-mcp-client，关闭 ToolCallback auto-config**。
> - `McpClient`（**SYNC**，对齐 07 §3.1 同步风格）做连接/发现/调用。
> - `spring.ai.mcp.client.toolcallback.enabled=false`（不用 starter 把 MCP 工具包成 `ToolCallback` 注册给 ChatClient——B2 决策 Zify 不用 ChatClient/ToolCallback）。
> - tool 模块自己适配：`McpClient.listTools()` → `ToolViewDTO`（inputSchema 取 `McpSchema.Tool.inputSchema()`）；`McpClient.callTool(name, args)` → `ToolExecutionResultDTO`。
> - `requestTimeout` 30s（`McpClient.SyncSpec.requestTimeout`，对齐 B1 单次请求超时）。
>
> **决定 3 — 工具发现粒度**：一个 MCP Server → `listTools()` → 每个 remote tool 注册一条 Zify `tool` 表记录：`source_type=MCP`、关联 `mcp_server_id`、`input_schema`=MCP tool 的 inputSchema。（C2 定连接生命周期/发现时机/缓存；D2 定命名冲突去重）
>
> **B2 中立边界修正**（已同步回填 B2 卡片）：tool 模块「中立」从「pom 零 spring-ai」精确为「**接口中立**」——接口不含 spring-ai LLM 抽象（`ChatModel`/`ToolCallback`/`ChatResponse`），但 infrastructure 层可用 `spring-ai-mcp-client`（MCP 协议实现，与 HTTP 工具用 RestClient 同性质）。备选（未采纳）：官方 MCP Java SDK `io.modelcontextprotocol:mcp`（独立于 spring-ai，但无 Boot 自动配置、需手动管理连接，一人开发成本高）。
>
> **连带影响**：
> 1. tool 模块 pom 加 `spring-ai-starter-mcp-client`（B1 已预告）。
> 2. `application.yml`：`spring.ai.mcp.client.*` 配置（type=SYNC、toolcallback.enabled=false、各 server 连接配置）。
> 3. C2 定连接生命周期（starter 提供 `toolsChangeConsumer` 供工具列表变更通知）；D2 定命名冲突（starter 的 `DefaultMcpToolNamePrefixGenerator` 在 ToolCallback 层，关闭后 Zify 自行去重）。
> 4. tool 表 `source_type=MCP` + `mcp_server_id`（D1/D2）。
>
> **决策日期**：2026-06-14

---

#### P2-C2　MCP 连接生命周期（常驻/按需、发现时机、断连重连）

| 项 | 内容 |
|----|------|
| 状态 | ✅ 已决 |
| 优先级 | 🟡 实现期细化 |
| 前置依赖 | C1 |

**关键问题**：
1. MCP Server 连接是**常驻保活复用**（启动/首次使用时建连、保活、复用）还是**按需建立**（每次调用建连用完即关）？常驻涉及连接池/心跳/超时回收。
2. 工具发现时机：连接时一次性发现并缓存入 `tool` 表，还是每次调用前动态刷新？缓存失效如何处理（Server 增删了工具）。
3. **断连/Server 重启**：检测机制、自动重连策略、重连失败时该 Server 下工具的降级（标记不可用？）。
4. 连接与并发：同一 Server 的多个并发工具调用是否复用单连接、有无排队。

**关联文档**：`glm-docs/07`（连接/重试原则）、`glm-docs/12` §五。

**决策结果**：✅ 已决（2026-06-15）
> **决定 1 — 连接模式：常驻保活**（非按需）：starter 应用启动时连接已配置的 `mcp_server`，复用连接；用户新增 server → 即时建连；删除/禁用 server → 关闭连接。不按需建连（ReAct 多次调同一 server，反复建连开销大）。
>
> **决定 2 — 工具发现时机：连接时一次性 + `toolsChangeConsumer` 增量**：连接建立后 `listTools()` → 写 `tool` 表（`source_type=MCP`、`mcp_server_id`、`input_schema`=MCP tool schema）；`toolsChangeConsumer`（starter 提供）监听 server 端工具增删 → 增量更新 tool 表（启用/禁用/新增/软删）；不每次调用前刷新（`listTools` 已缓存）。
>
> **决定 3 — 断连重连 + 状态标记**：starter 内置重连；Zify 层 `mcp_server` 表存连接状态（`ONLINE`/`OFFLINE`/`ERROR`）。重连失败 → 标记 `ERROR` → `listBoundTools`（B2）时该 server 下工具不可用（衔接 D2 降级）；连接恢复 → 重新 `listTools` 刷新 tool 表 + 状态回 `ONLINE`。
>
> **决定 4 — 并发：单连接复用**：每个 `mcp_server` 一条 `McpClient` 连接，并发调用复用（MCP JSON-RPC 单连接多请求）；不需 per-server 连接池（一期 MCP 调用量不大）。
>
> **连带影响**：
> 1. `mcp_server` 表：连接配置（`base_url`/`transport_type`/`auth_config`）+ 状态字段（`ONLINE`/`OFFLINE`/`ERROR`）（D1 落字段）。
> 2. 新增 server 即时连接、toolsChange 增量更新 tool 表：Zify 层实现（starter 提供 hook）。
> 3. server 状态变更 → tool 可用性联动（D2）。
>
> **决策日期**：2026-06-15

---

#### P2-C3　HTTP 工具 OpenAPI 解析范围 + 鉴权凭据加密

| 项 | 内容 |
|----|------|
| 状态 | ✅ 已决 |
| 优先级 | 🟡 实现期细化 |
| 前置依赖 | B1, B2 |

**关键问题**：
1. 「手动配置 URL/Header/Body」与「OpenAPI Schema 解析」两种定义方式如何统一进 Tool 接口？
2. OpenAPI 解析支持哪些版本（3.0 / 3.1）？一个 spec 映射成「一个工具」还是「每个 operation → 一个工具」？
3. 鉴权：Header/Body 中的 token / API Key 属敏感信息，是否像 Provider API Key 一样**加密存储**（复用 `SecretEncryptor`）？`CLAUDE.md` §7「API Key 加密存储」。
4. 运行时参数注入：LLM 填的参数如何映射到 path/query/header/body（OpenAPI 的 `in` 字段）。

**关联文档**：`glm-docs/02` §3、`docs-prd/phase-P1/02`（SecretEncryptor 复用）、`CLAUDE.md` §7。

**决策结果**：✅ 已决（2026-06-15）
> **决定 1 — 两种定义方式统一**（底层同构）：手动配置（用户填 endpoint/method/header/body 模板 + inputSchema）与 OpenAPI 解析（导入 spec 自动提取）产出**同构的 tool 配置**——底层 `tool` 表存统一的「HTTP 工具配置」。
>
> **决定 2 — OpenAPI 解析范围**：版本 OpenAPI **3.0/3.1**；映射粒度**一个 operation → 一个 tool**（path+method 唯一标识，一个 spec 多 operation → 多 tool）；解析库 **Swagger Parser**（`io.swagger.parser.v3:swagger-parser-v3`，OpenAPI 解析事实标准，新依赖已确认引入）。解析产出每个 operation → `endpoint`(baseUrl+path)/`method`/参数(name/in/type/required) → 生成 `input_schema` + 参数映射。
>
> **决定 3 — 鉴权凭据加密**：Header/Body 里的 token/API Key 敏感信息**加密存储**，复用 `common.SecretEncryptor`（P1 Provider API Key 已用）；明文仅执行时解密、**不记录、不返回**（对齐 §6）。`tool` 表存 `auth_config`（加密 JSON）。
>
> **决定 4 — 参数映射（OpenAPI `in` 字段）**：LLM 填的 args 按 `in` 映射——`path`→填充 URL 模板（`/users/{id}`）、`query`→拼 query string、`header`→设请求头、`body`→request body。手动配置工具：用户定义参数→path/query/header/body 映射或固定模板。
>
> **连带影响**：
> 1. `tool` 表：`endpoint`/`method`/`params_mapping`(JSON)/`headers_template`(JSON)/`body_template`/`auth_config`(加密 JSON)/`input_schema`（D1 落字段）。
> 2. HTTP 工具执行：tool 模块 infrastructure 层，用 RestClient（对齐 07/B1），按 `params_mapping` 构造请求。
> 3. 新依赖：`io.swagger.parser.v3:swagger-parser-v3`（tool 模块 pom）。
>
> **决策日期**：2026-06-15

---

#### P2-C4　ReAct 循环控制（终止/最大轮次/中断/死循环兜底）

| 项 | 内容 |
|----|------|
| 状态 | ✅ 已决 |
| 优先级 | 🟡 实现期细化 |
| 前置依赖 | A1 |

**关键问题**：
1. **终止条件**：模型主动停止（不再请求工具）、达到最大轮次、达到 token 上限，以哪个为准？最大轮次默认值与配置项。
2. **死循环兜底**：模型反复调用同一工具/无限请求工具时的检测与中断策略。
3. **用户中断**（SSE 断连/点停止）：进行中的工具调用如何处理？已完成的工具调用结果是否落库？已产出的部分 LLM 文本是否落库（类比 P1 §5.3 的 `finishReason=CANCELLED`）？
4. 单轮超时（一次工具调用超时）与整轮超时（整个 ReAct 循环 deadline）的分层。

**关联文档**：`docs-prd/phase-P1/02` §5.3（中断与取消）、`glm-docs/07`（超时）。

**决策结果**：✅ 已决（2026-06-15）
> **决定 1 — 终止条件**：①模型 `finishReason=STOP` 无 tool call → 正常结束；②**最大轮次**（默认 **10**，可配）→ 落库已产出内容（`finishReason=MAX_TURNS`），视作正常截断不报错；③token 累计接近窗口 → A2 的 turn 级摘要压缩处理，仍超则中断。
>
> **决定 2 — 死循环兜底**：主兜底 = 最大轮次（达到即停）；增强 = 检测同一 `(toolName, args)` 连续重复 **3 次** → 先回灌「检测到重复调用，请换方法」给模型一轮（给纠正机会），仍重复则中断。
>
> **决定 3 — 用户中断（SSE 断连/停止）**：取消链 SSE 断连 → chat `Future.cancel` → engine 中断循环 + **取消进行中工具调用**（B1 工具执行支持中断/RestClient 取消）→ model 取消当前 LLM stream。部分落库（对齐 P1 §5.3 + A2）：已产出文本 + 已完成工具结果 → 落库（`finishReason=CANCELLED`）；进行中工具取消、不落库。
>
> **决定 4 — 超时分层**：单次 LLM stream 120s（07 §4.2/model 层）；单工具调用 30s 可配（B1）；**整轮 ReAct 循环 deadline 120s**（从用户发消息起，对齐 SseEmitter，C4 新增）。循环每轮检查剩余时间，不足 → 中断（落库 `TIMEOUT`）。
>
> **配置项**：`zify.chat.react.{max-turns:10, loop-deadline:120s, dup-tool-call-threshold:3}`。
>
> **连带影响**：
> 1. engine 循环控制逻辑（`EngineService` 扩展）；`ChatTurnResult.finishReason` 增 `MAX_TURNS`/`TIMEOUT`（C5 的 SSE 也带 finishReason）。
> 2. 中断部分落库（`ChatStreamService` 扩展，对齐 P1 §5.3）。
> 3. 取消进行中工具衔接 C6（线程模型）。
>
> **决策日期**：2026-06-15

---

#### P2-C5　多轮 + SSE 事件协议扩展

| 项 | 内容 |
|----|------|
| 状态 | ✅ 已决 |
| 优先级 | 🟠 前置必要 |
| 前置依赖 | A1, A2 |

**背景**：P1 SSE 协议只有 `message_delta` / `done` / `run_error`（`docs-prd/phase-P1/02` §5.2，且明确「前端 `ChatStreamEvent` 预留 `tool_call` 分支但 P1 不会收到」）。P2 多轮 ReAct 需要在 token 流中穿插工具调用事件。

**关键问题**：
1. 新增事件类型设计：`tool_call_start`（工具名/入参）、`tool_call_end`（输出/耗时/状态）、是否需要区分「轮次」边界（`turn_start`/`turn_end`）、错误事件细化。
2. 事件载荷字段（toolCallId / toolName / args / result / durationMs / status），与 `tool_call_log` 主键如何关联（前端可下钻日志）。
3. 多轮中**每轮 LLM 的 token 流**与**工具事件**的交错顺序在前端的渲染规则。
4. 是否沿用 P1 的「提交消息(POST) + 建立流(GET)」两步协议，还是工具调用需新端点。

**影响面**：前端 `useChatStream` / `chatStore`、SSE Controller、与 A1 机制的输出对接。

**关联文档**：`docs-prd/phase-P1/02` §5.2 §6.7、`glm-docs/06`（前端 SSE 放置）。

**决策结果**：✅ 已决（2026-06-14）
> **事件协议扩展**（P1 三类 `message_delta`/`done`/`run_error` 既有，新增两类）：
>
> | 事件 | 时机 | data 载荷 |
> |------|------|----------|
> | `message_delta`（既有） | 每轮 LLM token 流 | `{ conversationId, assistantMessageId, delta }` |
> | **`tool_call_start`**（新增） | 模型决定调工具、engine 即将执行 | `{ conversationId, assistantMessageId, toolCallId, toolName, args(JSON) }` |
> | **`tool_call_end`**（新增） | 工具执行完（成功/失败/熔断） | `{ conversationId, assistantMessageId, toolCallId, toolName, status(SUCCESS/ERROR), output, durationMs, toolCallLogId }` |
> | `done`（既有） | 整轮 ReAct 结束 | `{ conversationId, assistantMessageId }`（=最终 ASSISTANT id） |
> | `run_error`（既有） | 致命错误 | 不变 |
>
> **不需要 `turn_start`/`turn_end`**：靠 `assistantMessageId` 分段。多轮 ReAct 里每轮 LLM 输出对应**独立** assistantMessageId（A2：每条 ASSISTANT message 独立 id），前端按 id 分段。
>
> **载荷关键字段**：
> - `assistantMessageId`：触发该工具调用的 ASSISTANT 消息 id（关联文本段与工具卡片）。
> - `toolCallId`：模型生成的本次调用 id（请求↔响应配对）。
> - `toolCallLogId`：**`tool_call_log` 主键**（A2 决策 5）——前端点击工具卡片下钻完整日志。
> - `output`：给模型的文本（前端精简展示，完整结果折叠/下钻 `toolCallLogId`）；`status` 对齐 B2/B1 的 `ToolExecutionResultDTO.status`。
>
> **沿用 P1 两步协议（无新端点）**：工具事件在同一条 SSE 流推送，仍 `GET /api/chat/stream?messageId=...`（EventSource GET-only 不变）。`openChatStream` 的 handlers 增 `onToolCallStart`/`onToolCallEnd`。
>
> **前端渲染规则**：
> - 文本段（`message_delta`）按 `assistantMessageId` 分组追加；多轮有多个文本段。
> - 工具卡片（`tool_call_start`→`tool_call_end`）内联在所属文本段下方，**默认折叠**，完成可展开看 output、点 `toolCallLogId` 下钻。
> - 实时性：`tool_call_start` 立即显示「调用中」，`tool_call_end` 更新结果/耗时。
>
> **历史回放 = 实时流同一套渲染**（衔接 A2）：从 message 表（`role`+`metadata`）重建工具调用视图——`ASSISTANT`+metadata.toolCalls 非空→文本段+工具卡片；`TOOL`+metadata.toolCallId/toolCallLogId→工具结果（并入对应卡片）；`ASSISTANT` 无 toolCalls→文本段（中间推理/最终回复）。SSE 事件是「增量」、历史 message 是「全量」，映射到同一 `MessageView`。
>
> **前端类型/Store 扩展（落 P2 spec）**：`ChatStreamEvent` 增 `tool_call_start`/`tool_call_end` 分支；`MessageView` 增 `toolCalls?: ToolCallView[]`（toolCallId/toolName/args/status/output/durationMs/toolCallLogId）；`chatStore` 增 `appendToolCall`/`updateToolCall`；`useChatStream` handlers 增 `onToolCallStart`/`onToolCallEnd`。
>
> **连带影响**：
> 1. **sink 回调需扩展**：P1 的 `TextStreamSink`（仅 `onDelta`）需升级为支持工具事件的接口（`onDelta`/`onToolCallStart`/`onToolCallEnd`）——engine 在循环中经该回调推事件，chat 转 SSE。具体签名落 P2 spec。
> 2. `docs-prd/phase-P1/02` §5.2 SSE 协议表 + `types/chat.ts` `ChatStreamEvent`：落实本扩展。
> 3. `glm-docs/06` §11.7：前端 SSE 处理（useChatStream）扩展工具事件分支。
>
> **决策日期**：2026-06-14

---

#### P2-C6　并行工具调用 + 线程模型

| 项 | 内容 |
|----|------|
| 状态 | ✅ 已决 |
| 优先级 | 🟡 实现期细化 |
| 前置依赖 | A1, B1 |

**关键问题**：
1. 模型一次决策请求**多个工具**（parallel tool calls）时，并行执行还是串行？各自独立超时还是共享整轮 deadline？
2. 工具执行用哪个**线程池**（`ThreadPoolExecutor`，禁 `Executors`，`CLAUDE.md` §3）？工具调用是阻塞式 HTTP，须避免阻塞 Spring AI 的 **reactive 流**（`07` §3.1）。
3. 与 P1 虚拟线程执行器（`llmTaskExecutor`）的关系：工具执行是否复用虚拟线程、还是独立的有界线程池（便于隔离/限流）。
4. 并行结果回灌顺序与 SSE 事件时序。

**关联文档**：`glm-docs/07` §3（线程管理）、`CLAUDE.md` §3（线程池）、`docs-prd/phase-P1/02` §十一（llmTaskExecutor）。

**决策结果**：✅ 已决（2026-06-15）
> **决定 1 — 并行工具调用**：模型一次返回多 tool call → **并行执行**（独立 IO 提效）；各工具独立超时（B1）+ 熔断（B1）+ 共享循环 deadline（C4）；结果按 `toolCallId` 配对回灌。
>
> **决定 2 — 工具执行器**：**独立于 `llmTaskExecutor`**（隔离工具 IO 与 LLM）；`newVirtualThreadPerTaskExecutor()`（Spring Bean，对齐 07 §3.2）+ 全局 `Semaphore`（`max-concurrent` 50）。per-tool 限流留二期。（CLAUDE.md §3「禁 `Executors.newXxx()`」针对 `newFixed`/`newCached` 无界队列 OOM，虚拟线程执行器是 07 §3.2 已定例外。）
>
> **决定 3 — 同步阻塞模型**：engine 循环在虚拟线程上同步调 model + tool（阻塞 IO 不占 OS 线程，简单高效）；不用异步编排（`CompletableFuture` 仅并行多工具）；reactive 流由 `CountDownLatch` 桥接到同步（07 §3.1）。
>
> **决定 4 — 并行实现**：多 tool call → 各 submit 工具执行器 + acquire Semaphore → `CompletableFuture.allOf` 等全部；各独立 try/catch（互不影响、各自 status）；各 own timeout + 循环 deadline 约束。
>
> **决定 5 — SSE 事件时序（C5）**：`tool_call_start(A/B)` 提交时发 → `tool_call_end` 各完成时发（乱序，前端按 toolCallId 配对）→ 全部完成 → 下一轮 `message_delta`。
>
> **配置**：`zify.tool.executor.max-concurrent: 50`。
>
> **连带影响**：tool 模块 `ToolExecutor` Bean（虚拟线程+Semaphore）；engine 循环并行执行；取消（C4）`Future.cancel` 取消进行中并行工具。
>
> **决策日期**：2026-06-15

---

#### P2-C7　工具安全（SSRF 防护、大小限制）

| 项 | 内容 |
|----|------|
| 状态 | ✅ 已决 |
| 优先级 | 🟡 实现期细化 |
| 前置依赖 | — |

**关键问题**：
1. HTTP 工具可配任意 URL，存在 **SSRF 风险**：是否过滤内网/保留地址（127.0.0.0/8、10/8、172.16/12、192.168/16、169.254/16、metadata 服务 169.254.169.254）？白名单/黑名单策略？DNS rebinding 防护？
2. MCP Server URL 同样的 SSRF 校验。
3. **请求体 / 响应体大小限制**（防止工具返回超大 JSON 打爆上下文/数据库）；响应截断策略。
4. Header 注入风险（用户配的 Header 是否允许任意键值、是否屏蔽敏感回显）。

**关联文档**：`CLAUDE.md` §7（部署安全）、`glm-docs/08`。

**决策结果**：✅ 已决（2026-06-15）
> **决定 1 — SSRF 防护**（HTTP 工具 URL + MCP Server URL）：**黑名单模式**（默认开，可配关闭）。禁止解析到内网/保留地址——IPv4：`127.0.0.0/8`、`10.0.0.0/8`、`172.16.0.0/12`、`192.168.0.0/16`、`169.254.0.0/16`（含云元数据 `169.254.169.254`）、`0.0.0.0/8`、`100.64.0.0/10`；IPv6：`::1`、`fc00::/7`、`fe80::/10`。**DNS 检查**：解析→校验所有 IP→连接用已解析 IP（一期基础防护；完整 DNS-rebinding 防护自定义 DNS resolver 留二期）。校验时机：保存时（即时反馈）+ 运行时（防 IP 变更）。
>
> **决定 2 — MCP Server URL**：C2 连接时走同样 SSRF 黑名单。
>
> **决定 3 — 大小限制 + 截断**：**响应**默认上限 **32KB**（可配），超则截断+标记 `truncated`，回灌模型/存 message/tool_call_log 用截断后内容（衔接 A2/D1）；**请求体**默认上限 **1MB**。
>
> **决定 4 — Header 防护**：允许任意键值，但敏感 Header（`Authorization`/`Cookie`/`Set-Cookie`）**不明文记入** tool_call_log/output（脱敏）；Header 值大小限制。
>
> **配置**：`zify.tool.security.{ssrf.enabled:true, ssrf.allow-private:false, response-max-bytes:32768, request-max-bytes:1048576}`。
>
> **连带影响**：tool 模块 `infrastructure/` 加 SSRF 校验+大小限制（HTTP 工具+MCP 都过）；tool_call_log 存截断后内容（D1）。
>
> **决策日期**：2026-06-15

---

### D 数据/运维级（建表前对齐 `glm-docs/10`、`11`）

---

#### P2-D1　`tool_call_log`(19) 大表字段与存储策略

| 项 | 内容 |
|----|------|
| 状态 | ✅ 已决 |
| 优先级 | 🟡 实现期细化 |
| 前置依赖 | A2 |

**关键问题**：
1. 字段设计：`id`/`tool_id`/`tool_name`/`source_type`(MCP/HTTP/WfT)/`agent_id`/`conversation_id`/`turn`/`input`/`output`/`status`/`duration_ms`/`error`/`created_at`/`is_deleted`（对齐 `glm-docs/10`、`11` 编号 19）。
2. 输入/输出可能很大（工具返回大 JSON）：用 JSON 列 + **截断阈值**（如超 8KB 截断存摘要），避免单行过大。
3. 索引：按 `conversation_id`+`created_at`、`agent_id`+`created_at` 建索引；关联 ID 必须建索引（§5 硬约束）。
4. 大表运维：是否分区、P5 归档要预留哪些扩展点；列表查询禁止 `SELECT *`。

**关联文档**：`glm-docs/10`、`glm-docs/11`（编号 19）、`glm-docs/09`（大表性能）、`docs-prd/phase-P1/01-data-model.md`（建表风格）。

**决策结果**：✅ 已决（2026-06-15）
> **字段**（对齐 glm-docs/10/11 编号 19 + 各议题决策汇总）：`id`/`created_at`/`updated_at`/`is_deleted`/`tool_id`/`tool_name`(快照)/`source_type`(MCP/HTTP/WFT)/`mcp_server_id`(NULL,C1)/`agent_id`(NULL)/`conversation_id`(NULL)/`workflow_run_id`(NULL,P4)/`workflow_node_run_id`(NULL,P4)/`turn`(NULL)/`tool_call_id`(关联 TOOL message,A2决策5)/`input`(JSON,截断后)/`output`(LONGTEXT,截断后)/`status`(SUCCESS/ERROR/TIMEOUT/CIRCUIT_OPEN/CANCELLED)/`duration_ms`/`error`(TEXT NULL)。不设 created_by/updated_by（大表规则）。
>
> **存储策略**（衔接 C7）：input/output 存**截断后**内容（C7 响应 32KB 阈值）；input 用 JSON 列，output 用 LONGTEXT。
>
> **索引**（关联 ID 必须建，§5）：`idx_tcl_conv_created`(conversation_id,is_deleted,created_at DESC,id DESC)、`idx_tcl_agent_created`(agent_id,created_at)、`idx_tcl_tool_created`(tool_id,created_at)、`idx_tcl_created_at`(created_at)。
>
> **大表运维**：一期**不分区**（50 人量不大）；P5 归档按 created_at 分批物理删除（保留 N 天），预留 idx_created_at；列表查询禁止 SELECT *。
>
> **连带**：迁移脚本 `V6__tool__create_tool_call_log.sql`（tool 模块，P2）；glm-docs/11 tool_call_log 归属已改 tool（A2），关系 message(TOOL) 0..1:1 tool_call_log（经 tool_call_id）。
>
> **决策日期**：2026-06-15

---

#### P2-D2　工具生命周期与运行时校验（禁用/断连/命名冲突）

| 项 | 内容 |
|----|------|
| 状态 | ✅ 已决 |
| 优先级 | 🟡 实现期细化 |
| 前置依赖 | B2 |

**关键问题**：
1. 工具被**禁用/删除/编辑**后，已绑定该工具的 Agent 运行时如何处理（跳过？报错？降级提示）？`agent_tool` 关联表是否随工具禁用自动失效。
2. MCP Server 断连导致其下工具**临时不可用**时，ReAct 循环中的降级（该工具不进入本轮 LLM 可选集？调用时报错回灌？）。
3. **命名冲突**：多个 MCP Server 或 HTTP 工具注册同名工具时如何去重/命名空间化——下发给 LLM 的工具名必须唯一。
4. Agent 绑定工具时的可用性校验时机（绑定时校验存在性 vs 运行时再校验，对齐 P1 模型校验 A-07 风格）。

**关联文档**：`glm-docs/02` §3、`docs-prd/phase-P1/02` §10.1（A-07 运行时校验风格）。

**决策结果**：✅ 已决（2026-06-15）
> **决定 1 — 工具禁用/删除/编辑运行时处理**：工具禁用（`tool.enabled=0`）或软删（`is_deleted=1`）→ `listBoundTools`（B2）**不返回**（本轮 LLM 看不到）；编辑改配置 → 下次 `listBoundTools` 返回新配置。`agent_tool` 关联**不随禁用自动删**（保留绑定），仅 `listBoundTools` 过滤。
>
> **决定 2 — MCP Server 断连降级**（衔接 C2）：`mcp_server.status != ONLINE` 的工具，`listBoundTools` 过滤掉；若 Agent **所有**工具不可用 → 模型无工具能力，正常回答。
>
> **决定 3 — 命名冲突去重**（下发 LLM 的工具名必须唯一）：HTTP 工具用户起名，创建/改名校验 `tool.name` 未删除唯一；MCP 工具不同 server 可能同名，注册时去重——冲突则加前缀 `mcpServerName__toolName`（Zify 自实现，因 C1 关闭 spring-ai-mcp prefix generator）。`tool.name` 即 LLM-visible name，全局唯一。
>
> **决定 4 — 绑定校验时机**（对齐 P1 A-07）：保存时校验工具存在+enabled（即时反馈）+运行时 `listBoundTools` 再校验可用性。
>
> **连带**：`tool` 表 `name` 唯一约束（generated column）+ `enabled` 字段；`listBoundTools` 过滤逻辑 `tool.enabled=1 AND tool.is_deleted=0 AND (source_type=HTTP OR mcp_server.status=ONLINE)`；Agent 表单工具绑定校验。
>
> **决策日期**：2026-06-15

---

## 六、修订记录

| 日期 | 议题 | 变更 | 操作人 |
|------|------|------|--------|
| 2026-06-14 | P2-A1 | 采纳方案③（Spring AI User-Controlled streaming，复用 `ChatModel.stream()` + `ToolCallingManager` 手动驱动循环）；状态 ⬜→✅。连带影响已分派给 A2/B2/C5/C4 | Claude |
| 2026-06-14 | P2-B2 | 定中立化边界（tool 中立 / engine 循环用中立 DTO / model 封装 spring-ai）+ ToolFacade 两方法 + DTO；状态 ⬜→✅。同步回填 A1 精度修正（spring-ai 类型封装收敛到 model，engine 不用 ToolCallingManager，Tool 不实现 ToolCallback） | Claude |
| 2026-06-14 | P2-A2 | 定 5 点决策：①tool_call_log 归 tool 模块（修订 glm-docs/11 engine→tool）②工具过程进 message 表 ③engine 保持纯编排（P1 §2.1 不改）④摘要压缩以 turn 为单位 ⑤tool_call_log↔TOOL message 经 toolCallLogId 关联；状态 ⬜→✅。化解「engine 写 tool_call_log」与 P1 §2.1 的张力 | Claude |
| 2026-06-14 | P2-B1 | 定工具调用规范：超时分层+可配 / 重试幂等性驱动（非幂等请求发出后不重试）/ 熔断 per-tool_id / 失败回灌不中断循环 / 异常对 engine 只暴露 DTO；规范落点新建 glm-docs/13；状态 ⬜→✅ | Claude |
| 2026-06-14 | P2-C5 | 定 SSE 协议扩展：新增 tool_call_start/tool_call_end 两事件（带 toolCallLogId 下钻）、不要 turn 事件（靠 assistantMessageId 分段）、沿用两步协议、工具卡片默认折叠、历史回放与实时流同一套渲染；连带 sink 回调需扩展（onDelta→+onToolCall*）；状态 ⬜→✅ | Claude |
| 2026-06-14 | P2-C1 | 定 MCP：传输只做 Streamable-HTTP+SSE 不做 stdio；用 spring-ai-mcp-client（SYNC）+ 关闭 ToolCallback auto-config + 自适配 McpClient→中立 Tool；工具发现每 remote tool 一条 tool 记录；状态 ⬜→✅。同步回填 B2 中立修正（pom 零 spring-ai → 接口中立，infra 层可用 spring-ai-mcp） | Claude |
| 2026-06-15 | P2-C2 | 定 MCP 连接生命周期：常驻保活 / 连接时一次性发现+toolsChange 增量 / 断连重连+mcp_server 状态标记（ONLINE/OFFLINE/ERROR）降级 / 单连接复用；状态 ⬜→✅ | Claude |
| 2026-06-15 | P2-C3 | 定 HTTP 工具：两种定义方式底层同构 / OpenAPI 3.0-3.1 一个 operation→一个 tool（Swagger Parser 新依赖已确认）/ 鉴权加密复用 SecretEncryptor / 参数按 in 字段映射 path/query/header/body；状态 ⬜→✅ | Claude |
| 2026-06-15 | P2-C4 | 定 ReAct 循环控制：终止（STOP/MAX_TURNS 默认10/token）/ 死循环兜底（重复3次回灌再中断）/ 用户中断取消链+部分落库 CANCELLED / 超时分层（单LLM 120s+单工具30s+循环deadline 120s）；状态 ⬜→✅ | Claude |
| 2026-06-15 | P2-C6 | 定并行工具+线程模型：多 tool call 并行执行 / 独立 ToolExecutor（虚拟线程+Semaphore 50，与 llmTaskExecutor 隔离）/ 同步阻塞模型（虚拟线程优势）/ CompletableFuture.allOf / SSE 乱序按 toolCallId 配对；状态 ⬜→✅ | Claude |
| 2026-06-15 | P2-C7 | 定工具安全：SSRF 黑名单（默认开，禁内网/保留地址+DNS 检查）+ 响应 32KB 截断 + 请求 1MB 上限 + 敏感 Header 脱敏；状态 ⬜→✅ | Claude |
| 2026-06-15 | P2-D1 | 定 tool_call_log(19) 字段（17 列覆盖各议题决策）+ 截断存储（C7 32KB）+ 4 索引 + 不分区；状态 ⬜→✅ | Claude |
| 2026-06-15 | P2-D2 | 定工具生命周期：禁用/断连经 listBoundTools 过滤降级 / 命名冲突去重（HTTP 校验唯一 + MCP 加 server 前缀）/ 双重校验（保存时+运行时）；状态 ⬜→✅ | Claude |
| 2026-06-15 | 🎉 **P2 全部 13 题决策完成** | A1/B2/A2/B1/C5/C1-C4/C6/C7/D1/D2 全部 ✅；可进入 P2 正式文档（`01-data-model.md` + `02-functional-spec.md`）撰写 + `glm-docs/*` 回写 | Claude |

> 议题全部 ✅ 后：据此清单撰写 `docs-prd/phase-P2/01-data-model.md` 与 `02-functional-spec.md`，并按需回写 `glm-docs/*`。
