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
| **P2-A2** | 工具调用结果持久化与上下文重建（含 tool_call_log 归属） | 架构级 | 🟠 | ⬜ | 3 | A1 |
| **P2-B1** | HTTP/MCP 工具调用的超时/重试/容错规范 | 规范级 | 🟠 | ⬜ | 4 | — |
| **P2-B2** | 统一 Tool 接口输入输出契约 + ToolFacade DTO + Schema 下发 | 规范级 | 🟠 | ✅ | 2 | A1 |
| **P2-C1** | MCP Client 传输方式范围 + 是否采用 spring-ai-mcp | 实现级 | 🟡 | ⬜ | 6 | B1, B2 |
| **P2-C2** | MCP 连接生命周期（常驻/按需、发现时机、断连重连） | 实现级 | 🟡 | ⬜ | 7 | C1 |
| **P2-C3** | HTTP 工具 OpenAPI 解析范围 + 鉴权凭据加密 | 实现级 | 🟡 | ⬜ | 8 | B1, B2 |
| **P2-C4** | ReAct 循环控制（终止/最大轮次/中断/死循环兜底） | 实现级 | 🟡 | ⬜ | 9 | A1 |
| **P2-C5** | 多轮 + SSE 事件协议扩展（在 P1 协议上加工具事件） | 实现级 | 🟠 | ⬜ | 5 | A1, A2 |
| **P2-C6** | 并行工具调用 + 线程模型（不阻塞 reactive 流） | 实现级 | 🟡 | ⬜ | 10 | A1, B1 |
| **P2-C7** | 工具安全（SSRF 防护、请求/响应大小限制） | 实现级 | 🟡 | ⬜ | 11 | — |
| **P2-D1** | `tool_call_log`(19) 大表字段与存储策略 | 数据级 | 🟡 | ⬜ | 12 | A2 |
| **P2-D2** | 工具生命周期与运行时校验（禁用/断连/命名冲突） | 数据级 | 🟡 | ⬜ | 13 | B2 |

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
| 状态 | ⬜ 待讨论 |
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

**决策结果**：⬜ 待回填
> - 决定（含 tool_call_log 归属）：
> - message 表是否扩展：
> - 对 P1 §2.1 边界的处理：
> - 理由：
> - 需回写的正式文档：
> - 决策日期：

---

### B 规范级（文档留白，P2 硬前置）

---

#### P2-B1　HTTP/MCP 工具调用的超时/重试/容错规范

| 项 | 内容 |
|----|------|
| 状态 | ⬜ 待讨论 |
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

**决策结果**：⬜ 待回填
> - 决定（超时/重试/熔断取值与策略）：
> - 工具失败在 ReAct 循环中的处理：
> - 规范落点（新文档 / 扩写 07）：
> - 决策日期：

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

---

### C 实现级（范围与策略，动手前明确）

---

#### P2-C1　MCP Client 传输方式范围 + 是否采用 spring-ai-mcp

| 项 | 内容 |
|----|------|
| 状态 | ⬜ 待讨论 |
| 优先级 | 🟡 实现期细化 |
| 前置依赖 | B1, B2 |

**关键问题**：
1. MCP 标准传输有 stdio / SSE / Streamable HTTP 三种。一期是否**只做 SSE + Streamable HTTP、不做 stdio**（Zify 是 Web 服务，stdio 多用于本地）？
2. 是否采用 `spring-ai-mcp-client`（Spring AI 2.0 生态）承接连接/发现/调用，还是自实现 MCP 协议（initialize / tools.list / tools.call）？
3. 工具发现的粒度：一个 MCP Server 下的多个 tool 如何注册进统一 Tool 体系（每个 remote tool → 一条 `tool` 记录？来源标记 `mcp_server_id`）。

**关联文档**：`glm-docs/02` §3、`glm-docs/12` §五、`glm-docs/07` §3.1（客户端选型一致性）。

**决策结果**：⬜ 待回填

---

#### P2-C2　MCP 连接生命周期（常驻/按需、发现时机、断连重连）

| 项 | 内容 |
|----|------|
| 状态 | ⬜ 待讨论 |
| 优先级 | 🟡 实现期细化 |
| 前置依赖 | C1 |

