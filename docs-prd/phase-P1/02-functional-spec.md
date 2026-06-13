# P1 核心对话闭环 — 功能规格说明

> 本文档定义 P1（核心对话闭环 MVP）的全部产品功能、边界、前端交互、后端功能与接口设计。
> 数据库设计见 `01-data-model.md`。
> 依据：`glm-docs/02`（模块）、`glm-docs/03`（前端）、`glm-docs/06`（代码组织）、`glm-docs/07`（LLM 调用）、`glm-docs/12`（路线图 P1）。

---

## 一、模块定位与范围

P1 打通一期产品的核心价值闭环：用户选择一个 Agent，发送消息，看到流式回复，历史会话保留。

### 1.1 涉及模块

| 模块 | P1 做什么 |
|------|---------|
| **model**（扩展） | 新增**流式 Chat 网关**：`infrastructure/client/` 实现 OpenAI 兼容 + Anthropic 流式调用；`ModelFacade` 新增 `chatStream`。复用已有 Provider 配置与 API Key 解密 |
| **agent**（新建） | `agent` 表 + Agent CRUD + 基础配置（名称/描述/System Prompt/模型/类型）。类型只支持 REACT。`AgentFacade.getAgentConfig` 供 engine/chat 获取配置 |
| **engine**（新建） | 最小编排：组装 Prompt → 调 `ModelFacade.chatStream` → 经回调流式输出。`EngineFacade.runChatTurn` 纯编排、不碰数据库。按 `glm-docs/07` 配超时/重试/取消 |
| **chat**（新建） | `conversation` / `message` 表 + 会话与消息管理 + **持有 SSE 流式端点与消息持久化** |

### 1.2 模块依赖（pom 已声明，P1 无需改 pom）

```text
chat   → common, agent, engine
engine → common, agent, model (+ tool/knowledge/workflow，P1 不调用)
agent  → common, model (+ tool/knowledge/workflow，P1 不调用)
model  → common
```

> `agent` / `engine` 的 pom 已依赖 tool/knowledge/workflow（对齐依赖图），P1 不调用这些模块，故**无需**提前定义 `ToolFacade`/`KnowledgeFacade`/`WorkflowFacade` 的 no-op 实现——它们推迟到 P2/P3/P4 首次被消费时再建（对路线图 P1「接口先行」一条的精简）。

### 1.3 功能总览

| 功能 | P1 | 说明 |
|------|----|------|
| Agent CRUD（创建/查看/编辑/删除） | ✅ | 名称、描述、System Prompt、模型、类型 |
| Agent 启用/禁用 | ✅ | 禁用后不出现在「新建会话」选择器 |
| Agent 列表（搜索 + 类型筛选） | ✅ | — |
| 新建会话（选择 Agent） | ✅ | — |
| 会话列表（按最近活动排序） | ✅ | Keyset 分页 |
| 发送消息 + 流式回复 | ✅ | SSE，可中断 |
| 消息历史（继续对话） | ✅ | Keyset 分页加载 |
| 删除会话（级联删除消息） | ✅ | 软删 |
| ReAct 多轮工具调用循环 | ❌ P2 | P1 为「零工具」单轮 LLM 调用 |
| 知识库检索增强 | ❌ P3 | — |
| Workflow Agent | ❌ P4 | 创建表单中该项禁用 |
| System Prompt `{{variable}}` 注入 | ❌ P5 | P1 原文透传 |
| 会话重命名 / 复制 / 分组 | ❌ P5 | 标题默认取 Agent 名称 |
| 消息反馈 / 收藏 | ❌ 二期 | — |

---

## 二、关键架构决策

P1 的核心架构围绕「对话如何流式生成并持久化」展开。以下决策必须无歧义落地。

### 2.1 `chat → engine`：chat 编排，engine 执行

依赖图规定 `chat → engine`（engine 不依赖 chat）。因此：

- **chat 持有会话与消息数据**，是用户对话的编排者；
- **engine 是纯编排 Facade**：输入「Agent 配置 + 历史消息」，输出「事件流 + 最终结果」，**不读写任何数据库表**；
- 调用方向：`chat SSE Controller → EngineFacade.runChatTurn → ModelFacade.chatStream`。

### 2.2 chat 拥有 SSE 端点与消息持久化

由于 chat 持有会话数据且必须落库消息，**SSE 流式端点放在 chat 模块**（`adapter/sse/`），engine 只负责把 token 经回调推回来。这样：

- USER 消息由 chat 在提交时落库；
- ASSISTANT 消息由 chat 在生成完成后落库（一次性 INSERT，无占位行）；
- engine 全程不碰 `conversation` / `message` 表。

