# Zify 一期开发路线图

> 把一期 7 个功能模块拆解成**可顺序交付的开发阶段**，覆盖每阶段的功能、数据、前端、验收标准与边界。
> 制定依据：`02-zify-v01-modules.md`（模块定义）、`03-zify-v01-frontend-view.md`（前端视图）、`11-zify-core-data-model.md`（21 张表）。
> 配套规范：分层与硬约束见 `CLAUDE.md` §3–§6 与 `06-zify-code-organization.md`，数据库见 `10-zify-database-spec.md`，LLM 调用见 `07-zify-LLM-api-calling.md`。

---

## 一、总体策略

### 1. 开发顺序 ≠ 配置顺序

文档 03 的 `模型管理 → 工具 → 知识库 → 工作流 → Agents → 对话` 是**管理员首次配置的前端操作顺序**，不是最优的**开发顺序**。开发顺序按「价值」和「风险」驱动：

- **价值驱动**：每阶段交付一条**端到端可用的闭环**（用户能真正用上某项能力），而不是先把某一层（如所有工具）全部铺满再开下一层。
- **风险驱动**：把最难、返工成本最高的技术点（SSE 流式 + 上游取消、ReAct 编排、RAG 异步管道、工作流执行引擎）尽早验证。

### 2. 垂直切片优先

每个阶段都是「Controller → Service → Facade → 前端」一整刀切到底，最早暴露分层架构问题（跨模块 Facade 边界、事务不覆盖外部调用、SSE 放置位置等），早暴露早改，代价最低。

### 3. 接口先行（一次性定义全部 Facade）

依赖图（`CLAUDE.md` §2）要求 `engine` 依赖 `tool/knowledge/workflow`、`agent` 依赖 `workflow`。为避免「上游模块不存在导致编译失败」，**在 Phase 1 一次性把 7 个业务模块的 Facade 接口全部定义出来**（实现可为 no-op），保证整个依赖图从第一天起就能编译通过；后续阶段只往里填实现，不再动模块边界。

### 4. 每阶段过硬约束

每阶段交付前过 `CLAUDE.md` §10 检查清单，重点守住：
- 跨模块只走 Facade，Controller 只调本模块 Service。
- 事务内禁止调用 LLM/Embedding/MCP/HTTP 等外部慢调用。
- 所有外部调用有超时；SSE 断连必须取消上游 LLM。
- API Key 不记录、不返回、加密存储。

---

## 二、阶段总览

| 阶段 | 主题 | 主要模块 | 状态 | 阶段结束用户能做什么 |
|------|------|---------|------|---------------------|
| **P0** | 基线（基础设施 + 模型） | common、model | ✅ 已完成 | 配置 Provider，验证连通性，能通过 ModelFacade 调 LLM/Embedding |
| **P1** | 核心对话闭环（最小 MVP） | agent、engine、chat | 待开发 | 选 Agent → 发消息 → 看到流式回复 → 历史保留 |
| **P2** | 工具能力 + ReAct 多轮循环 | tool、engine（扩）、agent（扩） | 待开发 | Agent 自主调用 HTTP/MCP 工具完成多步任务 |
| **P3** | 知识库 RAG + 检索增强 | knowledge、engine（扩）、agent（扩） | 待开发 | Agent 基于团队文档有据回答，命中测试可调试 |
| **P4** | 工作流引擎 + 触发器 | workflow、trigger、tool（扩）、agent（扩） | 待开发 | 可视化编排确定性流程，Webhook/Cron 自动触发 |
| **P5** | 收尾打磨与上线 | 全量 | 待开发 | 性能压测、部署上线、System Prompt 变量注入等收尾 |

> 一期全部功能在 **P0–P4** 完成；P5 是上线前的打磨。二期方向（认证、Reranking、混合检索、Ops Trace 等）不在本路线图内，见文末。

### 阶段依赖

```text
P0 基线 ──────► P1 核心闭环 ──┬──► P2 工具+ReAct ──┐
(model/common)  (agent/engine/   │                    ├──► P4 工作流+触发器 ──► P5 收尾上线
                chat)            └──► P3 知识库 RAG ──┘
```

- P2、P3 都在 P1 之后、P4 之前，二者相对独立（可互换，但建议 P2 先，见各阶段说明）。
- P4 依赖 P2（tool）和 P3（knowledge）都已具备，因为工作流的 Tool 节点和 Knowledge Retrieval 节点会复用它们。

---

## 三、P0：基线（已完成）

**范围**：`common` + `model`。一切的地基。

