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

数据库迁移脚本统一放在 `zify-app/src/main/resources/db/migration/`（Flyway），按模块前缀命名（如 `V1__agent__create_agent_table.sql`）。

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

### 2.4 字段类型选择指南

#### 整数类型

| 类型 | 范围 | 使用场景 |
|---|---|---|
| `TINYINT` | -128 ~ 127 | 布尔标记（0/1）、小范围枚举 |
| `SMALLINT` | -32768 ~ 32767 | 短列表排序、中等范围枚举 |
| `INT` | -21亿 ~ 21亿 | 数量、计数、排序值 |
| `BIGINT` | -922京 ~ 922京 | 仅当 INT 不够时使用 |

规则：

- `is_deleted` 固定用 `TINYINT`，不用 `BOOLEAN`（MySQL `BOOLEAN` 是 `TINYINT(1)` 的别名，无实质区别，但项目统一用 `TINYINT`）。
- 关联 ID 使用 `CHAR(36)`（UUID），不使用自增整数。
- 数量字段默认 `INT NOT NULL DEFAULT 0`。如果预期不会超过百万，用 `INT` 即可。
- 禁止用 `UNSIGNED` 属性，与 Java 类型映射容易出错。

#### 字符串类型

| 类型 | 最大长度 | 使用场景 |
|---|---|---|
| `CHAR(36)` | 固定 36 字符 | UUID 主键、关联 ID |
| `VARCHAR(N)` | 最多 65535 字节 | 名称、描述、状态、URL、配置值 |
| `TEXT` | 64 KB | 短内容：消息摘要、知识库 chunk 内容 |
| `MEDIUMTEXT` | 16 MB | 中等内容：较长的 Agent Prompt |
| `LONGTEXT` | 4 GB | 长内容：完整消息体、工作流 DSL |

VARCHAR 长度建议：

| 场景 | 建议长度 | 说明 |
|---|---|---|
| 名称 | `VARCHAR(128)` | Agent / 工具 / 知识库名称 |
| 描述 | `VARCHAR(512)` | 简短描述 |
| 状态 | `VARCHAR(32)` | 状态枚举值 |
| 类型 | `VARCHAR(32)` | 类型枚举值 |
| URL / Path | `VARCHAR(512)` | API URL、文件路径 |
| API Key 加密后 | `VARCHAR(512)` | 加密后的密钥 |
| 配置键值 | `VARCHAR(255)` | 配置名称、模型名称 |

规则：

- VARCHAR 长度按实际业务需要设置，不无脑给 255 或 1024。
- 超过 512 字符的文本内容优先使用 TEXT 类型。
- 所有 TEXT / MEDIUMTEXT / LONGTEXT 字段不允许设 DEFAULT 值。

#### 时间类型

| 类型 | 精度 | 范围 | 存储 | 时区 |
|---|---|---|---|---|
| `DATETIME(3)` | 毫秒 | 1000-9999 年 | 8 字节 | 不带时区，存什么读什么 |
| `TIMESTAMP(3)` | 毫秒 | 1970-2038 年 | 4 字节 | 自动转换时区 |

Zify 统一使用 `DATETIME(3)`，不使用 `TIMESTAMP`：

- `TIMESTAMP` 有 2038 年上限问题。
- `DATETIME` 不受 MySQL 时区设置影响，存 UTC 读 UTC，行为可预测。
- 应用层统一按 UTC 写入和读取，在展示层转换为用户时区。

规则：

- 所有时间字段精度统一为毫秒：`DATETIME(3)`。
- 禁止使用 `DATE`、`TIME`、`YEAR` 类型，除非有明确的业务需求（如生日、年度归档）。
- 禁止用 `VARCHAR` 或整数存时间戳。

#### 精确数值

金额、比例、费率等需要精确计算的字段必须使用 `DECIMAL`：

```sql
`rate` DECIMAL(10, 6) NOT NULL DEFAULT 0.000000 COMMENT '费率'
```

规则：

- 禁止用 `FLOAT` / `DOUBLE` 存储金额或需要精确比较的数值。
- `DECIMAL(P, S)` 中 P 为总位数，S 为小数位数。按业务精度需求设置。
- Zify 一期没有金额场景，但 Token 用量统计等如果需要精确计算，也应使用 `DECIMAL` 或 `BIGINT`（存最小单位）。