> **对 `glm-docs/06` §11.7 的有意偏离**：§11.7 示例的 `/api/engine/chat/stream` + `engineApi.ts` 把流式端点放在 engine。本设计因 `chat → engine` 依赖与「持久化归 chat」改为 chat 拥有该端点（路径 `/api/chat/...`，前端用 `chatApi.ts`）。**建议据此更新 `06-zify-code-organization.md` §11.7 / §11.9**。

### 2.3 流式回调：`TextStreamSink`

为避免 engine/model/chat 三方重复定义回调、又不把 Spring MVC 的 `SseEmitter` 泄漏到 domain/api 层，在 `zify-common` 定义一个单方法通用回调：

```java
// com.zify.common.web.TextStreamSink
@FunctionalInterface
public interface TextStreamSink {
    void onDelta(String delta);
}
```

流向：`chat`（将 `SseEmitter.send` 包装为 sink）→ `engine.runChatTurn(command, sink)` → `model.chatStream(command, sink)`。token 逐块回调，错误用异常上抛，结束信息用方法返回值。

### 2.4 两步流式协议（受 EventSource GET-only 约束）

原生 `EventSource` 只能 GET、不能带 body，故发送消息与建立流分两步：

1. **提交用户消息**（POST，chat）：落库 USER 消息、更新会话计数与时间，返回 `userMessageId`。
2. **建立流**（GET SSE，chat）：以 `messageId=<userMessageId>` 标识本轮，加载历史、调 engine 生成 ASSISTANT 回复并流式返回。

`messageId` 为本轮「要回复的那条用户消息」的 ID，比「会话最新一条消息」更精确，避免重复触发生成同一轮。

### 2.5 model 模块流式 Chat 网关是 P1 的硬依赖

当前 `ModelFacade` 仅有 `listAvailableModels`，**没有对话调用能力**。engine 的「调一次 LLM → 流式返回」依赖 model 模块新增 `chatStream`。因此 **P1 必须先完成 model 模块的流式 Chat 网关**（见第八章），再做 engine 编排。

---

## 三、Agent 管理

### 3.1 创建 Agent

**入口**：Agents 列表页 →「新建 Agent」→ `/agents/create` 分步表单。

**分步与配置项**：

| 步骤 | 字段 | 类型 | 必填 | 校验 | 含义 |
|------|------|------|------|------|------|
| Step 1 基础信息 | `name` | 文本 | 是 | 1–128 字符，未删除唯一 | Agent 名称，会话列表/选择器中展示 |
| | `description` | 文本 | 否 | ≤512 字符 | Agent 用途说明，卡片副标题 |
| | `agentType` | 单选 | 是 | 仅 `REACT` 可选 | `WORKFLOW` 项显示但禁用（P4 启用） |
| Step 2 人设 | `systemPrompt` | 多行文本 | 否 | ≤MEDIUMTEXT 上限 | 定义角色/行为/回答风格，按原文透传 LLM |
| Step 3 能力 | `modelId` | 下拉 | 是（REACT） | 必须是可用 LLM | 来自 `ModelFacade.listAvailableModels("LLM")`；工具/知识库/工作流绑定步骤**隐藏** |
| Step 4 确认 | — | — | — | — | 汇总确认后保存 |

**模型下拉**：只列出「可用 LLM」= `model.enabled=1 AND provider.status=ACTIVE AND 均未删除`。

**提交流程**：

```text
1. 前端校验：name 非空、agentType=REACT、modelId 已选
2. 后端校验：name 未删除唯一；modelId 可用；agentType=REACT
3. 落库 agent（status 默认 ACTIVE）
4. 返回 Agent 详情
```

**业务规则**：

- `agentType` 创建后**不可修改**（REACT 与 WORKFLOW 执行路径不同）。
- `modelId` 可后续更换；更换时校验新模型可用。
- 名称在未删除数据中唯一（generated column 唯一键）。

### 3.2 Agent 列表

**展示**：卡片列表。每张卡片：名称、描述、类型标签（REACT）、绑定模型名、状态（ACTIVE 🟢 / INACTIVE ⚫）、最近对话时间（来自该 Agent 最新 `conversation.last_message_at`）。

**操作**：编辑、启用/禁用、删除。

**筛选/搜索**：按名称模糊搜索、按类型/状态筛选。OFFSET 分页（小表）。

### 3.3 编辑 Agent

**可编辑**：`name`、`description`、`systemPrompt`、`modelId`、`status`。
**不可编辑**：`agentType`、`id`、`created_at`。

编辑表单复用创建表单组件，预填已有配置。