- `common`：配置外化、`BusinessException` + `ErrorCode` 枚举、统一响应 `Result`、工具类、`ThreadPoolExecutor` 构造的线程池、加解密工具。
- `model`：`model_provider` / `model` 表、Provider 配置（API Key 加密存储）、LLM/Embedding 调用归口 `ModelFacade`、连通性测试（Strategy 模式按 Provider 类型）。

**验收**：Provider 可增删改查，连通性测试通过，`ModelFacade` 能发起一次 LLM 调用和一次 Embedding 调用。

> P0 已落地，后续阶段在它的 `ModelFacade` 之上构建。

---

## 四、P1：核心对话闭环（最小 MVP）

> **目标**：交付一期产品的核心价值闭环——用户能选一个 Agent、发消息、看到流式回复、历史会话保留。这是风险最高的一刀（SSE 流式 + 上游取消 + 编排 + 多轮持久化），必须最先验证。

### 后端

| 模块 | 本阶段做 | 跨模块接口 |
|------|---------|-----------|
| **agent** | `agent` 表；Agent CRUD；基础配置（名称/描述/System Prompt/模型选择/类型选择）。类型**只支持 ReAct**（Workflow 类型留到 P4）。工具/知识库绑定先留接口、空实现 | 定义 `AgentFacade`（供 engine 取 Agent 配置） |
| **engine** | 最小编排：**零工具**即「调一次 LLM → 流式返回」；打通 SSE 流式、断连/超时取消上游 LLM、显式 retry wrapper、超时配置（`07`）；ReAct 框架搭好但循环一轮即结束 | 定义 `EngineFacade`（流式对话执行入口）；定义 `ToolFacade`/`KnowledgeFacade`/`WorkflowFacade` **接口**（no-op 实现，仅为编译） |
| **chat** | `conversation` / `message` 表；会话 CRUD、新建、删除、继续对话；消息持久化（用户消息 + AI 回复） | — |

**关键架构决策（engine ↔ chat 边界）**：依赖图规定 `chat → engine`（engine 不依赖 chat）。因此：

- **engine 是纯编排**：输入「Agent 配置 + 历史消息 + 用户输入」，输出「事件流 + 工具调用」，不持有对话持久化。
- **chat 持有持久化**：chat 的 Controller 加载 `message` 历史构建上下文 → 调 `EngineFacade` 获取事件流 → 落库 `conversation`/`message`。
- engine 只写它**自己的** `tool_call_log`（P2 引入），不碰 chat 的表。

这样既满足依赖方向，又把「编排」与「持久化」职责切开。

### 数据库（→ `10`、`11`）

新增 3 张 MySQL 表：`agent`(5)、`conversation`(17)、`message`(18)。
`agent` 表预留 `type`、`workflow_id`（P4 用）、模型列；绑定关联表 `agent_tool`/`agent_knowledge` 留到 P2/P3 建。

### 前端（→ `03`）

- 对话页 `/`（默认落地页）：左栏会话列表（按时间倒序）、新建会话（弹 Agent 选择器）、搜索；右栏消息流 + 输入框 + 中断按钮。
- Agents 列表 `/agents` + 创建/编辑 `/agents/create`、`/agents/[id]/edit`：Step 1 基础信息（含类型选择，Workflow 项可选不可用）、Step 2 System Prompt、Step 3 模型选择；**工具/知识库/工作流绑定步骤先隐藏**。
- SSE 创建放 `api/engineApi.ts`，UI 状态放 `features/chat/hooks/useChatStream.ts`。

### 验收标准（DoD）

1. 创建一个只绑模型的 ReAct Agent。
2. 在对话页选它新建会话，发送消息，看到**流式**回复；断开/中断能取消上游。
3. 刷新页面或回到会话列表，历史消息完整保留，可继续对话、删除会话。
4. 跨模块只走 Facade；事务未覆盖 LLM 调用；API Key 未泄露。

### 边界（本阶段不做）

- ❌ 工具调用循环、❌ 知识库检索、❌ Workflow Agent、❌ System Prompt 变量注入的高级处理（P5）、❌ 消息反馈/收藏。

---

## 五、P2：工具能力 + ReAct 多轮循环

> **目标**：把 P1 的「单轮 LLM」升级为真正的 ReAct 多轮循环——Agent 能自主决策、调用 HTTP/MCP 工具、观察结果、再决策，直到完成任务。

### 为什么排在 P3（知识库）之前

工具比知识库轻（无解析/分块/向量化管道），且**统一 Tool 接口（`name/description/parameters/execute`）应由 ReAct 消费方来定义**——趁 P1 刚搭好编排框架，把工具接入循环，接口设计才不会跑偏。

### 后端