**关键问题**：
1. MCP Server 连接是**常驻保活复用**（启动/首次使用时建连、保活、复用）还是**按需建立**（每次调用建连用完即关）？常驻涉及连接池/心跳/超时回收。
2. 工具发现时机：连接时一次性发现并缓存入 `tool` 表，还是每次调用前动态刷新？缓存失效如何处理（Server 增删了工具）。
3. **断连/Server 重启**：检测机制、自动重连策略、重连失败时该 Server 下工具的降级（标记不可用？）。
4. 连接与并发：同一 Server 的多个并发工具调用是否复用单连接、有无排队。

**关联文档**：`glm-docs/07`（连接/重试原则）、`glm-docs/12` §五。

**决策结果**：⬜ 待回填

---

#### P2-C3　HTTP 工具 OpenAPI 解析范围 + 鉴权凭据加密

| 项 | 内容 |
|----|------|
| 状态 | ⬜ 待讨论 |
| 优先级 | 🟡 实现期细化 |
| 前置依赖 | B1, B2 |

**关键问题**：
1. 「手动配置 URL/Header/Body」与「OpenAPI Schema 解析」两种定义方式如何统一进 Tool 接口？
2. OpenAPI 解析支持哪些版本（3.0 / 3.1）？一个 spec 映射成「一个工具」还是「每个 operation → 一个工具」？
3. 鉴权：Header/Body 中的 token / API Key 属敏感信息，是否像 Provider API Key 一样**加密存储**（复用 `SecretEncryptor`）？`CLAUDE.md` §7「API Key 加密存储」。
4. 运行时参数注入：LLM 填的参数如何映射到 path/query/header/body（OpenAPI 的 `in` 字段）。

**关联文档**：`glm-docs/02` §3、`docs-prd/phase-P1/02`（SecretEncryptor 复用）、`CLAUDE.md` §7。

**决策结果**：⬜ 待回填

---

#### P2-C4　ReAct 循环控制（终止/最大轮次/中断/死循环兜底）

| 项 | 内容 |
|----|------|
| 状态 | ⬜ 待讨论 |
| 优先级 | 🟡 实现期细化 |
| 前置依赖 | A1 |

**关键问题**：
1. **终止条件**：模型主动停止（不再请求工具）、达到最大轮次、达到 token 上限，以哪个为准？最大轮次默认值与配置项。
2. **死循环兜底**：模型反复调用同一工具/无限请求工具时的检测与中断策略。
3. **用户中断**（SSE 断连/点停止）：进行中的工具调用如何处理？已完成的工具调用结果是否落库？已产出的部分 LLM 文本是否落库（类比 P1 §5.3 的 `finishReason=CANCELLED`）？
4. 单轮超时（一次工具调用超时）与整轮超时（整个 ReAct 循环 deadline）的分层。

**关联文档**：`docs-prd/phase-P1/02` §5.3（中断与取消）、`glm-docs/07`（超时）。

**决策结果**：⬜ 待回填

---

#### P2-C5　多轮 + SSE 事件协议扩展

| 项 | 内容 |
|----|------|
| 状态 | ⬜ 待讨论 |
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

**决策结果**：⬜ 待回填

---

#### P2-C6　并行工具调用 + 线程模型

| 项 | 内容 |
|----|------|
| 状态 | ⬜ 待讨论 |
| 优先级 | 🟡 实现期细化 |
| 前置依赖 | A1, B1 |

**关键问题**：
1. 模型一次决策请求**多个工具**（parallel tool calls）时，并行执行还是串行？各自独立超时还是共享整轮 deadline？
2. 工具执行用哪个**线程池**（`ThreadPoolExecutor`，禁 `Executors`，`CLAUDE.md` §3）？工具调用是阻塞式 HTTP，须避免阻塞 Spring AI 的 **reactive 流**（`07` §3.1）。
3. 与 P1 虚拟线程执行器（`llmTaskExecutor`）的关系：工具执行是否复用虚拟线程、还是独立的有界线程池（便于隔离/限流）。
4. 并行结果回灌顺序与 SSE 事件时序。

**关联文档**：`glm-docs/07` §3（线程管理）、`CLAUDE.md` §3（线程池）、`docs-prd/phase-P1/02` §十一（llmTaskExecutor）。

**决策结果**：⬜ 待回填

---

#### P2-C7　工具安全（SSRF 防护、大小限制）