### 3.4 删除 Agent

```text
1. 点击删除 → 二次确认（提示该 Agent 下有 N 个会话）
2. 软删 agent（is_deleted=1）
3. 该 Agent 下的会话不级联删除（会话可继续查看历史），但不能再发新消息
```

> 选择「软删 Agent 但保留会话」而非级联：历史对话有留存价值；Agent 被删后会话变为只读。若需清理，用户单独删会话。

### 3.5 启用/禁用 Agent

- 禁用（`INACTIVE`）：不出现在「新建会话」选择器；已有会话只读。
- 启用（`ACTIVE`）：恢复。

---

## 四、会话与消息管理

### 4.1 新建会话

**入口**：对话页左栏「新建对话」→ 弹出 `AgentSelector`（只列 `ACTIVE` 的 REACT Agent）→ 选择后创建会话并进入。

**创建**：`title` 默认 = Agent 名称；`agent_id` = 所选 Agent；`last_message_at` = 创建时间；`message_count` = 0。校验 Agent 存在、类型 REACT、状态 ACTIVE。

### 4.2 会话列表（左栏）

**排序**：按 `last_message_at` 倒序（最近活动在上）。Keyset 分页（游标 `last_message_at#id`），「加载更多」。

**每条**：标题、Agent 名称、消息数（`message_count`）、最后活动时间。

**搜索**：按标题模糊搜索（可选，P1 提供）。

### 4.3 发送消息（第一步：提交）

用户在输入框输入文本 → 点发送：

```text
1. 前端乐观渲染 USER 气泡
2. POST /api/chat/conversations/{id}/messages { content }
3. 后端：校验会话 ACTIVE；落库 USER 消息；conversation.message_count+1、last_message_at=now
4. 返回 { userMessageId }
5. 前端用 userMessageId 打开 SSE 流（第二步）
```

**校验**：`content` 非空（去空白后）；会话存在且 ACTIVE；会话的 Agent 存在、ACTIVE、REACT、`model_id` 可用。

### 4.4 流式回复（第二步：建立流）

见第五章。

### 4.5 消息历史（继续对话）

进入已有会话 → 加载消息历史（Keyset，最新一页在前，「加载更多」向上翻），随后可继续发送。

**排序与分页**：按 `created_at DESC, id DESC` Keyset，每页 20，最大 100。前端展示时反转为时间正序。

### 4.6 删除会话

```text
1. 点击删除 → 二次确认
2. 软删 conversation（is_deleted=1）
3. 软删该会话下所有 message（is_deleted=1）
```

---

## 五、对话流式生成

### 5.1 端到端流程

```text
[前端]                        [chat 模块]                    [engine 模块]              [model 模块]
  │
  │ POST /conversations/{id}/messages {content} │
  ├──────────────────────────► │ 校验+落库 USER 消息
  │                             │ 更新 conversation 计数/时间
  │  { userMessageId } ◄────────┤
  │
  │ GET /chat/stream?messageId=… (SSE) │
  ├──────────────────────────► │ 校验会话/Agent/模型
  │                             │ 加载历史消息 → List<ChatMessage>
  │                             │ 生成 assistantMessageId
  │                             │ EngineFacade.runChatTurn(cmd, sink) │
  │                             ├──────────────────────────► │ AgentFacade.getAgentConfig
  │                             │                              │ 组装 system+history
  │                             │                              │ ModelFacade.chatStream(cmd, sink) │
  │                             │                              ├──────────────────────► │ 解密 Key/选 Client
  │                             │                              │                          │ 重试/超时/并发保护
  │                             │                              │                          │ 流式拉取 token
  │  event: message_delta ◄─────┤  sink.onDelta(delta) ◄───────┤ sink.onDelta ◄──────────┤
  │  event: message_delta ◄─────┤  …（逐块）                                                      │
  │                             │  ChatTurnResult ◄────────────┤ ◄──── ChatCompletionResult┤
  │                             │ 落库 ASSISTANT 消息(含 metadata)
  │  event: done ◄──────────────┤
  │
```

### 5.2 SSE 事件协议

端点 `GET /api/chat/stream?messageId={userMessageId}`，`Accept: text/event-stream`，`produces=text/event-stream`。

事件名与 `data` 负载：

| 事件名 | data 字段 | 含义 |
|--------|----------|------|
| `message_delta` | `{ conversationId, assistantMessageId, delta }` | 一段文本增量；前端追加到对应 ASSISTANT 气泡 |
| `done` | `{ conversationId, assistantMessageId }` | 本轮完成，ASSISTANT 消息已落库；前端关闭 EventSource |
| `run_error` | `{ message, retryable }` | 本轮失败；未落库 ASSISTANT 消息；前端关闭 EventSource 并提示 |

