# Zify 数据库规范

> Zify 使用 MySQL 8.x 存业务数据，PostgreSQL + pgvector 存知识库 chunk 和 embedding。
> 本规范覆盖索引设计原则、大表预判和应对策略、分页查询注意事项、通用字段约定。
> 所有规则要求 AI 建表时直接遵守。

---

## 一、全局规则

### 1.1 数据库分工

| 数据库 | 用途 |
|---|---|
| MySQL 8.x | Agent、对话、工作流、工具、触发器、模型配置、运行日志、文件元数据 |
| PostgreSQL + pgvector | 知识库 chunk、embedding、向量检索 |

### 1.2 命名规则

| 对象 | 规则 | 示例 |
|---|---|---|
| 表名 | 小写下划线，使用业务单数或日志语义名 | `agent`、`message`、`workflow_run` |
| 字段名 | 小写下划线 | `conversation_id`、`created_at` |
| 主键字段 | 固定为 `id` | `id` |
| 外键引用字段 | `{目标表名}_id` | `agent_id`、`workflow_id` |
| 普通索引 | `idx_{表名简称}_{字段名}` | `idx_msg_conv_created_id` |
| 唯一索引 | `uk_{表名简称}_{字段名}` | `uk_agent_active_name` |

### 1.3 迁移规则

- 所有表结构变更必须通过 Flyway 或 Liquibase 脚本管理。
- 禁止手工进数据库改表。
- 建表脚本必须同时包含索引。
- 新增字段必须明确是否允许 NULL、默认值、注释。

---

## 二、MySQL 业务表通用字段

### 2.1 必须字段

每张 MySQL 业务表必须包含以下字段。这里的业务表包括资源表、配置表、运行记录表、日志表；如后续确实需要纯关联表或临时表不使用该模板，建表说明必须写明原因。

```sql
`id`          CHAR(36)     NOT NULL COMMENT '主键，应用生成 UUID',
`created_at`  DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间，UTC',
`updated_at`  DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间，UTC',
`is_deleted`  TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除：0 正常，1 已删除',

PRIMARY KEY (`id`)
```

规则：

- 主键统一使用 `CHAR(36)`，不使用 `VARCHAR(36)`。
- UUID 由应用生成，数据库不负责生成 ID。
- 所有时间字段按 UTC 写入。
- `created_at`、`updated_at` 精度固定为毫秒：`DATETIME(3)`。
- 所有查询默认带 `is_deleted = 0`，除非明确查询回收站或清理数据。
- 日志表也保留 `is_deleted`，但历史日志清理仍按 `created_at` 分批物理删除。
- 禁止给 `is_deleted` 单独建索引，但可以放入联合索引。

### 2.2 可选用户字段

用户创建的资源表必须预留用户字段：

```sql
`created_by` CHAR(36) NULL COMMENT '创建人用户ID，一期无登录时可为空',
`updated_by` CHAR(36) NULL COMMENT '更新人用户ID，一期无登录时可为空'
```

必须包含用户字段的表：

- `agent`
- `workflow`
- `knowledge`
- `tool`
- `model_provider`
- `conversation`

日志表可以不加 `created_by` / `updated_by`，但必须能通过父对象追溯用户。

### 2.3 NULL 和 DEFAULT 规则

| 字段类型 | 规则 |
|---|---|
| 必填业务字段 | `NOT NULL`，必须有真实业务默认值才允许 `DEFAULT` |
| 可选业务字段 | 允许 `NULL`，禁止用空字符串或 0 表示未知 |
| TEXT / LONGTEXT | 不设置默认值 |
| JSON | 默认允许 `NULL`；需要空对象时由应用写入 `{}` |
| 状态字段 | `VARCHAR(32) NOT NULL DEFAULT '{初始状态}'` |
| 数量字段 | `INT NOT NULL DEFAULT 0` |
| 金额 / 比例 | `DECIMAL`，禁止 FLOAT / DOUBLE |

禁止：

- 禁止用 `''` 表示未知字符串。
- 禁止用 `0` 表示未知外键。
- 禁止所有字段无脑 `NOT NULL DEFAULT ''`。

### 2.4 MySQL 建库语句

```sql
CREATE DATABASE zify
DEFAULT CHARACTER SET utf8mb4
DEFAULT COLLATE utf8mb4_0900_ai_ci;
```

---

## 三、软删除与唯一约束

### 3.1 查询规则

业务查询必须过滤未删除数据：

```sql
WHERE is_deleted = 0
```