#### JSON 类型

```sql
`dsl_json` JSON NULL COMMENT '工作流 DSL 定义'
```

规则：

- JSON 字段用于存储结构灵活的配置数据（工作流 DSL、工具参数定义、节点配置等）。
- JSON 字段默认允许 `NULL`。需要空对象时由应用写入 `{}`。
- 禁止在 JSON 字段上直接建普通索引。需要索引 JSON 内部字段时使用 generated column。
- JSON 字段不做业务外键引用。引用关系用独立的外键字段。
- 详见 §5.6 JSON 字段使用规范。

### 2.5 表结构设计约束

规则：

- 单表字段数量建议不超过 30 个。超过时考虑拆分（如将大字段拆到扩展表）。
- 每张表必须写 `COMMENT`，每个字段必须写 `COMMENT`。
- 表名、字段名禁止使用 MySQL 保留字（如 `order`、`group`、`key`、`index`、`trigger`）。如果业务名称与保留字冲突，加前缀或后缀（如 `trigger_config`、`sort_order`）。
- 大字段（TEXT / MEDIUMTEXT / LONGTEXT / JSON）超过 2 个时，考虑拆到独立扩展表，避免影响主表查询性能。
- 禁止在数据库中存储文件二进制内容（BLOB）。文件存 PVC / 对象存储，数据库只存路径和元数据。
- 禁止在数据库中存储 Java 序列化对象。
- 同一语义的字段在不同表中必须使用相同的数据类型和长度。例如 `agent_id` 在所有表中都是 `CHAR(36) NOT NULL`。

### 2.6 MySQL 建库语句

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

### 3.4 关联表设计规范

关联表（如 `agent_tool`、`agent_knowledge`）也需要遵守通用字段规范：

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

规则：

- 关联表必须有 `id`、`created_at`、`updated_at`、`is_deleted` 四个通用字段。
- 关联表不需要 `created_by` / `updated_by`，可通过父对象追溯。
- 两个关联字段都必须建索引。
- 未删除数据的唯一约束使用 generated column 拼接两个关联 ID 实现。
- VARCHAR 拼接长度 = 两个 UUID 长度（36+36）+ 分隔符（1）= 73。
- 关联表不需要独立的清理策略，随父对象删除而软删除。

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

### 4.5 SQL 编写规范

#### INSERT 规则

- INSERT 语句必须显式指定列名，禁止省略列名依赖列顺序。
- 单次批量 INSERT 不超过 500 行。超过时分批执行，每批之间短暂间隔。
- 批量 INSERT 的 VALUES 子句中，每组值的列顺序必须与列名列表完全对应。

```sql
-- 正确
INSERT INTO `agent` (`id`, `name`, `agent_type`, `status`, `created_at`, `updated_at`, `is_deleted`)
VALUES (?, ?, ?, ?, ?, ?, 0);

-- 禁止
INSERT INTO `agent` VALUES (?, ?, ?, ?, ?, ?, 0);
```

#### UPDATE / DELETE 规则

- UPDATE 和 DELETE 必须有 WHERE 子句。禁止无 WHERE 的全表更新或删除。
- UPDATE 必须同时更新 `updated_at` 字段（MyBatis-Plus 自动填充可替代）。
- 大批量 DELETE 必须分批执行（每批 ≤ 1000 条），每批之间 sleep 100-500ms。
- 禁止在事务外执行大批量 UPDATE 或 DELETE。

```sql
-- 正确：分批删除
DELETE FROM trigger_log
WHERE created_at < DATE_SUB(UTC_TIMESTAMP(), INTERVAL 90 DAY)
ORDER BY created_at
LIMIT 1000;

-- 禁止：无 WHERE 条件
DELETE FROM trigger_log;
```

#### 查询规则

- 禁止 `SELECT *`，必须显式列出所需字段。
- 禁止在索引列上使用函数或表达式，会导致索引失效：