> 不发 `tool_call` 事件（P2 才有工具）。前端 `ChatStreamEvent` 类型预留 `tool_call` 分支但 P1 不会收到。

### 5.3 中断与取消

```text
前端点「停止」/ 关闭页面 → EventSource.close()
   → chat 的 SseEmitter 触发 onCompletion/onError
   → chat 取消 engine 任务（Future.cancel(true)）
   → engine 透传中断到 model.chatStream
   → model 的流式 Client 检测 Thread.isInterrupted()，停止读取上游并关闭连接
```

取消后：

- 若尚未发任何 `message_delta`：不落库 ASSISTANT 消息，不报错（视为用户主动放弃）。
- 若已发部分 `message_delta`：**落库已生成的部分文本**作为 ASSISTANT 消息（`metadata.finishReason=CANCELLED`），保证历史一致；并发送 `done` 结束流。

### 5.4 超时与重试（遵循 `glm-docs/07`）

- 由 model 模块的 Chat 网关统一实现：连接超时 10s、首 token 超时 30s、idle 超时 45s、总 deadline 120s；显式 retry wrapper（最大 3 次尝试，首 chunk 前可重试）。
- **首 chunk 前失败且可重试**：model 内部重试，对上层透明。
- **首 chunk 后失败 / 重试耗尽**：model 抛 `LlmException` → engine 透传 → chat 发 `run_error`，不落库。
- `SseEmitter` 超时（120s）只是连接兜底，不替代上游 deadline；二者任一触发都取消上游。

---

## 六、前端设计

### 6.1 路由调整

当前 `app/router.tsx`：`/` = HomePage、`/chat` = ChatPage。按 `glm-docs/03`「对话页是默认落地页」，P1 调整为：

| 路由 | 页面 | 说明 |
|------|------|------|
| `/` | `pages/chat/ChatPage.tsx` | **默认落地页 = 对话页** |
| `/agents` | `pages/agents/AgentListPage.tsx` | Agent 列表 |
| `/agents/create` | `pages/agents/AgentFormPage.tsx` | 创建 |
| `/agents/:id/edit` | `pages/agents/AgentFormPage.tsx` | 编辑 |

> 现有 `HomePage`（`/`）与 `/chat` 路由移除；`MainLayout` 导航「对话」的 key 改为 `/`。

### 6.2 对话页 `/`（ChatPage）

```text
┌──────────────┬─────────────────────────────────────┐
│ 会话列表(左栏) │ 对话区(右栏)                          │
│              │                                     │
│ [+ 新建对话]  │ Agent 名称 · 模型 · 状态              │
│ [搜索框]      │ ───────────────────────────────────  │
│ 会话卡片列表  │ 消息流（USER 右、ASSISTANT 左）        │
│ · 标题        │ · 流式追加的 ASSISTANT 气泡            │
│ · Agent 名    │ · 「加载更多」向上翻历史               │
│ · N 条 · 时间 │ ───────────────────────────────────  │
│ (按最近活动)  │ [输入框 ........................] [发送]│
│              │ [停止]（流式中显示）                   │
└──────────────┴─────────────────────────────────────┘
```

- 进入页面：加载会话列表；无活跃会话时显示空状态引导「选择 Agent 开始对话」。
- 点「新建对话」→ `AgentSelector` 弹窗 → 选 Agent → 创建会话 → 进入右栏。
- 点会话 → 加载消息历史 → 可继续对话。
- 空状态：右栏提示选择 Agent。

### 6.3 Agents 列表页 `/agents`（AgentListPage）

- 顶部：「新建 Agent」按钮 + 搜索框 + 类型/状态筛选。
- 卡片列表：名称、描述、REACT 标签、模型名、状态、最近对话时间；操作：编辑、启用/禁用、删除。

### 6.4 Agent 表单页（AgentFormPage）

`/agents/create` 与 `/agents/:id/edit` 复用同一组件，按 `id` 有无区分创建/编辑。分步表单（Ant Design `Steps`）：

- Step 1 基础信息：`name`、`description`、`agentType`（仅 REACT 可选，WORKFLOW 禁用并标注「P4 上线」）。
- Step 2 人设：`systemPrompt`（多行编辑器，可折叠/全屏）。
- Step 3 能力：`modelId`（`features/model/components/ModelSelector` 下拉）。**不显示**工具/知识库/工作流绑定步骤。
- Step 4 确认：汇总 → 保存 → 跳回列表。