### 3.2 禁止的唯一索引

禁止使用下面这种唯一索引：

```sql
-- 禁止：删除后的同名记录只能存在一条
UNIQUE KEY uk_agent_name_deleted (`name`, `is_deleted`)
```

原因：多条已删除记录的 `is_deleted` 都等于 1，会互相冲突。

### 3.3 未删除数据唯一的写法

如果业务要求“未删除数据唯一”，使用 generated column：

```sql
`active_name` VARCHAR(128)
    GENERATED ALWAYS AS (
        CASE WHEN `is_deleted` = 0 THEN `name` ELSE NULL END
    ) STORED COMMENT '未删除数据唯一键辅助列',

UNIQUE KEY `uk_agent_active_name` (`active_name`)
```

规则：

- generated column 只用于唯一约束，不在业务代码中读写。
- 已删除数据的 generated column 为 NULL，MySQL 允许多个 NULL，因此不会冲突。
- 复合唯一约束使用一个 generated column 拼接业务字段；拼接后的字段长度必须覆盖所有参与字段和分隔符：

```sql
`active_provider_model` VARCHAR(255)
    GENERATED ALWAYS AS (
        CASE
            WHEN `is_deleted` = 0 THEN CONCAT(`provider_type`, '#', `model_name`)
            ELSE NULL
        END
    ) STORED,

UNIQUE KEY `uk_model_active_provider_model` (`active_provider_model`)
```

---

## 四、MySQL 索引设计原则

### 4.1 必须建索引的字段

| 场景 | 必须索引 |
|---|---|
| 外键引用字段 | `{target}_id` |
| 高频等值过滤 | `status`、`type`、`provider_type` 等 |
| 大表时间清理 | `created_at` |
| Keyset 分页 | `created_at, id` |
| 软删除过滤 | 放入联合索引，不建单列 |
| 唯一约束 | 使用 `uk_` 前缀 |

### 4.2 联合索引顺序

联合索引字段按以下顺序排列：

1. 高选择性等值字段，例如 `conversation_id`、`workflow_id`。
2. `is_deleted`。
3. 其他等值字段，例如 `status`。
4. 范围和排序字段，例如 `created_at`。
5. 稳定排序字段 `id`。

如果查询没有父对象 ID，只按 `status`、`type` 等字段筛选，则该等值字段放在 `is_deleted` 前面，例如 `(status, is_deleted, created_at DESC, id DESC)`。

示例：

```sql
-- 查询某会话消息，按时间倒序翻页
WHERE conversation_id = ?
  AND is_deleted = 0
  AND (created_at < ? OR (created_at = ? AND id < ?))
ORDER BY created_at DESC, id DESC
LIMIT 20

-- 对应索引
KEY `idx_msg_conv_deleted_created_id`
    (`conversation_id`, `is_deleted`, `created_at` DESC, `id` DESC)
```

### 4.3 禁止事项

| 禁止 | 原因 |
|---|---|
| 禁止 `SELECT *` | 大字段和无关字段会增加 IO |
| 禁止给 `is_deleted` 建单列索引 | 低基数字段，单列选择性差 |
| 禁止冗余索引 | 已有 `(a,b)` 时通常不需要 `(a)` |
| 禁止单表超过 8 个索引 | 写入成本过高；超过时必须说明理由 |
| 禁止在 JSON 字段上直接建普通索引 | 需要查询 JSON 内部字段时使用 generated column |
| 禁止在大 TEXT 字段上建普通索引 | 需要搜索时后续单独设计全文检索 |

### 4.4 外键约定

一期不创建数据库物理外键。

规则：

- 所有关联字段仍按外键命名，例如 `agent_id`、`conversation_id`。
- 所有关联字段必须建索引。
- 关联完整性由 Service 层校验。
- 删除父对象时，子对象通过业务逻辑处理，不依赖数据库级 cascade。

---

## 五、大表预判和应对策略

### 5.1 大表清单

| 表 | 增长来源 | 半年预估 | 判定 |
|---|---|---:|---|
| `message` | 用户对话消息 | 20-50 万 | 中等 |
| `tool_call_log` | Agent / 工作流工具调用 | 30-100 万 | 偏大 |
| `workflow_run` | 工作流运行记录 | 10-50 万 | 中等 |
| `workflow_node_run` | 工作流节点运行记录 | 30-200 万 | 偏大 |
| `trigger_log` | Webhook / Cron 触发记录 | 10-100 万 | 偏大 |
| `document_parse_log` | 文档解析记录 | 1-10 万 | 中等 |
| `document_chunk` | 文档分块 | 5-50 万 | PostgreSQL / pgvector |