| 模块 | 本阶段做 |
|------|---------|
| **tool** | `mcp_server`/`tool` 表；统一 Tool 接口与抽象；**HTTP 工具**（手动配置 URL/Header/Body + OpenAPI Schema 解析）；**MCP Client**（连接 SSE/Streamable HTTP → 发现 → 注册工具）；工具管理（列表/启用禁用/编辑）；`ToolFacade` 实装 |
| **engine**（扩） | ReAct 多轮循环：LLM 决策 → 调 `ToolFacade.execute` → 观察回灌历史 → 再决策 … → 结束；工具结果作为消息历史一部分；循环中断；写入 `tool_call_log`(19) |
| **agent**（扩） | `agent_tool`(6) 表；创建/编辑表单的**工具绑定**步骤激活（多选） |

### 数据库

新增 3 张 MySQL 表：`mcp_server`(3)、`tool`(4)、`tool_call_log`(19, 大表)、`agent_tool`(6, 关联表)。

### 前端

- 工具列表 `/tools`（按类型分组：MCP/HTTP/工作流占位）、HTTP 工具创建 `/tools/create?type=http`、MCP 连接 `/tools/create?type=mcp`（连接测试 + 发现工具列表）。
- Agent 表单 Step 3 显示工具绑定多选；对话区展示工具调用过程（输入/输出/耗时）。

### 验收标准

1. 创建一个 HTTP 工具并绑定到 Agent，Agent 能在对话中自主调用并基于返回结果回答。
2. 连接一个 MCP Server，自动发现工具，Agent 可调用其中已启用工具。
3. `tool_call_log` 记录每次调用的输入输出，可用于调试。
4. 工具调用有超时；循环可中断；SSE 断连取消上游。

### 边界

- ❌ Workflow-as-Tool（要等工作流，P4）、❌ 内置工具库、❌ MCP Server（只做 Client）、❌ 工具 OAuth。

---

## 六、P3：知识库 RAG + 检索增强

> **目标**：让 Agent 能检索团队内部文档，提供有据可依的回答；并内嵌命中测试供调试检索质量。

### 后端

| 模块 | 本阶段做 |
|------|---------|
| **knowledge** | `knowledge`/`document`/`document_parse_log` 表（MySQL）+ `document_chunk`（**PostgreSQL + pgvector**）；知识库 CRUD；文档上传（PDF/DOCX/TXT/MD）→ 异步解析 → 段落级分块（可配大小/重叠）→ Embedding（走 `ModelFacade`）→ 入库；向量检索 Top-K（带 `knowledge_id` 过滤，不返回 `embedding`）；命中测试；`KnowledgeFacade`（检索接口）实装 |
| **engine**（扩） | Agent 调用时按绑定知识库检索 Top-K，注入 System Prompt / 上下文 |
| **agent**（扩） | `agent_knowledge`(7) 表；创建/编辑表单的**知识库绑定**步骤激活 |

**异步解析管线**：文档解析/分块/Embedding 是慢外部调用，必须**异步执行**（线程池 + 进度写 Redis），**绝不在数据库事务内做**（§5 硬约束）。

### 数据库

新增 4 张表：`knowledge`(8)、`document`(9)、`document_chunk`(10, pgvector)、`document_parse_log`(11, 大表)、`agent_knowledge`(7, 关联表)。首次引入 PostgreSQL + pgvector。

### 前端

- 知识库列表 `/knowledge`；详情 `/knowledge/[id]` 双 Tab：**文档管理**（拖拽上传、状态/进度实时更新、chunk 列表展开编辑）+ **命中测试**（查询 + Top-K + 相似度分数）。
- Agent 表单 Step 3 显示知识库绑定多选。

### 验收标准

1. 创建知识库、上传文档，看到「解析中 → 已完成」进度，文档被正确分块并向量化。
2. 命中测试输入查询，返回 Top-K chunk + 相似度分数 + 来源文档。
3. Agent 绑定知识库后，对话中能基于文档内容有据回答。

### 边界（→ `02` §4）

- ❌ 关键词/混合检索、❌ Reranking、❌ RAG Pipeline、❌ 摘要索引、❌ 多分块策略、❌ Notion/网页抓取等在线数据源、❌ 多向量库（只用 pgvector）。

---

## 七、P4：工作流引擎 + 触发器 + Workflow Agent

> **目标**：可视化编排确定性流程，支持自动触发；打通工作流作为 Agent 类型（Workflow Agent）和作为工具（Workflow-as-Tool）。这是最重的一阶段，依赖 P2/P3 已就绪。

### 后端