### 6.5 文件清单（按 `glm-docs/06` §11 结构）

```text
src/
├── api/
│   ├── agentApi.ts          # Agent CRUD
│   └── chatApi.ts           # 会话/消息 CRUD + sendMessage + openChatStream（SSE）
├── types/
│   ├── agent.ts             # Agent 相关 HTTP 契约类型
│   └── chat.ts              # 会话/消息类型 + ChatStreamEvent
├── stores/
│   └── chatStore.ts         # currentConversationId / messages / isStreaming / eventSourceRef
├── features/
│   ├── agent/components/
│   │   ├── AgentSelector.tsx     # 新建会话时选 Agent（弹窗）
│   │   ├── AgentForm.tsx         # 分步表单主体
│   │   └── AgentTypeSelector.tsx
│   ├── model/components/
│   │   └── ModelSelector.tsx     # Agent 表单中的模型下拉
│   └── chat/hooks/
│       └── useChatStream.ts      # SSE 事件处理 + chatStore 写入
├── pages/
│   ├── chat/
│   │   ├── ChatPage.tsx
│   │   └── components/{ConversationSidebar,ChatPanel,MessageList,MessageInput}.tsx
│   └── agents/
│       ├── AgentListPage.tsx
│       ├── AgentFormPage.tsx
│       └── components/AgentCard.tsx
```

> 类型放置：与已实现的 `modelApi.ts` 一致，HTTP 契约类型可内联于对应 `*Api.ts` 并 re-export，或放 `types/{module}.ts`。P1 统一采用 `types/{module}.ts`（SSE 事件类型 `ChatStreamEvent` 放 `types/chat.ts`）。

### 6.6 状态管理（Zustand）

`stores/chatStore.ts`（跨组件共享）：

| 状态 | 含义 |
|------|------|
| `currentConversationId` | 当前打开的会话 |
| `messages` | 当前会话的消息流（含流式中的临时 ASSISTANT 气泡） |
| `isStreaming` | 是否正在生成 |
| `eventSourceRef` | 当前 EventSource 引用（用于中断） |

不进 Store：表单草稿（Agent 表单）、会话列表游标/搜索、单页 loading——用组件本地 `useState` / 页面 Hook。

`useChatStream.ts`：封装 `sendMessage` + `openChatStream`，处理 `message_delta`/`done`/`run_error`，写入 `chatStore`；提供 `stop()`（关闭 EventSource）。

### 6.7 SSE 调用（chatApi.ts）

```typescript
// 两步：先提交用户消息，再用 userMessageId 开流
sendMessage(conversationId, content)      // POST → { userMessageId }
openChatStream(messageId, handlers)       // new EventSource('/api/chat/stream?messageId=…')
```

`EventSource` 监听 `message_delta` / `done` / `run_error`；`done`/`run_error`/`onerror` 均 `close()`。中断按钮先 `es.close()`。

---

## 七、后端 API 设计（HTTP）

统一响应 `Result<T> = { code, message, data }`（`com.zify.common.web.Result`）。错误经 `BusinessException` + `ErrorCode`，由全局异常处理器转 `{ code, message }`。SSE 端点返回 `SseEmitter`（非 `Result`）。

### 7.1 Agent 接口（`/api/agents`，agent 模块 `adapter/web/AgentController`）

#### 创建 Agent

`POST /api/agents`

```jsonc
// 请求
{ "name": "客服助手", "description": "处理客户咨询", "agentType": "REACT",
  "systemPrompt": "你是一名友好的客服…", "modelId": "uuid" }
// 响应 data：AgentDetailResponse（见下）
```

规则：`agentType` 必须为 `REACT`；`name` 未删除唯一；`modelId` 必须是可用 LLM。

#### 查询列表

`GET /api/agents?page=1&pageSize=20&name=&agentType=&status=`

```jsonc
// 响应 data：OffsetPageResponse<AgentSummaryResponse>
{ "records": [{ "id","name","description","agentType","status",
                "modelName","lastConversationAt","createdAt" }],
  "total": 12, "page": 1, "pageSize": 20 }
```

规则：`name` 模糊搜索；`lastConversationAt` 取该 Agent 最新会话的 `last_message_at`（可空）。小表 OFFSET 分页。

#### 查询详情

`GET /api/agents/{id}` → `AgentDetailResponse`（含 `systemPrompt`、`modelId` 等全部字段）。

#### 更新

`PUT /api/agents/{id}` — 可改 `name`/`description`/`systemPrompt`/`modelId`/`status`；`agentType` 不可改。

#### 删除

`DELETE /api/agents/{id}` — 软删；不级联删会话。

