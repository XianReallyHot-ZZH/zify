# P1 核心对话闭环 — 数据模型设计

> 本文档定义 P1（核心对话闭环 MVP）涉及的数据库表结构、字段含义、索引、表关系与迁移方案。
> P1 范围：`agent`、`conversation`、`message` 三张 MySQL 表。
> 不涉及建表的 P1 增量（model 模块的流式 Chat 网关）见 `02-functional-spec.md` 第八章。
> 建表规范遵循 `glm-docs/10-zify-database-spec.md`，表关系对齐 `glm-docs/11-zify-core-data-model.md`。

---

## 一、设计背景

P1 交付「选 Agent → 发消息 → 流式回复 → 历史保留」的最小闭环，新增三张表：

| 表 | 模块 | 用途 | 规模 |
|----|------|------|------|
| `agent` | agent | Agent 主表（P1 只用 REACT 类型） | 小表 |
| `conversation` | chat | 对话会话 | 中表 |
| `message` | chat | 对话消息（用户消息 + AI 回复） | 大表 |

P1 **不建**的表（后续阶段建，但模块 pom 依赖已就位、Facade 接口先以 no-op 定义）：

- `agent_tool`、`agent_knowledge`（P2/P3，工具与知识库绑定）
- `tool_call_log`（P2，engine 写入）
- `workflow`、`workflow_node`、`workflow_edge`、`workflow_run`、`workflow_node_run`、`trigger`、`trigger_log`（P4）

`agent` 表为 P4 预留 `workflow_id` 列（WORKFLOW 类型用），P1 恒为 NULL。

P1 **不新增** model 模块的表：流式 Chat 网关是纯代码增量（`infrastructure/client/` + `ModelFacade.chatStream`），复用已有的 `model_provider` / `model` 表读取供应商配置与解密 API Key。

---

## 二、ER 关系

```text
model (LLM)
  └─ 1:N  agent                 Agent 选用一个 LLM 模型（agent.model_id）

agent
  ├─ 1:N  conversation           一个 Agent 多个会话
  │         └─ 1:N  message      一个会话多条消息
  └─ N:1  workflow              （仅 WORKFLOW Agent，P4；P1 不用）
```

P1 实际使用的关系链：

```text
agent.model_id  → model.id            （逻辑引用，无物理外键）
conversation.agent_id → agent.id      （逻辑引用，无物理外键）
message.conversation_id → conversation.id（逻辑引用，无物理外键）
```

一期不建数据库物理外键（`10-zify-database-spec.md` §4.4），关联完整性由 Service 层校验。

---

## 三、表结构

### 3.1 `agent` — Agent 主表

```sql
CREATE TABLE `agent` (
    `id`            CHAR(36)     NOT NULL COMMENT 'Agent ID',
    `created_at`    DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间，UTC',
    `updated_at`    DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间，UTC',
    `is_deleted`    TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除：0 正常，1 已删除',
    `created_by`    CHAR(36)     NULL     COMMENT '创建人用户ID，一期无登录可为空',
    `updated_by`    CHAR(36)     NULL     COMMENT '更新人用户ID，一期无登录可为空',

    `name`          VARCHAR(128) NOT NULL COMMENT 'Agent 名称',
    `description`   VARCHAR(512) NULL     COMMENT 'Agent 描述',
    `agent_type`    VARCHAR(32)  NOT NULL COMMENT 'Agent 类型：REACT / WORKFLOW',
    `status`        VARCHAR(32)  NOT NULL DEFAULT 'ACTIVE' COMMENT '状态：ACTIVE / INACTIVE',
    `system_prompt` MEDIUMTEXT   NULL     COMMENT 'System Prompt，定义 Agent 角色、行为与回答风格',
    `model_id`      CHAR(36)     NULL     COMMENT '绑定的 LLM 模型 ID（REACT 必填，引用 model.id）',
    `workflow_id`   CHAR(36)     NULL     COMMENT '绑定的工作流 ID（WORKFLOW 类型用，P4 启用）',

    `active_name`   VARCHAR(128)
        GENERATED ALWAYS AS (
            CASE WHEN `is_deleted` = 0 THEN `name` ELSE NULL END
        ) STORED COMMENT '未删除 Agent 名称唯一键辅助列',

    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_agent_active_name` (`active_name`),
    KEY `idx_agent_type_deleted_created_id`
        (`agent_type`, `is_deleted`, `created_at` DESC, `id` DESC),
    KEY `idx_agent_model_deleted_id`
        (`model_id`, `is_deleted`, `id` DESC),
    KEY `idx_agent_created_at` (`created_at`)
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_0900_ai_ci
  COMMENT='Agent';