| 模块 | 本阶段做 |
|------|---------|
| **workflow** | `workflow`/`workflow_node`/`workflow_edge`/`workflow_run`/`workflow_node_run` 表；编辑数据存储（画布/节点/连线）；**节点执行引擎**（拓扑顺序、条件分支 If-Else、并行）；变量系统（节点间传递）；**9 个节点**（Start/End/LLM/If-Else/HTTP Request/Knowledge Retrieval/Code/Tool/Answer）；运行日志；`WorkflowFacade` 实装（含「执行一次」入口，供触发器和 Workflow-as-Tool 复用） |
| **trigger** | `trigger`/`trigger_log` 表；Webhook 唯一端点；Cron 调度；触发器绑定工作流；触发日志（关联 `workflow_run`） |
| **tool**（扩） | Workflow-as-Tool：工作流发布时自动注册为统一工具，调用即启动一次工作流运行 |
| **agent**（扩） | **Workflow Agent** 类型激活：绑定一个已创建工作流（用 P1 预留的 `agent.workflow_id`） |

### 数据库

新增 7 张表：`workflow`(12)、`workflow_node`(13)、`workflow_edge`(14)、`workflow_run`(15, 大表)、`workflow_node_run`(16, 大表)、`trigger`(20)、`trigger_log`(21, 大表)。**至此 21 张表全部落地。**

### 前端

- 工作流列表 `/workflows`（卡片：名称/触发方式标签/最近运行）；全屏画布 `/workflows/[id]`（顶部工具栏、左侧节点面板、中间画布、右侧节点配置、底部运行日志面板）；触发器配置弹窗。
- Agent 表单：Workflow Agent 类型可用，Step 3/4 改为「绑定工作流」。
- 工具列表的「工作流工具」分组填充（发布工作流后自动出现）。

### 验收标准

1. 拖拽编排一个含 LLM + If-Else + HTTP Request 的工作流，手动运行成功，运行日志显示各节点输入输出。
2. Knowledge Retrieval 节点能检索 P3 的知识库；Tool 节点能调用 P2 的工具。
3. 为工作流配置 Webhook，外部 POST 触发运行并记录日志；配置 Cron，按周期自动运行。
4. 创建 Workflow Agent 绑定该工作流，对话中调用它即执行工作流。
5. 发布的工作流自动出现在工具列表，可被其他 Agent/工作流当工具调用。

### 边界（→ `02` §5、§6）

- ❌ 迭代/循环节点、❌ 模板转换/变量赋值/变量聚合等节点、❌ 问题分类器/参数提取器、❌ Human-in-the-Loop、❌ DSL 版本管理、❌ 画布实时协作、❌ Agent 子工作流节点、❌ 触发器复杂条件过滤。

---

## 八、P5：收尾打磨与上线

> **目标**：一期功能在 P0–P4 已全部完成，P5 是面向上线的体验打磨与验证。

- **System Prompt 变量注入**：`{{date}}` 等变量（`02` §1 明确要，P1 简化处理，这里补全）。
- **对话体验收尾**：会话重命名/复制、按 Agent 分组展示（`03` §1）、空状态引导。
- **性能验证**：按 `09-zify-performance-bottleneck.md` 压测瓶颈点（大表 `message`/`tool_call_log`/运行日志的分页与归档、pgvector 检索性能、SSE 并发）。
- **部署上线**：按 `08-zify-deployment-architecture.md`（Docker + K8s 单副本、TLS 在 Ingress 终止、上传写 PVC、每日备份 MySQL/PG/uploads 保留 14 天）。
- **全局回归**：7 个一级导航全部走通；过 `CLAUDE.md` §10 全量检查清单。

### 验收标准

1. 内部 ~50 人可日常使用全部 7 个功能，核心链路（对话/工具/知识库/工作流）稳定。
2. 压测下无明显性能瓶颈或已记录应对策略；备份与恢复流程验证通过。

---

## 九、二期及以后（不在本路线图内）

文档明确列为二期/按需的方向，一期**不做**，仅在此登记，避免误纳入：

- 用户认证与权限（一期预留用户标识字段，方便二期迁移）。
- 知识库：Reranking、关键词/混合检索。
- 工作流：迭代/循环节点。
- Ops Trace（Langfuse）、消息反馈与收藏。
- 多向量库、计费、应用市场、插件、SSO 等（一期明确不做）。

---

## 十、如何使用本文档

1. 每开始一个阶段，先确认上一阶段的 DoD 已达成、相关 Facade 接口已就位。
2. 阶段内按 `CLAUDE.md` §2 的功能实现顺序：模块归属 → `pom.xml` 依赖 → DTO/Entity/Mapper/Converter → Service（事务）→ FacadeImpl → Controller → 前端。
3. 阶段结束过 `CLAUDE.md` §10 检查清单。
4. 若阶段内需求与本文冲突，**先更新本文档（或对应 `02`/`03` 决策文档），再写代码**。