#### 启用/禁用

`PUT /api/agents/{id}/status` body `{ "status": "INACTIVE" }`。

### 7.2 会话接口（`/api/chat/conversations`，chat 模块 `adapter/web/ConversationController`）

#### 新建会话

`POST /api/chat/conversations` body `{ "agentId": "uuid" }`
→ `ConversationResponse { id, title, agentId, agentName, status, messageCount, lastMessageAt, createdAt }`
规则：校验 Agent 存在、`REACT`、`ACTIVE`；`title` 取 Agent 名称。

#### 会话列表（Keyset）

`GET /api/chat/conversations?cursor=&limit=20&agentId=&title=`
→ `CursorPageResponse<ConversationSummaryResponse>`（`records`/`nextCursor`/`hasMore`）
规则：按 `last_message_at DESC, id DESC`；`agentId`/`title` 可选过滤；游标 opaque。

#### 会话详情

`GET /api/chat/conversations/{id}` → `ConversationResponse`。

#### 删除会话

`DELETE /api/chat/conversations/{id}` — 软删会话 + 其下全部消息。

### 7.3 消息接口（chat 模块 `adapter/web/MessageController`）

#### 发送用户消息（第一步）

`POST /api/chat/conversations/{id}/messages` body `{ "content": "你好" }`
→ `{ "userMessageId": "uuid", "createdAt": "..." }`
规则：`content` 去空白后非空；会话 ACTIVE；Agent 可用；落库 USER 消息并更新会话计数/时间。**不在此调用 LLM**（事务短）。

#### 消息历史（Keyset）

`GET /api/chat/conversations/{id}/messages?cursor=&limit=20`
→ `CursorPageResponse<MessageResponse>`
`MessageResponse { id, role, content, metadata, createdAt }`
规则：按 `created_at DESC, id DESC`；含 `content` 与 `metadata`（消息流渲染必需）；游标 opaque。

### 7.4 流式回复（第二步，chat 模块 `adapter/sse/ChatStreamSseController`）

`GET /api/chat/stream?messageId={userMessageId}` — `produces=text/event-stream` → `SseEmitter`

流程（见第五章）：校验 → 加载历史 → `engineFacade.runChatTurn` → 落库 ASSISTANT → 发 `done`；失败发 `run_error`。

---

## 八、model 模块增量：流式 Chat 网关

P1 在 model 模块新增 LLM 流式对话能力（按 `glm-docs/07`，本文只定契约与落点，超时/重试/熔断细节遵循 07）。

### 8.1 新增 Facade 方法

```java
// com.zify.model.api.ModelFacade（在已有 listAvailableModels 之外新增）
ChatCompletionResult chatStream(ChatCompletionCommand command, TextStreamSink sink);
```

- **职责**：按 `modelId` 解析模型与供应商、解密 API Key、按 `provider_type` 选 Client、施加并发许可/超时/重试，流式拉取 token 经 `sink.onDelta` 回调，返回最终结果。
- **边界**：API Key 仅在本模块 `infrastructure/client/` 内解密使用，不返回、不记录、不入异常。

### 8.2 新增 api/dto（`com.zify.model.api.dto.chat`）

| DTO | 字段 |
|-----|------|
| `ChatCompletionCommand` | `modelId`, `messages: List<ChatMessage>`, `options: ChatOptions` |
| `ChatMessage` | `role`(USER/ASSISTANT/SYSTEM), `content` |
| `ChatOptions` | `temperature`, `maxTokens`, `topP`（均可空，空则用 `model.default_params`） |
| `ChatCompletionResult` | `content`(全文), `finishReason`, `usage: TokenUsage` |
| `TokenUsage` | `promptTokens`, `completionTokens`, `totalTokens` |

### 8.3 新增 infrastructure/client

```text
infrastructure/client/
├── LlmChatClient.java                  # 接口：streamChat(ctx, sink)
├── OpenAiCompatibleChatClient.java     # OPENAI / OPENAI_COMPATIBLE：POST /v1/chat/completions (stream)
├── AnthropicChatClient.java            # ANTHROPIC：POST /v1/messages (stream)
├── LlmChatGateway.java                 # 编排：解析配置→解密Key→选Client→并发许可→重试→超时
└── exception/
    └── LlmException.java               # 及可重试/不可重试/超时/取消子类（对齐 07 §七）
```

- 复用现有 `model_provider` / `model` 表与 `SecretEncryptor`。
- Strategy 选 Client：按 `provider_type`（OPENAI/OPENAI_COMPATIBLE 共用一个 Client，ANTHROPIC 单独），与现有测试 Handler 的选取方式一致。
- 流式 Client 必须周期检查 `Thread.currentThread().isInterrupted()` 以支持取消。
- 配置见第十一章。