```sql
-- 禁止
WHERE DATE(created_at) = '2026-06-07'
WHERE UPPER(name) = 'TEST'

-- 正确
WHERE created_at >= '2026-06-07 00:00:00.000'
  AND created_at < '2026-06-08 00:00:00.000'
WHERE name = 'TEST'
```

- 禁止隐式类型转换。查询条件值必须与字段类型一致：

```sql
-- 禁止：VARCHAR 列用整数查询
WHERE agent_id = 12345

-- 正确
WHERE agent_id = '12345'
```

- `IN` 子句中的值不超过 500 个。超过时拆分多次查询或使用临时表。
- `LIKE` 查询禁止以通配符开头（`%keyword`），会导致索引失效。需要全文搜索时后续单独设计。
- 避免在 `WHERE` 子句中对不同字段使用 `OR`，优先用 `UNION ALL` 或拆分查询。
- 多表 JOIN 不超过 3 张表。
- 禁止使用存储过程、视图、触发器、自定义函数。业务逻辑全部在 Java 应用中实现。
- 新增查询必须使用 `EXPLAIN` 验证执行计划，确保命中正确索引。

### 4.6 状态字段设计规范

规则：

- 状态字段统一使用 `VARCHAR(32)`，不使用 MySQL `ENUM` 类型。

`ENUM` 的问题：

- 新增枚举值需要 `ALTER TABLE`，对大表风险高。
- `ENUM` 值在 Java 代码中也需要同步，两边维护成本高。
- `VARCHAR(32)` 足够存任何状态值，新增状态只需要改代码。

- 状态值使用大写英文，下划线分隔：`ACTIVE`、`INACTIVE`、`DRAFT`、`PUBLISHED`、`RUNNING`、`SUCCEEDED`、`FAILED`、`CANCELLED`、`PENDING`、`PROCESSING`。
- 每个状态字段必须定义 `NOT NULL DEFAULT '{初始状态}'`。
- 状态流转由 Java Service 层校验和驱动，数据库不约束状态转换路径。

常用状态字段约定：

| 表 | 字段 | 值域 |
|---|---|---|
| `agent` | `status` | `ACTIVE` / `INACTIVE` |
| `agent` | `agent_type` | `REACT` / `WORKFLOW` |
| `workflow` | `status` | `DRAFT` / `PUBLISHED` |
| `tool` | `status` | `ACTIVE` / `INACTIVE` |
| `tool` | `tool_type` | `MCP` / `HTTP` / `WORKFLOW` |
| `workflow_run` | `status` | `PENDING` / `RUNNING` / `SUCCEEDED` / `FAILED` / `CANCELLED` |
| `workflow_node_run` | `status` | `PENDING` / `RUNNING` / `SUCCEEDED` / `FAILED` / `SKIPPED` |
| `trigger` | `trigger_type` | `WEBHOOK` / `CRON` |
| `trigger_log` | `status` | `PENDING` / `SUCCEEDED` / `FAILED` |
| `document` | `parse_status` | `PENDING` / `PROCESSING` / `SUCCEEDED` / `FAILED` |
| `conversation` | `status` | `ACTIVE` / `ARCHIVED` |
| `message` | `role` | `USER` / `ASSISTANT` / `SYSTEM` / `TOOL` |
| `model_provider` | `provider_type` | `OPENAI` / `ANTHROPIC` / `OPENAI_COMPATIBLE` |
| `model` | `model_type` | `LLM` / `EMBEDDING` / `RERANK` |
| `model_provider` | `status` | `ACTIVE` / `INACTIVE` |

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

### 5.6 JSON 字段使用规范

Zify 多处使用 JSON 字段存储结构灵活的配置数据：

| 表 | JSON 字段 | 用途 |
|---|---|---|
| `workflow` | `dsl_json` | 工作流画布定义（节点、连线、位置） |
| `workflow_node` | `config_json` | 节点配置（LLM Prompt、HTTP 参数、代码片段等） |
| `tool` | `parameter_schema_json` | 工具参数 JSON Schema 定义 |
| `tool` | `config_json` | 工具连接配置（HTTP 工具的 URL/Header/Body 模板） |
| `tool_call_log` | `request_json` | 工具调用请求体 |
| `tool_call_log` | `response_json` | 工具调用响应体 |
| `workflow_node_run` | `input_json` | 节点运行输入数据 |
| `workflow_node_run` | `output_json` | 节点运行输出数据 |
| `document_chunk` (PG) | `metadata` | 文档 chunk 元数据（来源页码、段落位置等） |