小表：

- `agent`
- `workflow`
- `knowledge`
- `tool`
- `model_provider`

小表可以使用 OFFSET 分页，但仍要限制 `LIMIT`。

### 5.2 大表统一规则

所有大表必须做到：

- 按主要父对象 ID 建联合分页索引。
- 单独建 `created_at` 索引用于清理。
- 父对象分页索引和状态分页索引必须包含 `is_deleted`。
- 列表接口只返回轻量字段。
- 大字段只在详情接口按主键查询。
- 必须有保留策略。
- 删除历史数据时必须分批删除。

### 5.3 必备索引模板

#### message

```sql
KEY `idx_msg_conv_deleted_created_id`
    (`conversation_id`, `is_deleted`, `created_at` DESC, `id` DESC),

KEY `idx_msg_created_at`
    (`created_at`)
```

#### tool_call_log

```sql
KEY `idx_tcl_tool_deleted_created_id`
    (`tool_id`, `is_deleted`, `created_at` DESC, `id` DESC),

KEY `idx_tcl_conv_deleted_created_id`
    (`conversation_id`, `is_deleted`, `created_at` DESC, `id` DESC),

KEY `idx_tcl_created_at`
    (`created_at`)
```

#### workflow_run

```sql
KEY `idx_wr_workflow_deleted_created_id`
    (`workflow_id`, `is_deleted`, `created_at` DESC, `id` DESC),

KEY `idx_wr_status_deleted_created_id`
    (`status`, `is_deleted`, `created_at` DESC, `id` DESC),

KEY `idx_wr_created_at`
    (`created_at`)
```

#### workflow_node_run

```sql
KEY `idx_wnr_run_deleted_node`
    (`workflow_run_id`, `is_deleted`, `node_id`),

KEY `idx_wnr_run_deleted_created_id`
    (`workflow_run_id`, `is_deleted`, `created_at` DESC, `id` DESC),

KEY `idx_wnr_created_at`
    (`created_at`)
```

#### trigger_log

```sql
KEY `idx_tl_trigger_deleted_created_id`
    (`trigger_id`, `is_deleted`, `created_at` DESC, `id` DESC),

KEY `idx_tl_status_deleted_created_id`
    (`status`, `is_deleted`, `created_at` DESC, `id` DESC),

KEY `idx_tl_created_at`
    (`created_at`)
```

#### document_parse_log

```sql
KEY `idx_dpl_document_deleted_created_id`
    (`document_id`, `is_deleted`, `created_at` DESC, `id` DESC),

KEY `idx_dpl_status_deleted_created_id`
    (`status`, `is_deleted`, `created_at` DESC, `id` DESC),

KEY `idx_dpl_created_at`
    (`created_at`)
```

### 5.4 大字段规则

| 大字段 | 存储 | 列表接口 |
|---|---|---|
| `message.content` | `LONGTEXT` 或 `TEXT` | 只返回摘要 |
| `agent.system_prompt` | `LONGTEXT` | Agent 列表不返回 |
| `workflow.dsl_json` | `JSON` | 工作流列表不返回 |
| `workflow_node_run.input_json` | `JSON` | 运行列表不返回 |
| `workflow_node_run.output_json` | `JSON` | 运行列表不返回 |
| `tool_call_log.request_json` | `JSON` | 日志列表不返回 |
| `tool_call_log.response_json` | `JSON` | 日志列表不返回 |

### 5.5 数据清理规则

删除历史日志必须分批执行：

```sql
DELETE FROM trigger_log
WHERE created_at < DATE_SUB(UTC_TIMESTAMP(), INTERVAL 90 DAY)
ORDER BY created_at
LIMIT 1000;
```

规则：

- 每批最多删除 1000 条。
- 每批之间 sleep 100-500ms。
- 清理 SQL 必须命中 `created_at` 索引。
- 清理任务必须避开备份时间窗口。

建议保留时间：

| 表 | 保留 |
|---|---:|
| `message` | 180 天或用户主动删除 |
| `tool_call_log` | 90 天 |
| `workflow_run` | 90 天 |
| `workflow_node_run` | 90 天 |
| `trigger_log` | 90 天 |
| `document_parse_log` | 30 天 |

---

## 六、分页查询规范

### 6.1 大表禁止 OFFSET