### 8.4 复用关系

engine 通过 `ModelFacade.chatStream` 调用，不感知 Provider 差异；chat/engine 都不直接接触 Client。

---

## 九、后端 Facade 契约汇总

### 9.1 `AgentFacade`（agent 模块 `api/AgentFacade`）

```java
AgentConfigDTO getAgentConfig(String agentId);
```

`AgentConfigDTO { id, name, agentType, status, systemPrompt, modelId }`。
供 chat（创建会话时校验）与 engine（组装 Prompt 时取 systemPrompt/modelId）使用。不暴露工具/知识库（P1 无）。

### 9.2 `EngineFacade`（engine 模块 `api/EngineFacade`）

```java
ChatTurnResult runChatTurn(ChatTurnCommand command, TextStreamSink sink);
```

- `ChatTurnCommand { agentId, history: List<ChatMessage>, assistantMessageId }`（`ChatMessage` 在 engine `api/dto`：`{ role, content }`）。
- 行为：`AgentFacade.getAgentConfig` → 组装 system + history → `ModelFacade.chatStream` → token 经 `sink` 回调 → 返回 `ChatTurnResult { content, finishReason, usage }`。
- **不读写 conversation/message 表**；失败抛异常，由 chat 决定如何发 `run_error`。
- 中断：`Future.cancel(true)` 透传到 model。

### 9.3 跨模块数据流总览

```text
chat (持久化/HTTP/SSE)
  └─ EngineFacade.runChatTurn (编排，无 DB)
       ├─ AgentFacade.getAgentConfig (读 Agent 配置)
       └─ ModelFacade.chatStream (LLM 流式，解密Key/重试/超时)
```

---

## 十、业务规则汇总

### 10.1 Agent 规则

| 编号 | 规则 |
|------|------|
| A-01 | Agent 名称在未删除数据中唯一 |
| A-02 | `agent_type` 创建后不可修改 |
| A-03 | `model_id` 对 REACT Agent 业务必填且必须可用（DB 允许 NULL） |
| A-04 | 保存时校验 `modelId`：`model.enabled=1 AND provider.status=ACTIVE AND 均未删除` |
| A-05 | 禁用 Agent 后不出现在「新建会话」选择器；已有会话只读 |
| A-06 | 删除 Agent 不级联删会话（会话只读保留） |
| A-07 | 模型被删/禁用后，引用它的 Agent 配置不自动清除，运行时再校验报错 |

### 10.2 会话与消息规则

| 编号 | 规则 |
|------|------|
| C-01 | 会话标题默认取 Agent 名称；P1 不支持改名 |
| C-02 | 会话创建后不可更换 Agent |
| C-03 | 仅 `ACTIVE` 且 `REACT` 且模型可用的 Agent 可建会话 |
| C-04 | 发送消息需会话 ACTIVE、Agent ACTIVE、模型可用 |
| C-05 | ASSISTANT 消息仅在生成完成后 INSERT（无占位行/无 GENERATING 持久态） |
| C-06 | 流式中断且已产出文本时，落库部分文本（`finishReason=CANCELLED`） |
| C-07 | 删除会话级联软删其下全部消息 |
| C-08 | 事务只包数据库写（提交用户消息、落库 ASSISTANT 消息、更新计数），**禁止包 LLM 调用** |

### 10.3 流式与调用规则

| 编号 | 规则 |
|------|------|
| S-01 | 发送消息（POST）与建立流（GET SSE）分两步，由 EventSource GET-only 决定 |
| S-02 | SSE 端点位于 chat 模块；engine 不持有 HTTP/DB |
| S-03 | 用户断开/SSE 超时/SSE 发送失败 必须取消上游 LLM 调用 |
| S-04 | 首 chunk 前失败可重试（model 内部）；首 chunk 后失败不重试，发 `run_error` |
| S-05 | 重试耗尽 / 不可重试错误 → 发 `run_error`，不落库 ASSISTANT |
| S-06 | API Key 全链路不记录、不返回、不入异常 |

---

## 十一、配置项（application.yml 增量）

```yaml
spring:
  threads:
    virtual:
      enabled: true          # 新增：虚拟线程（07 §3.2）

zify:
  llm:
    timeout:                 # 新增：chat-stream 超时（07 §4.2）
      chat-stream:
        connect: 10s
        first-token: 30s
        idle: 45s
        total: 120s
    # provider-defaults.max-concurrent / acquire-timeout 已存在
```