使用规则：

- **JSON 用于配置和日志，不用于核心查询维度**。需要作为查询条件、排序条件、唯一约束的字段，必须拆成独立列。
- **JSON 字段不参与 WHERE 过滤**。如果需要按 JSON 内部字段过滤，使用 generated column 提取为独立列并建索引。
- **JSON 列表接口不返回**。列表接口只返回轻量字段，JSON 大字段只在详情接口按主键查询。
- **JSON 结构由 Java 代码校验**，数据库不约束 JSON 内部结构。Java 中使用对应的 DTO 类做序列化和校验。
- **JSON 字段允许 NULL**，不设 DEFAULT。需要空对象时由应用写入 `{}` 或 `[]`。
- **禁止用 JSON 替代正常的表关联**。对象之间的引用关系必须用独立的外键字段 + 索引。

generated column 索引示例（当需要按 JSON 内部字段查询时）：

```sql
-- 如果需要按 workflow_node 的 node_type 查询
`node_type_str` VARCHAR(32)
    GENERATED ALWAYS AS (
        JSON_UNQUOTE(JSON_EXTRACT(`config_json`, '$.nodeType'))
    ) STORED COMMENT '节点类型，从 config_json 提取',

KEY `idx_wn_node_type` (`node_type_str`)
```

### 5.7 事务规范

#### 隔离级别

Zify 使用 MySQL 默认隔离级别 `READ_COMMITTED`：

```yaml
# application.yml
spring:
  datasource:
    hikari:
      transaction-isolation: TRANSACTION_READ_COMMITTED
```

理由：

- `READ_COMMITTED` 避免 `REPEATABLE_READ` 下的间隙锁范围过大问题。
- Zify 单副本部署，并发冲突概率低，`READ_COMMITTED` 足够。
- 配合乐观锁（版本号或 `updated_at` CAS）处理并发更新。

#### 事务范围规则

- **事务只包住数据库读写操作，不包住外部调用**。禁止在事务内调用 LLM、Embedding、MCP、HTTP 工具或其他慢外部 API。
- 事务方法尽量短，从获取连接到释放连接的耗时控制在 200ms 以内。
- 读操作不需要显式事务（`@Transactional(readOnly = true)` 可用于标记但非必须）。
- 写操作事务放在 Service 的 public 方法上，Controller 不开事务。

```java
// 正确：事务只包住数据库操作
@Transactional
public void createAgent(CreateAgentCommand command) {
    // 1. 校验 → 调用其他模块 Facade（无事务外部调用）
    validate(command);
    // 2. 写库 → 事务内
    agentMapper.insert(entity);
    // 3. 写关联 → 事务内
    batchInsertAgentTools(agentId, command.getToolIds());
}

// 错误：事务包住了 LLM 调用
@Transactional
public void runAgent(String agentId) {
    AgentEntity agent = agentMapper.selectById(agentId);
    String response = modelFacade.chat(agent.getPrompt()); // 禁止：LLM 调用在事务内
    messageMapper.insert(responseEntity);
}
```

#### 大事务监控

- 一期通过 Hikari 连接池指标监控事务耗时。
- 事务持续时间超过 1 秒记录 WARN 日志。
- Hikari `connection-timeout` 设为 2 秒，获取连接超时时立即告警。

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

## 九、MySQL 实例配置建议

一期单节点部署，以下为关键参数建议值，实际按监控调整。

### 连接池（应用侧 Hikari）

```yaml
spring:
  datasource:
    hikari:
      maximum-pool-size: 20
      minimum-idle: 5
      connection-timeout: 2000
      validation-timeout: 1000
      idle-timeout: 300000
      max-lifetime: 1800000
      connection-test-query: SELECT 1
```

### MySQL 关键参数

