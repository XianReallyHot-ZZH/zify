# P2 工具能力 + ReAct 多轮循环 — 数据模型设计

> 本文档定义 P2（工具能力 + ReAct 多轮循环）涉及的数据库表结构、字段含义、JSON 约定、索引、表关系与迁移方案。
> P2 新增 4 张 MySQL 表：`mcp_server`(3)、`tool`(4)、`tool_call_log`(19)、`agent_tool`(6)；并扩展 P1 `message` 表的 `metadata` JSON（**不改列**）。
> 建表规范遵循 `glm-docs/10-zify-database-spec.md`，表关系对齐 `glm-docs/11-zify-core-data-model.md`。
> 技术决策依据：`docs-prd/phase-P2/00-technical-decisions.md`（A1/B2/A2/B1/C1–C7/D1/D2 全部 ✅）。

---

## 一、设计背景

P2 在 P1 核心对话闭环之上，把「单轮 LLM」升级为真正的 ReAct 多轮循环——Agent 自主决策、调用 HTTP/MCP 工具、观察结果、再决策，直到完成任务。为此引入统一工具系统与工具调用日志。

| 表 | 模块 | 用途 | 规模 |
|----|------|------|------|
| `mcp_server` | tool | MCP Server 连接配置（连接地址/传输/认证/连接状态） | 小表 |
| `tool` | tool | 统一工具定义（HTTP 工具 + MCP 工具；P4 再加 Workflow-as-Tool） | 小表 |
| `tool_call_log` | tool | 工具调用日志（执行点即记录点；对话/工作流/手动测试都经此） | **大表** |
| `agent_tool` | agent | Agent ↔ 工具 N:N 关联 | 关联表 |

P2 **不改列**的表：

- `message`（chat 模块）：P1 已预留 `role=TOOL`，P2 仅扩展 `metadata` JSON 结构以承载工具调用过程（见 §3.5）。
- `conversation`、`agent`、`model`：不变。
- P1 未建的 `agent_knowledge`、`knowledge*`、`workflow*`、`trigger*`：本阶段不建（P3/P4）。

### 1.1 关键设计取舍

- **`tool_call_log` 归 tool 模块**（修订 `glm-docs/11`，原归 engine）：写入点 = `ToolFacade.executeTool` 内部（执行点即记录点）。理由：tool_call_log 须同时服务对话（engine 经 ToolFacade）与工作流（workflow 经 ToolFacade，P4）；唯一能被两者依赖的是 tool 模块（`engine→tool`、`workflow→tool` 均允许），归 engine 则工作流场景无人能写。
- **工具调用过程进 `message` 表**（不单存 tool_call_log）：ASSISTANT toolCall 请求 + TOOL 响应都落 `message`，供模型继续对话时重建多轮工具上下文；`tool_call_log` 存详细执行日志（调试），二者经 `metadata.toolCallLogId` 关联。
- **HTTP 工具与 OpenAPI 导入底层同构**：手动配置与 OpenAPI 解析产出同一份 `tool` 表配置；OpenAPI 3.0/3.1，一个 operation → 一个 tool。
- **MCP 工具按 remote tool 粒度注册**：一个 MCP Server 发现的每个工具 → 一条 `tool` 记录（`source_type=MCP`、关联 `mcp_server_id`）。
- **MCP Server 双状态**：`enabled`（管理员开关）+ `status`（连接健康度 ONLINE/OFFLINE/ERROR），职责分离（见 §3.1）。

---

## 二、ER 关系

```text
agent
  └─ N:N  tool          via agent_tool（agent 模块）

tool
  ├─ N:1  mcp_server    仅 MCP 工具关联其来源 server（HTTP 工具 mcp_server_id=NULL）
  └─ 1:N  tool_call_log 一个工具产生多条调用日志

message(TOOL)
  └─ 0..1:1  tool_call_log  一条 TOOL 消息对应一条工具调用日志（经 metadata.toolCallLogId 关联）

conversation / agent / workflow_run(P4) / workflow_node_run(P4)
  └─ 1:N  tool_call_log   触发方可是对话/工作流/手动测试（关联 ID nullable）
```

P2 实际使用的关系链（逻辑引用，一期不建物理外键，见 `10` §4.4）：

```text
agent_tool.agent_id        → agent.id
agent_tool.tool_id         → tool.id
tool.mcp_server_id         → mcp_server.id        （仅 MCP 工具）
tool_call_log.tool_id      → tool.id
tool_call_log.mcp_server_id → mcp_server.id       （仅 MCP 工具，冗余快照）
tool_call_log.conversation_id → conversation.id   （对话场景）
tool_call_log.agent_id     → agent.id             （对话场景）
message.metadata.toolCallLogId → tool_call_log.id （TOOL 消息 ↔ 日志）
```

---

## 三、表结构