大表禁止使用 OFFSET：

```sql
-- 禁止
SELECT id, content, created_at
FROM message
WHERE conversation_id = ?
  AND is_deleted = 0
ORDER BY created_at DESC
LIMIT 20 OFFSET 100000;
```

### 6.2 Keyset 分页模板

第一页：

```sql
SELECT id, content, created_at
FROM message
WHERE conversation_id = ?
  AND is_deleted = 0
ORDER BY created_at DESC, id DESC
LIMIT 20;
```

下一页：

```sql
SELECT id, content, created_at
FROM message
WHERE conversation_id = ?
  AND is_deleted = 0
  AND (
      created_at < ?
      OR (created_at = ? AND id < ?)
  )
ORDER BY created_at DESC, id DESC
LIMIT 20;
```

对应索引：

```sql
KEY `idx_msg_conv_deleted_created_id`
    (`conversation_id`, `is_deleted`, `created_at` DESC, `id` DESC)
```

### 6.3 游标字段

前端传：

```json
{
  "cursor_created_at": "2026-06-03T12:00:00.123Z",
  "cursor_id": "uuid",
  "limit": 20
}
```

规则：

- 默认每页 20 条。
- 最大每页 100 条。
- 大表不返回总页数。
- 前端使用“加载更多”，不使用页码跳转。
- 排序必须固定为 `created_at DESC, id DESC`。
- 游标必须同时包含 `cursor_created_at` 和 `cursor_id`，禁止只用时间字段翻页。

### 6.4 允许 OFFSET 的场景

只有同时满足以下条件才允许 OFFSET：

- 表数据量小于 1 万。
- 表不会快速增长。
- 页面确实需要页码跳转。

允许使用 OFFSET 的表：

- `agent`
- `workflow`
- `knowledge`
- `tool`
- `model_provider`

即使使用 OFFSET，也必须限制：

```sql
LIMIT <= 100
```

---

## 七、PostgreSQL + pgvector 规范

### 7.1 建库和扩展

```sql
CREATE DATABASE zify_vector WITH ENCODING 'UTF8';

-- 连接到 zify_vector 数据库后执行
CREATE EXTENSION IF NOT EXISTS vector;
```

注意：

- PostgreSQL 使用 `UTF8`，不是 MySQL 的 `utf8mb4`。
- pgvector extension 初始化必须通过迁移脚本自动执行。

### 7.2 document_chunk 表结构

```sql
CREATE TABLE document_chunk (
    id            VARCHAR(36)  NOT NULL,
    knowledge_id  VARCHAR(36)  NOT NULL,
    document_id   VARCHAR(36)  NOT NULL,
    chunk_index   INTEGER      NOT NULL,
    content       TEXT         NOT NULL,
    content_hash  VARCHAR(64)  NOT NULL,
    metadata      JSONB        NOT NULL DEFAULT '{}'::jsonb,
    embedding     VECTOR(1536) NOT NULL,
    enabled       SMALLINT     NOT NULL DEFAULT 1,
    is_deleted    SMALLINT     NOT NULL DEFAULT 0,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMPTZ  NOT NULL DEFAULT NOW(),

    PRIMARY KEY (id)
);
```

规则：

- `VECTOR(1536)` 维度必须和实际 Embedding 模型一致。
- 如果更换 Embedding 维度，必须新建表或迁移表结构，不能混写不同维度。
- `metadata` 使用 `JSONB`。
- PostgreSQL 时间使用 `TIMESTAMPTZ`。
- PostgreSQL 没有 MySQL `ON UPDATE` 语法，更新 chunk 时应用层必须同时写入 `updated_at = NOW()`。

### 7.3 pgvector 必备索引

```sql
-- 向量检索索引
CREATE INDEX idx_doc_chunk_embedding
ON document_chunk
USING hnsw (embedding vector_cosine_ops)
WITH (m = 16, ef_construction = 64);

-- 知识库过滤索引
CREATE INDEX idx_doc_chunk_knowledge_enabled
ON document_chunk (knowledge_id, enabled, is_deleted);

-- 文档内 chunk 顺序
CREATE UNIQUE INDEX uk_doc_chunk_active_document_index
ON document_chunk (document_id, chunk_index)
WHERE is_deleted = 0;

-- 清理和排序
CREATE INDEX idx_doc_chunk_created_at
ON document_chunk (created_at);
```

### 7.4 向量检索 SQL 模板

单知识库：