```ini
# InnoDB 缓冲池：建议占物理内存 60-70%，但需与 zify-server 共存
# 8C16G 节点上 MySQL 独占时设 8G；与其他组件共存时适当调小
innodb_buffer_pool_size = 4G

# 每个事务的日志缓冲
innodb_log_buffer_size = 16M

# 连接数：应用连接池 20 + 预留监控和运维
max_connections = 100

# 慢查询监控
slow_query_log = ON
long_query_time = 0.5

# 字符集（建库时已指定，实例级也建议统一）
character_set_server = utf8mb4
collation_server = utf8mb4_0900_ai_ci

# binlog 用于备份恢复
log_bin = ON
binlog_format = ROW
binlog_retention_hours = 168
```

规则：

- `innodb_buffer_pool_size` 不超过物理内存的 70%，且需预留 OS 和其他进程内存。
- `slow_query_log` 必须开启，阈值默认 500ms。定期分析慢查询并优化。
- `max_connections` 必须大于应用连接池 `maximum-pool-size`，预留运维连接。
- 一期不需要主从复制，但 `log_bin` 开启以支持增量备份和回滚。

---

## 十、AI 建表检查清单

AI 每次新增表时必须检查：

**通用字段与类型**

- [ ] 是否属于 MySQL 业务表，还是 PostgreSQL pgvector 表。
- [ ] MySQL 业务表是否包含 `id/created_at/updated_at/is_deleted`。
- [ ] 用户创建的资源表是否包含 `created_by/updated_by`。
- [ ] 是否错误使用 `VARCHAR(36)` 作为 MySQL 主键（应为 `CHAR(36)`）。
- [ ] 整数字段是否使用了 `UNSIGNED`（禁止）。
- [ ] 时间字段是否使用 `DATETIME(3)` 而非 `TIMESTAMP` 或 `VARCHAR`。
- [ ] 金额或精确数值是否使用 `DECIMAL` 而非 `FLOAT` / `DOUBLE`。
- [ ] 状态字段是否使用 `VARCHAR(32)` 而非 `ENUM`。
- [ ] 每个字段是否都有 `COMMENT`。
- [ ] 同一语义字段在不同表中是否类型和长度一致。

**唯一约束与关联**

- [ ] 是否错误使用 `UNIQUE(field, is_deleted)`（应使用 generated column）。
- [ ] 关联表是否包含通用字段并有 generated column 唯一约束。
- [ ] 所有关联 ID 是否建索引。

**索引设计**

- [ ] 大表是否有 Keyset 分页索引。
- [ ] 大表是否有单列 `created_at` 清理索引。
- [ ] 联合索引字段顺序是否遵循：等值字段 → `is_deleted` → 范围字段 → `id`。
- [ ] 是否存在冗余索引或超过 8 个索引。
- [ ] 是否在 JSON / 大 TEXT 字段上建了普通索引（禁止）。

**查询设计**

- [ ] 列表接口是否只返回轻量字段。
- [ ] 大字段是否避免进入列表查询。
- [ ] 是否存在 `SELECT *` 依赖。
- [ ] 新增查询是否用 `EXPLAIN` 验证过执行计划。
- [ ] INSERT 是否显式指定了列名。
- [ ] WHERE 条件中的值类型是否与字段类型一致（避免隐式转换）。

**JSON 字段**

- [ ] JSON 字段是否只用于配置和日志，不用于核心查询维度。
- [ ] 需要 JSON 内部字段查询时是否使用了 generated column。
- [ ] JSON 字段是否不在列表接口中返回。

**事务**

- [ ] 事务内是否包含外部 API 调用（禁止）。
- [ ] 事务是否尽量短（目标 < 200ms）。

**PostgreSQL / pgvector**

- [ ] pgvector 表是否使用 PostgreSQL `UTF8`、`TIMESTAMPTZ`、`JSONB`。
- [ ] pgvector 检索是否禁止返回 `embedding`。
- [ ] pgvector 检索是否带 `knowledge_id` 过滤。
- [ ] 是否禁止日常导入时 DROP HNSW 索引。

**表结构**

- [ ] 单表字段是否超过 30 个（超过需说明理由或拆表）。
- [ ] 大字段（TEXT/MEDIUMTEXT/LONGTEXT/JSON）是否超过 2 个（超过考虑拆扩展表）。
- [ ] 表名和字段名是否使用了 MySQL 保留字（禁止）。