### 3.1 `mcp_server` — MCP Server 连接配置

```sql
CREATE TABLE `mcp_server` (
    `id`                CHAR(36)     NOT NULL COMMENT 'MCP Server ID',
    `created_at`        DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间，UTC',
    `updated_at`        DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间，UTC',
    `is_deleted`        TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除：0 正常，1 已删除',
    `created_by`        CHAR(36)     NULL     COMMENT '创建人用户ID，一期无登录可为空',
    `updated_by`        CHAR(36)     NULL     COMMENT '更新人用户ID，一期无登录可为空',

    `name`              VARCHAR(128) NOT NULL COMMENT 'MCP Server 名称（用户自定义，如"GitHub MCP"）',
    `description`       VARCHAR(512) NULL     COMMENT '描述',
    `base_url`          VARCHAR(512) NOT NULL COMMENT '连接地址（Streamable HTTP 或 SSE 端点）',
    `transport_type`    VARCHAR(32)  NOT NULL DEFAULT 'STREAMABLE_HTTP' COMMENT '传输方式：STREAMABLE_HTTP / SSE',
    `auth_type`         VARCHAR(32)  NOT NULL DEFAULT 'NONE' COMMENT '认证方式：NONE / API_KEY / BEARER（OAuth 一期不做）',
    `auth_config`       VARCHAR(1024) NULL    COMMENT '认证凭据（加密 JSON 整体存储，复用 SecretEncryptor；NONE 时为 NULL）',
    `enabled`           TINYINT      NOT NULL DEFAULT 1 COMMENT '管理员启用开关：0 禁用（断开连接且不重连），1 启用',
    `status`            VARCHAR(32)  NOT NULL DEFAULT 'OFFLINE' COMMENT '连接健康度：ONLINE / OFFLINE / ERROR',
    `status_message`    VARCHAR(512) NULL     COMMENT '连接状态详情（如最近一次错误信息）',
    `last_connected_at` DATETIME(3)  NULL     COMMENT '最近一次连接成功（ONLINE）时间',

    `active_name`       VARCHAR(128)
        GENERATED ALWAYS AS (
            CASE WHEN `is_deleted` = 0 THEN `name` ELSE NULL END
        ) STORED COMMENT '未删除名称唯一键辅助列',

    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_ms_active_name` (`active_name`),
    KEY `idx_ms_enabled_deleted_created_id`
        (`enabled`, `is_deleted`, `created_at` DESC, `id` DESC),
    KEY `idx_ms_status_deleted_id` (`status`, `is_deleted`, `id` DESC),
    KEY `idx_ms_created_at` (`created_at`)
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_0900_ai_ci
  COMMENT='MCP Server 连接配置';