model 模块新增一个虚拟线程执行器 Bean（`llmTaskExecutor`，`Executors.newVirtualThreadPerTaskExecutor()`），供 `chatStream` 任务使用；禁止在 Controller 直接 `Thread.startVirtualThread()`（07 §3.2）。

---

## 十二、错误码（`ErrorCode` 枚举增量）

| 错误码 | 含义 | 触发 |
|--------|------|------|
| `AGENT_NOT_FOUND`（已有 1001） | Agent 不存在/已删除 | 引用校验 |
| `AGENT_NAME_DUPLICATE`（新增） | Agent 名称已存在 | 创建/改名 |
| `AGENT_TYPE_INVALID`（新增） | Agent 类型非法（P1 非 REACT） | 创建 |
| `AGENT_TYPE_IMMUTABLE`（新增） | Agent 类型不可修改 | 更新 |
| `AGENT_INACTIVE`（新增） | Agent 已禁用，不可建会话/发消息 | 会话/消息 |
| `MODEL_UNAVAILABLE`（已有 1109） | 模型不可用 | Agent 保存/对话 |
| `CONVERSATION_NOT_FOUND`（新增） | 会话不存在/已删除 | 引用校验 |
| `CONVERSATION_NOT_ACTIVE`（新增） | 会话非 ACTIVE | 发消息 |
| `MESSAGE_CONTENT_EMPTY`（新增） | 消息内容为空 | 发消息 |
| `CHAT_TURN_FAILED`（新增） | 对话生成失败（LLM 错误的兜底） | engine/model 异常 |

> SSE 流内的失败不抛 HTTP 异常，而是发 `run_error` 事件（见 §5.2）；非流式接口（如发消息前的校验）走标准 `BusinessException`。

---

## 十三、一致性说明与对既有文档的偏离

| 项 | 既有文档 | P1 决策 | 处理 |
|----|---------|---------|------|
| SSE 端点归属 | `06` §11.7：`/api/engine/chat/stream` + `engineApi.ts` | chat 拥有，`/api/chat/...` + `chatApi.ts` | **建议更新 06 §11.7/§11.9**（因 `chat → engine` + 持久化归 chat） |
| 模块包结构 | `06` §三：`adapter/web`、`adapter/sse`、`infrastructure/facade` | 新模块（agent/chat/engine）**遵循 06 四层结构**（脚手架已预建 `adapter/` 等） | model 模块用旧扁平结构（`controller/`、`api/FacadeImpl`）属历史遗留，P1 不改，建议后续统一 |
| no-op Facade | `12` P1：定义 Tool/Knowledge/Workflow Facade no-op | P1 不调用这三模块，**推迟到 P2/P3/P4** 首次消费时建 | 精简路线图 P1「接口先行」一条；pom 依赖已就位，不影响编译 |
| 默认路由 | `router.tsx` 现状：`/`=HomePage、`/chat`=ChatPage | `/`=ChatPage（对齐 `03`） | P1 调整 router，移除 HomePage |
| `CursorPageResult` 字段 | 后端暴露 `nextCursorId`+`nextCursorCreatedAt` | Controller 编码为 opaque `nextCursor` 对齐前端 | 在 Controller 层做编解码，不改 common 类 |

---

## 十四、实现顺序与验收

### 14.1 实现顺序（对齐 `CLAUDE.md` §2）

1. **model 增量**：`infrastructure/client/`（Client + Gateway + 异常）→ `ModelFacade.chatStream` + dto → 配置 → 连通真实 Provider 做一次流式调用自测。
2. **agent**：迁移 V3 → Entity/Mapper/Converter → AgentService（事务、校验）→ AgentFacadeImpl → AgentController + dto → 前端 Agent 列表/表单。
3. **engine**：`EngineFacade.runChatTurn` + dto（组装 Prompt、调 chatStream、回调）→ 虚拟线程执行器。
4. **chat**：迁移 V4 → Entity/Mapper/Converter → ConversationService/MessageService（事务、计数、Keyset）→ Controller + SSE Controller → 前端对话页 + `useChatStream` + `chatStore`。
5. 全链路联调 + 过 `CLAUDE.md` §10 检查清单。

### 14.2 验收标准（DoD）

1. 创建一个只绑模型的 REACT Agent。
2. 在对话页选它新建会话，发送消息，看到**流式**回复；点「停止」能中断并取消上游。
3. 刷新/回到列表，历史完整保留，可继续对话、删除会话。
4. 跨模块只走 Facade；事务未覆盖 LLM 调用；API Key 未泄露。
5. 大表 `message` 用 Keyset 分页；列表接口未返回无关大字段。