| 项 | 内容 |
|----|------|
| 状态 | ⬜ 待讨论 |
| 优先级 | 🟡 实现期细化 |
| 前置依赖 | — |

**关键问题**：
1. HTTP 工具可配任意 URL，存在 **SSRF 风险**：是否过滤内网/保留地址（127.0.0.0/8、10/8、172.16/12、192.168/16、169.254/16、metadata 服务 169.254.169.254）？白名单/黑名单策略？DNS rebinding 防护？
2. MCP Server URL 同样的 SSRF 校验。
3. **请求体 / 响应体大小限制**（防止工具返回超大 JSON 打爆上下文/数据库）；响应截断策略。
4. Header 注入风险（用户配的 Header 是否允许任意键值、是否屏蔽敏感回显）。

**关联文档**：`CLAUDE.md` §7（部署安全）、`glm-docs/08`。

**决策结果**：⬜ 待回填

---

### D 数据/运维级（建表前对齐 `glm-docs/10`、`11`）

---

#### P2-D1　`tool_call_log`(19) 大表字段与存储策略

| 项 | 内容 |
|----|------|
| 状态 | ⬜ 待讨论 |
| 优先级 | 🟡 实现期细化 |
| 前置依赖 | A2 |

**关键问题**：
1. 字段设计：`id`/`tool_id`/`tool_name`/`source_type`(MCP/HTTP/WfT)/`agent_id`/`conversation_id`/`turn`/`input`/`output`/`status`/`duration_ms`/`error`/`created_at`/`is_deleted`（对齐 `glm-docs/10`、`11` 编号 19）。
2. 输入/输出可能很大（工具返回大 JSON）：用 JSON 列 + **截断阈值**（如超 8KB 截断存摘要），避免单行过大。
3. 索引：按 `conversation_id`+`created_at`、`agent_id`+`created_at` 建索引；关联 ID 必须建索引（§5 硬约束）。
4. 大表运维：是否分区、P5 归档要预留哪些扩展点；列表查询禁止 `SELECT *`。

**关联文档**：`glm-docs/10`、`glm-docs/11`（编号 19）、`glm-docs/09`（大表性能）、`docs-prd/phase-P1/01-data-model.md`（建表风格）。

**决策结果**：⬜ 待回填

---

#### P2-D2　工具生命周期与运行时校验（禁用/断连/命名冲突）

| 项 | 内容 |
|----|------|
| 状态 | ⬜ 待讨论 |
| 优先级 | 🟡 实现期细化 |
| 前置依赖 | B2 |

**关键问题**：
1. 工具被**禁用/删除/编辑**后，已绑定该工具的 Agent 运行时如何处理（跳过？报错？降级提示）？`agent_tool` 关联表是否随工具禁用自动失效。
2. MCP Server 断连导致其下工具**临时不可用**时，ReAct 循环中的降级（该工具不进入本轮 LLM 可选集？调用时报错回灌？）。
3. **命名冲突**：多个 MCP Server 或 HTTP 工具注册同名工具时如何去重/命名空间化——下发给 LLM 的工具名必须唯一。
4. Agent 绑定工具时的可用性校验时机（绑定时校验存在性 vs 运行时再校验，对齐 P1 模型校验 A-07 风格）。

**关联文档**：`glm-docs/02` §3、`docs-prd/phase-P1/02` §10.1（A-07 运行时校验风格）。

**决策结果**：⬜ 待回填

---

## 六、修订记录

| 日期 | 议题 | 变更 | 操作人 |
|------|------|------|--------|
| 2026-06-14 | P2-A1 | 采纳方案③（Spring AI User-Controlled streaming，复用 `ChatModel.stream()` + `ToolCallingManager` 手动驱动循环）；状态 ⬜→✅。连带影响已分派给 A2/B2/C5/C4 | Claude |
| 2026-06-14 | P2-B2 | 定中立化边界（tool 中立 / engine 循环用中立 DTO / model 封装 spring-ai）+ ToolFacade 两方法 + DTO；状态 ⬜→✅。同步回填 A1 精度修正（spring-ai 类型封装收敛到 model，engine 不用 ToolCallingManager，Tool 不实现 ToolCallback） | Claude |

> 议题全部 ✅ 后：据此清单撰写 `docs-prd/phase-P2/01-data-model.md` 与 `02-functional-spec.md`，并按需回写 `glm-docs/*`。