```

#### 字段说明

| 字段 | 必填 | 说明 |
|------|------|------|
| `name` | 是 | 用户自定义名称，未删除唯一（generated column 唯一键） |
| `description` | 否 | 用途说明，列表卡片副标题 |
| `base_url` | 是 | 远程 MCP Server 端点。保存时 + 运行时均过 SSRF 黑名单（`13` §8.1） |
| `transport_type` | 是 | `STREAMABLE_HTTP`（默认，MCP 现代传输）/ `SSE`。一期不做 stdio（`13` §9.1） |
| `auth_type` | 是 | `NONE`（无认证，默认）/ `API_KEY`（自定义 Header 名+值）/ `BEARER`（`Authorization: Bearer <token>`）。OAuth 留二期 |
| `auth_config` | 否 | 整个认证凭据对象加密后存储（见 §4.4）；`auth_type=NONE` 时为 NULL。明文仅连接/调用时解密，不记录、不返回 |
| `enabled` | 是 | 管理员开关。`0`=禁用：关闭已建连接、不再重连、其下工具在 `listAvailableTools` 中不可见；`1`=启用 |
| `status` | 是 | **连接健康度**（由 MCP Client 连接生命周期驱动，非用户设置）：`ONLINE`（已连接）/ `OFFLINE`（未连接/启动中/禁用）/ `ERROR`（重连失败） |
| `status_message` | 否 | 最近一次状态详情，便于前端展示（如 `connect timed out`）；`ONLINE` 时可清空 |
| `last_connected_at` | 否 | 最近 ONLINE 时间，运维参考 |

> **`enabled` 与 `status` 的区分**（决策 C2 细化）：`enabled` 是管理员意图（要不要连），`status` 是连接事实（连没连上）。`enabled=0` → 主动断连并置 `OFFLINE`；`enabled=1` 但连不上 → `ERROR`，其下工具降级不可见（D2）。二者解耦，避免「管理员禁用」与「网络抖动」混在同一字段。

#### 索引说明

| 索引 | 服务查询 |
|------|---------|
| `uk_ms_active_name` | 名称未删除唯一约束 |
| `idx_ms_enabled_deleted_created_id` | 工具列表页 MCP 分组（按启用状态筛选 + 创建时间倒序，OFFSET 分页） |
| `idx_ms_status_deleted_id` | 按 `status` 筛选（如「查看所有 ERROR 的 server」运维场景） |
| `idx_ms_created_at` | 通用时间排序 |

小表（≤ 数十条），列表用 OFFSET 分页（`10` §6.4 白名单）。

---

### 3.2 `tool` — 统一工具定义

```sql
CREATE TABLE `tool` (
    `id`              CHAR(36)     NOT NULL COMMENT '工具 ID',
    `created_at`      DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间，UTC',
    `updated_at`      DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间，UTC',
    `is_deleted`      TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除：0 正常，1 已删除',
    `created_by`      CHAR(36)     NULL     COMMENT '创建人用户ID，一期无登录可为空',
    `updated_by`      CHAR(36)     NULL     COMMENT '更新人用户ID，一期无登录可为空',

    `name`            VARCHAR(128) NOT NULL COMMENT '工具名（LLM 可见，全局未删除唯一；HTTP 用户起名，MCP 冲突时加 server 前缀 mcpServerName__toolName）',
    `description`     VARCHAR(512) NULL     COMMENT '工具描述（喂给 LLM 的工具说明，OpenAPI 导入时取 summary/description，超长截断）',
    `source_type`     VARCHAR(32)  NOT NULL COMMENT '工具来源：HTTP / MCP / WORKFLOW（WORKFLOW=Workflow-as-Tool，P4 启用）',
    `mcp_server_id`   CHAR(36)     NULL     COMMENT '来源 MCP Server ID（仅 source_type=MCP；HTTP/WORKFLOW 为 NULL）',
    `input_schema`    TEXT         NULL     COMMENT '工具输入参数 JSON Schema（字符串），定义时一次性生成入库，运行时原样透传给 LLM',

    `endpoint`        VARCHAR(512) NULL     COMMENT 'HTTP 工具完整请求地址（baseUrl+path 或模板，含 {param} 占位；仅 HTTP）',
    `method`          VARCHAR(16)  NULL     COMMENT 'HTTP 方法：GET/POST/PUT/DELETE/PATCH（仅 HTTP）',
    `config_json`     JSON         NULL     COMMENT 'HTTP 工具配置：params_mapping/headers_template/body_template（见 §4.2；仅 HTTP）',
    `auth_config`     VARCHAR(1024) NULL    COMMENT 'HTTP 工具鉴权凭据（加密 JSON 整体存储，见 §4.3；仅 HTTP，NONE 时为 NULL）',

    `timeout_seconds` INT          NULL     COMMENT '单次请求超时（秒），NULL 用全局默认 request-default=30s（13 §4.1）',
    `idempotent`      TINYINT      NOT NULL DEFAULT 0 COMMENT '是否幂等(0否1是)：HTTP 按 method 默认推断(GET/HEAD=1，POST/PUT/DELETE/PATCH=0)，MCP 默认0，用户可覆盖；驱动重试策略(13 §5)',
    `enabled`         TINYINT      NOT NULL DEFAULT 1 COMMENT '是否启用：0 禁用，1 启用；禁用后 listAvailableTools 不可见',

    `active_name`     VARCHAR(128)
        GENERATED ALWAYS AS (
            CASE WHEN `is_deleted` = 0 THEN `name` ELSE NULL END
        ) STORED COMMENT '未删除工具名唯一键辅助列',

    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_tool_active_name` (`active_name`),
    KEY `idx_tool_source_deleted_created_id`
        (`source_type`, `is_deleted`, `created_at` DESC, `id` DESC),
    KEY `idx_tool_mcpserver_deleted_id`
        (`mcp_server_id`, `is_deleted`, `id` DESC),
    KEY `idx_tool_created_at` (`created_at`)
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_0900_ai_ci
  COMMENT='统一工具定义';
```

#### 字段说明

| 字段 | 必填 | 说明 |
|------|------|------|
| `name` | 是 | **LLM 可见的工具名**，全局未删除唯一。HTTP 工具由用户起名（校验唯一）；MCP 工具取 remote tool name，若与已有未删除工具冲突则改为 `mcpServerName__toolName`（Zify 自实现前缀，因关闭了 spring-ai-mcp 的 prefix generator，决策 C1/D2）；OpenAPI 导入取 `operationId`，缺失时用 `method_path`，冲突加序号后缀 |
| `description` | 是（业务） | 喂给 LLM 的工具说明，直接影响 LLM 是否选用该工具。OpenAPI 导入取 `summary`，为空取 `description`，超 512 字符截断 |
| `source_type` | 是 | `HTTP`（手动配置/OpenAPI 导入）/ `MCP`（MCP Server 发现）/ `WORKFLOW`（P4 Workflow-as-Tool）。P2 只产生 `HTTP` 和 `MCP` |
| `mcp_server_id` | 否 | 仅 MCP 工具非空，指向来源 server。HTTP/WORKFLOW 为 NULL |
| `input_schema` | 是（业务） | 工具输入参数的 JSON Schema（字符串）。HTTP 手动配置由「可视化参数表单」生成；OpenAPI 由参数定义生成；MCP 取 `McpSchema.Tool.inputSchema()`。运行时**不重新生成**，原样透传给 model 模块转 Spring AI `ToolDefinition` |
| `endpoint` | 否 | 仅 HTTP。完整 URL，支持 `{param}` 路径占位（如 `https://api.example.com/users/{userId}`）。OpenAPI 导入时由 spec 的 server URL + path 拼接 |
| `method` | 否 | 仅 HTTP。OpenAPI 导入取 operation 的 method |
| `config_json` | 否 | 仅 HTTP。结构见 §4.2，含参数映射与 Header/Body 模板 |
| `auth_config` | 否 | 仅 HTTP。加密 JSON，结构见 §4.3 |
| `timeout_seconds` | 否 | 单次请求超时覆盖值（`13` §4.1）。NULL 用全局 `zify.tool.timeout.request-default=30s` |
| `idempotent` | 是 | 幂等标记。创建时按来源设默认（HTTP 按 method；MCP=0），用户可在表单覆盖。决定请求发出后失败是否重试（`13` §5.2） |
| `enabled` | 是 | 单工具开关。`0` → `listAvailableTools` 不返回（本轮 LLM 看不到，D2）；`agent_tool` 关联不自动删 |

> MCP 工具的字段约束：`mcp_server_id` 非空、`endpoint`/`method`/`config_json`/`auth_config` 为 NULL（这些由 server 定义，不由 Zify 配置）、`input_schema` 来自 server。MCP 工具仅可 `enabled` 切换与软删，不可编辑配置（配置变更走 server 重新发现）。

> `tool` 不设独立 `status` 字段：用 `enabled`(TINYINT) 表达启用/禁用，与 `model.enabled` 一致；可用性运行时由 `enabled + is_deleted + (source_type=HTTP OR mcp_server.status=ONLINE)` 联合判定（D2）。此为对 `10` §4.6「`tool.status`/`tool.tool_type`」约定的命名收敛——P2 用 `source_type` + `enabled`（决策 B2/D2 明示），**建议同步更新 `10` §4.6**。

#### 索引说明

| 索引 | 服务查询 |
|------|---------|
| `uk_tool_active_name` | 工具名未删除唯一约束（D2 命名冲突去重的 DB 兜底） |
| `idx_tool_source_deleted_created_id` | 工具列表按来源分组（HTTP/MCP/Workflow）+ 创建时间倒序，OFFSET 分页 |
| `idx_tool_mcpserver_deleted_id` | 「某 MCP Server 下发现的工具」（server 详情/展开列表） |
| `idx_tool_created_at` | 通用时间排序 |

小表，列表用 OFFSET 分页。

---

### 3.3 `tool_call_log` — 工具调用日志（大表）

```sql
CREATE TABLE `tool_call_log` (
    `id`                    CHAR(36)     NOT NULL COMMENT '日志 ID',
    `created_at`            DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '调用时间，UTC',
    `updated_at`            DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间，UTC',
    `is_deleted`            TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除：0 正常，1 已删除',

    `tool_id`               CHAR(36)     NOT NULL COMMENT '调用的工具 ID',
    `tool_name`             VARCHAR(128) NOT NULL COMMENT '工具名快照（工具可能被改名/删除，快照保证日志可读）',
    `source_type`           VARCHAR(32)  NOT NULL COMMENT '工具来源快照：HTTP / MCP / WORKFLOW',
    `mcp_server_id`         CHAR(36)     NULL     COMMENT 'MCP Server ID（仅 MCP 工具）',
    `agent_id`              CHAR(36)     NULL     COMMENT '触发调用的 Agent ID（对话场景；手动测试/工作流为 NULL）',
    `conversation_id`       CHAR(36)     NULL     COMMENT '触发调用的会话 ID（对话场景；手动测试/工作流为 NULL）',
    `workflow_run_id`       CHAR(36)     NULL     COMMENT '工作流运行 ID（P4 工作流场景；P2 恒 NULL）',
    `workflow_node_run_id`  CHAR(36)     NULL     COMMENT '工作流节点运行 ID（P4；P2 恒 NULL）',
    `turn`                  INT          NULL     COMMENT '对话 ReAct 循环轮次（1 起始，本轮第几次工具决策；手动测试/工作流为 NULL）',
    `tool_call_id`          VARCHAR(128) NULL     COMMENT '模型生成的本次调用 ID，关联 message(TOOL) 的 metadata.toolCallId（对话场景）',
    `input`                 JSON         NULL     COMMENT '调用入参（args 的 JSON，截断后；见 §4.5）',
    `output`                LONGTEXT     NULL     COMMENT '调用结果 output（截断后；见 §4.5）',
    `status`                VARCHAR(32)  NOT NULL COMMENT '执行状态：SUCCESS / ERROR / TIMEOUT / CIRCUIT_OPEN / CANCELLED',
    `duration_ms`           INT          NOT NULL DEFAULT 0 COMMENT '执行耗时（毫秒）',
    `error`                 TEXT         NULL     COMMENT '失败时的精简错误信息（成功为 NULL；不含鉴权凭据）',

    PRIMARY KEY (`id`),
    KEY `idx_tcl_conv_deleted_created_id`
        (`conversation_id`, `is_deleted`, `created_at` DESC, `id` DESC),
    KEY `idx_tcl_agent_deleted_created_id`
        (`agent_id`, `is_deleted`, `created_at` DESC, `id` DESC),
    KEY `idx_tcl_tool_deleted_created_id`
        (`tool_id`, `is_deleted`, `created_at` DESC, `id` DESC),
    KEY `idx_tcl_created_at` (`created_at`)
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_0900_ai_ci
  COMMENT='工具调用日志';
```

#### 字段说明（对齐决策 D1）

| 字段 | 必填 | 说明 |
|------|------|------|
| `tool_id` / `tool_name` / `source_type` / `mcp_server_id` | 是/是/是/否 | 工具快照信息。工具可能被改名/禁用/删除，日志存快照保证历史可读 |
| `agent_id` / `conversation_id` / `turn` | 否 | 对话场景的审计上下文（由 `ToolExecContext` 传入，决策 B2）；手动测试与工作流场景为 NULL |
| `workflow_run_id` / `workflow_node_run_id` | 否 | P4 工作流场景；P2 恒 NULL（列先建好，避免 P4 加列迁移） |
| `tool_call_id` | 否 | 模型生成的调用 ID，与 `message(TOOL).metadata.toolCallId` 对应，实现「TOOL 消息 ↔ 日志」双向关联（A2 决策 5）。手动测试/工作流为 NULL |
| `input` | 否 | 调用入参 JSON（LLM 填的 args）。**存截断后内容**（C7 32KB 阈值，见 §4.5） |
| `output` | 否 | 调用结果 output 文本。LONGTEXT，**存截断后内容**（C7 32KB 阈值）。回灌模型/存 message(TOOL) 用同一份截断内容 |
| `status` | 是 | `SUCCESS`（成功）/ `ERROR`（失败：4xx/非幂等执行失败/重试耗尽，B1 §6.2）/ `TIMEOUT`（总 deadline 超时）/ `CIRCUIT_OPEN`（该 tool_id 熔断中，B1 §6.1）/ `CANCELLED`（用户中断，C4） |
| `duration_ms` | 是 | 执行耗时，含重试总耗时 |
| `error` | 否 | 失败时的精简错误描述（成功为 NULL）。**不含鉴权凭据**（B1 §7 异常不能含凭据） |

`tool_call_log` 不设 `created_by`/`updated_by`：大表规则（`10` §2.2），可通过 `conversation_id → agent_id → agent.created_by` 或 `tool_id → tool.created_by` 追溯。

#### 索引说明（大表，遵循 `10` §5.3 模板 + D1）

| 索引 | 服务查询 |
|------|---------|
| `idx_tcl_conv_deleted_created_id` | 某会话的工具调用日志（对话页工具卡片下钻列表），Keyset 分页 |
| `idx_tcl_agent_deleted_created_id` | 某 Agent 的调用日志（Agent 调试视角） |
| `idx_tcl_tool_deleted_created_id` | 某工具的调用日志（工具详情页日志 Tab） |
| `idx_tcl_created_at` | 历史数据清理（按 `created_at` 分批物理删除，保留 90 天） |

> **P4 扩展**：`workflow_run_id` 列已建，P4 工作流落地时追加 `idx_tcl_workflowrun_deleted_created_id`（P2 该列恒 NULL，不预建索引避免全 NULL 索引浪费）。

---

### 3.4 `agent_tool` — Agent ↔ 工具关联表

```sql
CREATE TABLE `agent_tool` (
    `id`          CHAR(36)     NOT NULL COMMENT '主键',
    `created_at`  DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间，UTC',
    `updated_at`  DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间，UTC',
    `is_deleted`  TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除',

    `agent_id`    CHAR(36)     NOT NULL COMMENT 'Agent ID',
    `tool_id`     CHAR(36)     NOT NULL COMMENT '工具 ID',

    `active_agent_tool` VARCHAR(73)
        GENERATED ALWAYS AS (
            CASE WHEN `is_deleted` = 0 THEN CONCAT(`agent_id`, '#', `tool_id`) ELSE NULL END
        ) STORED COMMENT '未删除关联唯一键辅助列',

    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_at_active_agent_tool` (`active_agent_tool`),
    KEY `idx_at_agent_deleted_created_id`
        (`agent_id`, `is_deleted`, `created_at` DESC, `id` DESC),
    KEY `idx_at_tool_deleted_created_id`
        (`tool_id`, `is_deleted`, `created_at` DESC, `id` DESC)
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_0900_ai_ci
  COMMENT='Agent 与工具关联';
```

> 结构完全对齐 `10` §3.4 关联表模板。`agent_tool` 归 **agent 模块**（agent 拥有 Agent 资源）。关联表不设 `enabled`：单工具可用性由 `tool.enabled` + 来源可用性决定，绑定本身只有「绑/不绑」（D2）。`tool_id` 删除/禁用时，关联**不自动删**（保留绑定），仅运行时 `listAvailableTools` 过滤。

---

### 3.5 `message` 表扩展（不改列，扩 `metadata` JSON）

P1 `message` 表（`docs-prd/phase-P1/01-data-model.md` §3.3）的 `role` 已预留 `TOOL`，**P2 不新增任何列**，仅扩展 `metadata` JSON 结构以承载工具调用过程（决策 A2 决定 2）。

#### `metadata` 按 role 的结构

| role | content | metadata |
|------|---------|----------|
| `USER` | 用户消息正文 | `NULL`（同 P1） |
| `ASSISTANT`（中间轮，带 toolCall） | 本轮 LLM 产出的文本（可为空，模型可能只调工具不输出文本） | `{ "toolCalls": [{ "id", "name", "args"(JSON 字符串) }] }`（见 §4.6） |
| `TOOL`（新增实际使用） | 工具结果 output（截断后，与 tool_call_log.output 一致） | `{ "toolCallId", "toolName", "toolCallLogId" }`（见 §4.6） |
| `ASSISTANT`（最终轮，纯文本回复） | 最终回复正文 | `{ "modelId", "modelName", "providerType", "promptTokens", "completionTokens", "totalTokens", "finishReason", "durationMs" }`（同 P1，`finishReason` 取值集扩展，见下） |

`finishReason` 取值扩展（最终 ASSISTANT 消息）：

| 值 | 含义 | 来源 |
|----|------|------|
| `STOP` | 模型正常结束（无 tool call） | Provider |
| `LENGTH` | 达到 max_tokens | Provider |
| `MAX_TURNS` | 达到 ReAct 最大轮次（C4，正常截断不报错） | engine |
| `TIMEOUT` | 整轮循环 deadline 超时（C4） | engine |
| `CANCELLED` | 用户中断/SSE 断连（C4，部分落库） | engine |

> 中间轮 ASSISTANT（带 toolCall）的 metadata **不写** `finishReason` 等运行元数据（它不是「最终回复」）；其 `toolCalls` 是模型本轮决策的工具调用请求。最终轮 ASSISTANT 的 metadata 不含 `toolCalls`。

---

## 四、JSON 字段约定

### 4.1 `tool.input_schema`（工具输入参数 JSON Schema）

字符串形式存储的 JSON Schema，原样透传给 model 模块转 Spring AI `ToolDefinition.inputSchema()`。

```jsonc
// HTTP 手动配置（可视化参数表单生成）：查用户信息
{
  "type": "object",
  "properties": {
    "userId": { "type": "string", "description": "用户ID" },
    "fields": { "type": "string", "description": "返回字段，逗号分隔" }
  },
  "required": ["userId"]
}

// OpenAPI 导入（由 operation 参数定义生成）：同构
{ "type": "object", "properties": { ... }, "required": [ ... ] }

// MCP 工具：取 McpSchema.Tool.inputSchema() 原样存
{ "type": "object", "properties": { ... } }
```

规则（对齐 `10` §5.6）：

- 只用于「定义工具时生成一次入库」+「运行时透传给 LLM」，不参与业务过滤/排序。
- 由 Java DTO（参数表单 / Swagger Parser / MCP schema）生成并校验，数据库不约束内部结构。

### 4.2 `tool.config_json`（HTTP 工具配置）

仅 `source_type=HTTP` 非空。承载参数映射与请求模板。

```jsonc
{
  "paramsMapping": [
    { "name": "userId", "in": "path",   "required": true },
    { "name": "fields", "in": "query",  "required": false },
    { "name": "X-Trace-Id", "in": "header", "required": false, "secret": false }
  ],
  "headersTemplate": [
    { "name": "Content-Type", "value": "application/json", "secret": false },
    { "name": "X-Api-Key",    "value": "{{auth.apiKey}}",   "secret": true }
  ],
  "bodyTemplate": "{ \"query\": \"{{args.query}}\" }"
}
```

| 子字段 | 说明 |
|--------|------|
| `paramsMapping` | LLM 填的 args 如何映射到请求：`in` = `path`（填 URL `{param}` 占位）/ `query`（拼 query string）/ `header`（设请求头）/ `body`（进 body）。`secret=true` 的 Header 其值在执行时从 `auth_config` 解密注入，不存明文 |
| `headersTemplate` | 固定/模板请求头。`secret=true` 者值存 `{{auth.xxx}}` 占位，运行时从 `auth_config` 解密替换；非 secret 者存明文 |
| `bodyTemplate` | 请求体模板（POST/PUT/PATCH），支持 `{{args.paramName}}` / `{{auth.xxx}}` 占位替换；GET/DELETE 为 null |

规则：

- OpenAPI 导入时，`paramsMapping` 由 operation 的参数（`in` 字段）生成；`headersTemplate` 含 `Content-Type` 默认值 + 鉴权 Header；`bodyTemplate` 由 requestBody schema 生成最小模板。
- `config_json` 列表接口不返回（大字段规则，`10` §5.4），详情接口按主键返回。

### 4.3 `tool.auth_config`（HTTP 工具鉴权，加密 JSON）

整个对象加密后存 `auth_config`（复用 `common.SecretEncryptor`，与 model_provider.api_key 同机制）。明文仅执行时解密，不记录、不返回（对齐 §6）。

```jsonc
// 明文结构（入库前整体 AES 加密）
{ "type": "API_KEY", "headerName": "X-Api-Key", "apiKey": "sk-xxxxxx" }
// 或
{ "type": "BEARER", "token": "sk-xxxxxx" }
// 或（NONE）
null
```

接口返回时只给 `authType`（明文，从 tool 表无——故 authType 冗余存 `config_json` 或单独返回 `hasAuth: true/false`，见 `02-functional-spec.md`）。**密文不返回**，前端用占位符 `••••••` 表示已配置。

### 4.4 `mcp_server.auth_config`（MCP Server 认证，加密 JSON）

同 §4.3 机制，整对象加密存储。`auth_type`（明文列）决定结构：

```jsonc
// auth_type=API_KEY
{ "headerName": "x-api-key", "apiKey": "sk-xxxxxx" }
// auth_type=BEARER
{ "token": "sk-xxxxxx" }
// auth_type=NONE → auth_config=NULL
```

### 4.5 `tool_call_log.input` / `output`（截断存储）

| 字段 | 类型 | 截断阈值 | 说明 |
|------|------|---------|------|
| `input` | JSON | 32KB（`zify.tool.security.response-max-bytes`） | LLM 填的 args 的 JSON 序列化；超阈值截断并在末尾追加 `...[truncated]` |
| `output` | LONGTEXT | 32KB | 工具返回结果文本（C7 响应截断后）；超阈值截断并标记 |

规则（C7 + D1）：

- 截断在 tool 模块 `ToolFacade.executeTool` 内部完成（执行点即截断点即记录点），**回灌模型 / 存 message(TOOL) / 存 tool_call_log 三处用同一份截断内容**，保证一致。
- 列表接口禁止 `SELECT *`，`input`/`output` 只在详情按主键返回（`10` §5.4）。

### 4.6 `message.metadata` 扩展结构（仅 ASSISTANT-toolCall / TOOL）

```jsonc
// ASSISTANT（中间轮，带工具调用请求）
{
  "toolCalls": [
    { "id": "call_abc", "name": "get_weather", "args": "{\"city\":\"北京\"}" }
  ]
}

// TOOL（工具结果）
{
  "toolCallId": "call_abc",
  "toolName": "get_weather",
  "toolCallLogId": "tcl-uuid"
}
```

| 字段 | 说明 |
|------|------|
| `toolCalls[].id` | 模型生成的调用 ID（Provider 返回），与 TOOL 消息的 `toolCallId` 配对 |
| `toolCalls[].name` | 工具名快照 |
| `toolCalls[].args` | 入参 JSON 字符串（与 tool_call_log.input 同源，已截断） |
| `toolCallId` | 对应的 ASSISTANT toolCall 的 id（配对键） |
| `toolName` | 工具名快照 |
| `toolCallLogId` | 指向 `tool_call_log.id`，前端工具卡片可下钻完整日志（A2 决策 5） |

规则：结构由 Java DTO 校验，数据库不约束 JSON 内部结构（`10` §5.6）；列表/历史接口随 `content` 一并返回（消息流渲染必需）。

---

## 五、查询场景与索引命中

| 查询场景 | 命中索引 | 说明 |
|---------|---------|------|
| MCP Server 列表（按启用状态） | `idx_ms_enabled_deleted_created_id` | 小表 OFFSET |
| MCP Server 按 status 筛选（运维） | `idx_ms_status_deleted_id` | 查所有 ERROR |
| 某 MCP Server 下发现的工具 | `idx_tool_mcpserver_deleted_id` | server 详情/展开 |
| 工具列表（按来源分组） | `idx_tool_source_deleted_created_id` | 小表 OFFSET |
| 按 ID 查工具/Server | PRIMARY KEY | 详情/编辑 |
| 某 Agent 绑定的工具 | `idx_at_agent_deleted_created_id` | engine 取绑定（经 AgentFacade） |
| 某工具被多少 Agent 绑定 | `idx_at_tool_deleted_created_id` | 删除工具时的提示校验 |
| 某会话的工具调用日志（下钻） | `idx_tcl_conv_deleted_created_id` | 大表 Keyset |
| 某工具的调用日志 | `idx_tcl_tool_deleted_created_id` | 大表 Keyset |
| 某 Agent 的调用日志 | `idx_tcl_agent_deleted_created_id` | 大表 Keyset |
| 工具调用日志清理 | `idx_tcl_created_at` | 按 created_at 分批物理删除 |

---

## 六、分页方案

| 表 | 方式 | 排序键 | 游标/页码 |
|----|------|--------|----------|
| `mcp_server` | OFFSET（小表） | `created_at DESC, id DESC` | `page` / `pageSize` |
| `tool` | OFFSET（小表） | `created_at DESC, id DESC` | `page` / `pageSize` |
| `agent_tool` | —（按父对象查全量，不分页） | `created_at` | — |
| `tool_call_log` | Keyset（大表） | `created_at DESC, id DESC` | opaque `cursor` |

`tool_call_log` 游标编解码沿用 P1 方案（Controller 层 `Base64Url(ISO-8601(created_at) + "#" + id)`，见 `docs-prd/phase-P1/01-data-model.md` §六）。`mcp_server`/`tool` 在 `10` §6.4 OFFSET 白名单中。

---

## 七、迁移方案

迁移脚本位于 `zify-app/src/main/resources/db/migration/`，沿用 `V{n}__{module}__{desc}.sql`（P1 已用至 V5）。

```text
V6__tool__create_tool_tables.sql     # 建 mcp_server、tool、tool_call_log（一张表一个建表语句，建表即含索引）
V7__agent__create_agent_tool.sql     # 建 agent_tool
```

> `00-technical-decisions.md` §9.3 原写 `V6__tool__create_tool_tables.sql`（含 mcp_server/tool/tool_call_log）+ `V7__agent__create_agent_tool.sql`，本设计沿用。一张表一个 `CREATE TABLE`，建表即含索引（`10` §1.3）。不创建物理外键。

`message` 表无结构变更（仅 `metadata` JSON 内容扩展），**无需迁移脚本**。

---

## 八、与 `glm-docs/11-zify-core-data-model.md` 的对照

| 维度 | doc 11 原设计 | P2 落地 | 说明 |
|------|--------------|---------|------|
| `mcp_server`(3) | 已规划 | 本文档定稿字段 | 补 `name`/`base_url`/`transport_type`/`auth_type`/`auth_config`/`enabled`/`status`/`status_message`/`last_connected_at`/`active_name` |
| `tool`(4) | 已规划 | 本文档定稿字段 | 补统一配置（`source_type`/`mcp_server_id`/`input_schema`/HTTP 配置列/`timeout_seconds`/`idempotent`/`enabled`/`active_name`） |
| `tool_call_log`(19) 归属 | 原归 engine | **改归 tool 模块**（A2 决策 1，已回写 doc 11） | 执行点即记录点；对话/工作流/手动测试都经 `ToolFacade` |
| `tool_call_log` 字段 | 概要 | 本文档定稿 17 业务列 | 含 P4 预留 `workflow_run_id`/`workflow_node_run_id`（P2 恒 NULL） |
| `agent_tool`(6) | 已规划 | 本文档定稿 | 对齐 `10` §3.4 关联表模板 |
| 关系 `message(TOOL) 0..1:1 tool_call_log` | 原写 `message 1:N tool_call_log` | 改为 `message(TOOL) 0..1:1 tool_call_log`（经 `metadata.toolCallLogId`，A2 决策 5，已回写 doc 11） | 仅 TOOL 消息关联日志 |
| `message` 表 | P1 已建 | 不改列，扩 `metadata` JSON（A2 决定 2） | role=TOOL 实际启用 |

> **需回写/已回写的正式文档**（对齐 `00-technical-decisions.md` §十三）：`glm-docs/11`（tool_call_log 归属、message↔log 关系）✅ 已回写；`10` §4.6（`tool.status`/`tool_type` → `source_type`/`enabled` 命名收敛）⏳ 待落实；`docs-prd/phase-P1/01` §四 `message.metadata` 结构扩展 ⏳ 待落实（P2 编码时一并改）。