```sql
SELECT
    id,
    document_id,
    chunk_index,
    content,
    1 - (embedding <=> $1) AS similarity
FROM document_chunk
WHERE knowledge_id = $2
  AND enabled = 1
  AND is_deleted = 0
ORDER BY embedding <=> $1
LIMIT $3;
```

多个知识库：

```sql
SELECT
    id,
    knowledge_id,
    document_id,
    chunk_index,
    content,
    1 - (embedding <=> $1) AS similarity
FROM document_chunk
WHERE knowledge_id = ANY($2)
  AND enabled = 1
  AND is_deleted = 0
ORDER BY embedding <=> $1
LIMIT $3;
```

规则：

- 检索必须带 `knowledge_id` 或 `knowledge_id = ANY(...)`。
- 禁止全表向量检索。
- 默认 Top-K = 5。
- 最大 Top-K = 20。
- 入参向量维度必须等于 `VECTOR(1536)`，禁止在查询时传入其他维度。
- 查询结果禁止返回 `embedding` 字段。
- 不允许 `SELECT *`。

### 7.5 HNSW 索引维护规则

- HNSW 会提升检索速度，但会增加写入成本和索引体积。
- 日常文档上传时禁止自动 `DROP INDEX`。
- 只有首次离线大批量初始化时，才允许先导入数据再创建 HNSW 索引。
- 如果 pgvector 检索 p95 超过 1 秒，先检查过滤条件、chunk 数、Top-K、索引大小，再考虑专用向量库。

---

## 八、MySQL 建表模板

AI 创建 MySQL 业务表时按此模板：

```sql
CREATE TABLE `{table_name}` (
    `id`          CHAR(36)     NOT NULL COMMENT '{业务含义}ID',
    `created_at`  DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间，UTC',
    `updated_at`  DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间，UTC',
    `is_deleted`  TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除：0 正常，1 已删除',

    -- 业务字段

    PRIMARY KEY (`id`)

    -- 索引按本规范追加；追加索引时，先在 PRIMARY KEY 后添加逗号
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_0900_ai_ci
  COMMENT='{表注释}';
```

资源表模板：

```sql
CREATE TABLE `agent` (
    `id`          CHAR(36)     NOT NULL COMMENT 'Agent ID',
    `created_at`  DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间，UTC',
    `updated_at`  DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间，UTC',
    `is_deleted`  TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除：0 正常，1 已删除',
    `created_by`  CHAR(36)     NULL COMMENT '创建人用户ID',
    `updated_by`  CHAR(36)     NULL COMMENT '更新人用户ID',

    `name`        VARCHAR(128) NOT NULL COMMENT 'Agent 名称',
    `description` VARCHAR(512) NULL COMMENT 'Agent 描述',
    `agent_type`  VARCHAR(32)  NOT NULL COMMENT 'Agent 类型：REACT / WORKFLOW',
    `status`      VARCHAR(32)  NOT NULL DEFAULT 'ACTIVE' COMMENT '状态',

    `active_name` VARCHAR(128)
        GENERATED ALWAYS AS (
            CASE WHEN `is_deleted` = 0 THEN `name` ELSE NULL END
        ) STORED COMMENT '未删除 Agent 名称唯一键',

    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_agent_active_name` (`active_name`),
    KEY `idx_agent_type_deleted_created_id` (`agent_type`, `is_deleted`, `created_at` DESC, `id` DESC),
    KEY `idx_agent_created_at` (`created_at`)
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_0900_ai_ci
  COMMENT='Agent';
```

---

## 九、AI 建表检查清单

AI 每次新增表时必须检查：

- 是否属于 MySQL 业务表，还是 PostgreSQL pgvector 表。
- MySQL 业务表是否包含 `id/created_at/updated_at/is_deleted`。
- 用户创建的资源表是否包含 `created_by/updated_by`。
- 是否错误使用 `VARCHAR(36)` 作为 MySQL 主键。
- 是否错误使用 `UNIQUE(field, is_deleted)`。
- 所有关联 ID 是否建索引。
- 大表是否有 Keyset 分页索引。
- 大表是否有单列 `created_at` 清理索引。
- 列表接口需要的字段是否能被轻量查询返回。
- 大字段是否避免进入列表查询。
- 是否存在 `SELECT *` 依赖。
- pgvector 表是否使用 PostgreSQL `UTF8`、`TIMESTAMPTZ`、`JSONB`。
- pgvector 检索是否禁止返回 `embedding`。
- 是否禁止日常导入时 DROP HNSW 索引。