```

#### 字段说明

| 字段 | 必填 | 说明 |
|------|------|------|
| `name` | 是 | 用户自定义名称，未删除数据中唯一 |
| `description` | 否 | Agent 用途说明，列表卡片展示 |
| `agent_type` | 是 | `REACT` / `WORKFLOW`。P1 创建只允许 `REACT`；`WORKFLOW` 类型在 P4 启用，创建后不可修改 |
| `status` | 是 | `ACTIVE` / `INACTIVE`。`INACTIVE` 的 Agent 不出现在「新建会话」选择器中；已有会话继续可查看历史，但不能再发新消息 |
| `system_prompt` | 否 | System Prompt。P1 不做 `{{variable}}` 变量注入（P5 补），按原文透传给 LLM。为空时 Agent 无系统人设 |
| `model_id` | 否（DB）/ 是（REACT 业务） | 绑定的 LLM 模型。DB 允许 NULL 以兼容未来草稿；REACT Agent 保存时 Service 强制非空且校验模型可用 |
| `workflow_id` | 否 | P1 恒为 NULL，仅为 P4 WORKFLOW Agent 预留列 |

#### 索引说明

| 索引 | 服务查询 |
|------|---------|
| `uk_agent_active_name` | 名称未删除唯一约束（generated column） |
| `idx_agent_type_deleted_created_id` | Agent 列表按类型筛选 + 创建时间倒序分页 |
| `idx_agent_model_deleted_id` | 「该模型被多少 Agent 引用」（删除模型/禁用模型时的提示校验） |
| `idx_agent_created_at` | 通用时间排序与潜在清理 |

---

### 3.2 `conversation` — 对话会话

```sql
CREATE TABLE `conversation` (
    `id`              CHAR(36)     NOT NULL COMMENT '会话 ID',
    `created_at`      DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间，UTC',
    `updated_at`      DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间，UTC',
    `is_deleted`      TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除：0 正常，1 已删除',
    `created_by`      CHAR(36)     NULL     COMMENT '创建人用户ID，一期无登录可为空',
    `updated_by`      CHAR(36)     NULL     COMMENT '更新人用户ID，一期无登录可为空',

    `title`           VARCHAR(128) NOT NULL COMMENT '会话标题，默认取 Agent 名称',
    `agent_id`        CHAR(36)     NOT NULL COMMENT '所属 Agent ID',
    `status`          VARCHAR(32)  NOT NULL DEFAULT 'ACTIVE' COMMENT '状态：ACTIVE / ARCHIVED',
    `message_count`   INT          NOT NULL DEFAULT 0 COMMENT '消息数量（冗余，列表展示用）',
    `last_message_at` DATETIME(3)  NOT NULL COMMENT '最后一条消息时间（冗余，列表排序用；创建时置为创建时间）',

    PRIMARY KEY (`id`),
    KEY `idx_conv_deleted_lastmsg_id`
        (`is_deleted`, `last_message_at` DESC, `id` DESC),
    KEY `idx_conv_agent_deleted_created_id`
        (`agent_id`, `is_deleted`, `created_at` DESC, `id` DESC),
    KEY `idx_conv_created_at` (`created_at`)
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_0900_ai_ci
  COMMENT='对话会话';
```

#### 字段说明

| 字段 | 必填 | 说明 |
|------|------|------|
| `title` | 是 | 会话标题。创建时由 Service 置为 Agent 名称；重命名留到 P5 |
| `agent_id` | 是 | 会话所属 Agent。创建后不可更换（换 Agent 视为新建会话） |
| `status` | 是 | `ACTIVE` / `ARCHIVED`。P1 主要用 `ACTIVE`；`ARCHIVED` 预留 |
| `message_count` | 是 | 冗余计数，用于列表卡片展示「N 条消息」。每新增一条消息 +1，由 Service 维护 |
| `last_message_at` | 是 | 冗余时间戳，用于会话列表按最近活动排序。**创建时置为创建时间（永不为 NULL）**，每新增消息时更新，避免 NULL 在 `ORDER BY ... DESC` 下的游标分页问题 |

#### 索引说明

| 索引 | 服务查询 |
|------|---------|
| `idx_conv_deleted_lastmsg_id` | 会话列表按最近活动倒序 Keyset 分页（对话页左栏主查询） |
| `idx_conv_agent_deleted_created_id` | 「某 Agent 下的会话」（侧栏按 Agent 分组、Agent 卡片「最近对话」） |
| `idx_conv_created_at` | 通用时间排序 |

`conversation` 不在 `10-zify-database-spec.md` §6.4 的 OFFSET 白名单中，列表用 **Keyset 分页**（游标 = `last_message_at#id`）。

---

### 3.3 `message` — 对话消息（大表）

```sql
CREATE TABLE `message` (
    `id`              CHAR(36)     NOT NULL COMMENT '消息 ID',
    `created_at`      DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间，UTC',
    `updated_at`      DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间，UTC',
    `is_deleted`      TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除：0 正常，1 已删除',

    `conversation_id` CHAR(36)     NOT NULL COMMENT '所属会话 ID',
    `role`            VARCHAR(32)  NOT NULL COMMENT '角色：USER / ASSISTANT / SYSTEM / TOOL',
    `content`         LONGTEXT     NOT NULL COMMENT '消息正文',
    `metadata`        JSON         NULL     COMMENT '消息元数据（仅 ASSISTANT：modelId/modelName/token用量/finishReason/耗时）',

    PRIMARY KEY (`id`),
    KEY `idx_msg_conv_deleted_created_id`
        (`conversation_id`, `is_deleted`, `created_at` DESC, `id` DESC),
    KEY `idx_msg_created_at` (`created_at`)
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_0900_ai_ci
  COMMENT='对话消息';
```

#### 字段说明

| 字段 | 必填 | 说明 |
|------|------|------|
| `conversation_id` | 是 | 所属会话，必须建索引（大表父对象） |
| `role` | 是 | `USER`（用户消息）/ `ASSISTANT`（AI 回复）/ `SYSTEM` / `TOOL`。P1 只产生 `USER` 和 `ASSISTANT`；`SYSTEM`/`TOOL` 为后续阶段预留语义 |
| `content` | 是 | 消息正文。`LONGTEXT`，列表/详情均需返回（消息流渲染必需）；用 Keyset 分页控制单次返回量 |
| `metadata` | 否 | 仅 ASSISTANT 消息写入。`USER` 消息为 NULL。见 §四 JSON 约定 |

`message` 不设 `created_by` / `updated_by`：可通过 `conversation → agent → created_by` 追溯用户（`10-zify-database-spec.md` §2.2 大表规则）。

`message` 不设 `status` 字段：P1 的 ASSISTANT 消息**只在生成完成后 INSERT**（无占位行、无 GENERATING 中间态），故不存在「生成中/失败」的持久化状态；流式过程中的临时态只活在 SSE 事件与前端，生成失败则不落库（错误经 `run_error` 事件透传给前端）。

#### 索引说明（大表，遵循 §5.3 模板）

| 索引 | 服务查询 |
|------|---------|
| `idx_msg_conv_deleted_created_id` | 某会话内消息按时间倒序 Keyset 分页（对话区「加载更多历史」） |
| `idx_msg_created_at` | 历史数据清理（按 `created_at` 分批物理删除，保留 180 天） |

---

## 四、JSON 字段约定

### 4.1 `message.metadata`（仅 ASSISTANT）

记录一次 AI 回复的运行元数据，供后续调试与（二期）用量统计使用。

```jsonc
{
  "modelId": "uuid",
  "modelName": "gpt-4o",
  "providerType": "OPENAI_COMPATIBLE",
  "promptTokens": 320,
  "completionTokens": 180,
  "totalTokens": 500,
  "finishReason": "STOP",
  "durationMs": 4200
}
```

| 字段 | 说明 |
|------|------|
| `modelId` / `modelName` / `providerType` | 本次回复实际使用的模型快照（模型可能被删除/改名，故快照存元数据） |
| `promptTokens` / `completionTokens` / `totalTokens` | Token 用量，来自 Provider 返回（无则省略） |
| `finishReason` | `STOP`（正常结束）/ `LENGTH`（达到 max_tokens）/ `TIMEOUT` / `CANCELLED` 等 |
| `durationMs` | 从发起 LLM 调用到结束的耗时 |

**规则**（对齐 `10-zify-database-spec.md` §5.6）：

- `metadata` 只存运行元数据，不参与业务过滤/排序。
- 列表/会话历史接口**可返回** `metadata`（轻量），但 `message` 的列表语义即「会话内消息流」，本身就需 `content`，故 `metadata` 一并返回不违反大字段规则。
- 结构由 Java DTO 校验，数据库不约束 JSON 内部结构。

---

## 五、查询场景与索引命中

| 查询场景 | 命中索引 | 说明 |
|---------|---------|------|
| Agent 列表（按名称搜索 + 类型筛选） | `idx_agent_type_deleted_created_id` + `uk_agent_active_name` | 小表，OFFSET 分页 |
| Agent 详情/编辑 | PRIMARY KEY | — |
| 该模型被哪些 Agent 引用 | `idx_agent_model_deleted_id` | 删除/禁用模型时的提示 |
| 会话列表（按最近活动倒序） | `idx_conv_deleted_lastmsg_id` | Keyset 分页，游标 `last_message_at#id` |
| 某 Agent 的会话 | `idx_conv_agent_deleted_created_id` | 侧栏分组 |
| 某会话的消息历史 | `idx_msg_conv_deleted_created_id` | Keyset 分页，游标 `created_at#id` |

---

## 六、分页方案

| 表 | 方式 | 排序键 | 游标 |
|----|------|--------|------|
| `agent` | OFFSET（小表白名单） | `created_at DESC, id DESC` | `page` / `pageSize` |
| `conversation` | Keyset | `last_message_at DESC, id DESC` | opaque `cursor` |
| `message` | Keyset（大表） | `created_at DESC, id DESC` | opaque `cursor` |

**游标编解码**：后端 `CursorPageResult` 暴露 `nextCursorId` + `nextCursorCreatedAt` 两个字段，但前端 `CursorPageResponse` 期望单一 opaque `nextCursor` 字符串。Controller 层负责编解码：

- 编码（响应）：`nextCursor = Base64Url( ISO-8601(created_at) + "#" + id )`，无更多数据时为 `null`。
- 解码（请求）：将入参 `cursor` Base64Url 解码后拆出 `created_at` 与 `id`，填入查询的 Keyset 条件。

前端无需感知游标内部结构，直接透传 `nextCursor`。

---

## 七、迁移方案

迁移脚本位于 `zify-app/src/main/resources/db/migration/`，沿用现有命名 `V{n}__{module}__{desc}.sql`（现有 V1 common、V2 model）。

```text
V3__agent__create_agent_table.sql          # 建 agent 表
V4__chat__create_conversation_message.sql  # 建 conversation、message 表
```

规则：

- 一张表一个建表语句，建表即含索引（`10-zify-database-spec.md` §1.3）。
- 不使用 `SELECT *`，所有查询显式列字段。
- 不创建物理外键。
- 迁移上线后回滚通过新写一个迁移脚本完成，不手工改表。

---

## 八、与 `glm-docs/11-zify-core-data-model.md` 的对照

| 维度 | doc 11 原设计 | P1 落地 | 说明 |
|------|--------------|---------|------|
| `agent` 表 | 已规划 | 本文档定稿字段 | 补 `system_prompt`(MEDIUMTEXT)、`model_id`、`workflow_id`、`description`、`status`、`active_name` 唯一键 |
| `conversation` 表 | 已规划 | 本文档定稿字段 | 补 `title`、`message_count`、`last_message_at` 冗余字段以支撑列表 |
| `message` 表 | 已规划 | 本文档定稿字段 | 补 `metadata` JSON 存运行元数据；P1 不设 `status`（生成完成才入库） |
| `agent_tool` / `agent_knowledge` | 已规划 | P1 **不建** | P2/P3 建 |
| 关系 `model 1:N agent` | 已规划 | 落地 | `agent.model_id` 逻辑引用 |
| 关系 `agent 1:N conversation 1:N message` | 已规划 | 落地 | 逻辑引用，无物理外键 |
