# 模型管理模块 — 数据模型设计

> 模型管理模块为 Agent 和工作流提供 LLM / Embedding 模型调用能力。
> 本文档定义该模块的数据库表结构、JSON 字段约定、查询索引和关系。

---

## 一、设计背景

### 1.1 供应商差异

一期需要对接两类 API 协议：

| 协议阵营 | 供应商 | Client 实现 |
|---------|--------|------------|
| OpenAI 兼容 | OpenAI、DeepSeek、Ollama、Moonshot、硅基流动、Together AI、Groq 等 | `OpenAiCompatibleClient`（Spring AI `OpenAiChatModel`） |
| Anthropic 私有 | Anthropic（Claude） | `AnthropicClient`（Spring AI `AnthropicChatModel`） |

一个 OpenAI 兼容 Client 实现，通过不同的 `baseUrl` + `apiKey` 即可覆盖所有兼容供应商。

### 1.2 认证方式差异

| 供应商 | 认证方式 | 额外参数 |
|--------|---------|---------|
| OpenAI / 兼容 | `Authorization: Bearer {apiKey}` | 无 |
| Anthropic | `x-api-key: {apiKey}` + `anthropic-version` header | 需要 `apiVersion` |
| Ollama | 无认证 | 无（`apiKey` 可空） |

**统一方案**：`provider_type` 决定 Client 实现和认证方式，`extra_config` JSON 补充各厂商特有参数。

### 1.3 模型参数差异

所有供应商都支持的通用参数（语义一致）：

- `temperature`（范围有差异：OpenAI 0~2，Anthropic 0~1）
- `max_tokens`
- `top_p`
- `stream`（内部参数，用户不感知）
- `stop`（可选）

各厂商独有参数（`top_k`、`presence_penalty`、`frequency_penalty` 等）一期不暴露给用户，由 `default_params` JSON 预留。

### 1.4 健康检测

一期做轻量版：

- 供应商层面：测试连通性（认证对不对、网络通不通）
- 模型层面：发最短 chat 请求验证模型可用性

**不持久化检测结果，不做定时巡检**。用户点按钮 → 同步发请求 → 返回实时结果。

---

## 二、ER 关系

```text
model_provider  1:N  model
                      ├─ 1:N  agent              （Agent 选用 LLM 模型）
                      ├─ 1:N  knowledge          （知识库选用 Embedding 模型）
                      └─ 1:N  workflow_node      （LLM 节点选用模型，逻辑引用）
```

---

## 三、表结构

### 3.1 `model_provider` — 模型供应商配置

```sql
CREATE TABLE `model_provider` (
    `id`            CHAR(36)     NOT NULL COMMENT '供应商 ID',
    `created_at`    DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间，UTC',
    `updated_at`    DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间，UTC',
    `is_deleted`    TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除：0 正常，1 已删除',
    `created_by`    CHAR(36)     NULL     COMMENT '创建人用户ID',
    `updated_by`    CHAR(36)     NULL     COMMENT '更新人用户ID',

    `name`          VARCHAR(128) NOT NULL COMMENT '供应商名称，如"我的 DeepSeek"',
    `provider_type` VARCHAR(32)  NOT NULL COMMENT '供应商类型：OPENAI / ANTHROPIC / OPENAI_COMPATIBLE',
    `api_key`       VARCHAR(512) NULL     COMMENT 'API Key（AES 加密存储），Ollama 等可留空',
    `base_url`      VARCHAR(512) NOT NULL COMMENT 'API Base URL，如 https://api.openai.com',
    `extra_config`  JSON         NULL     COMMENT '供应商特有配置（如 apiVersion、deploymentName）',
    `status`        VARCHAR(32)  NOT NULL DEFAULT 'ACTIVE' COMMENT '状态：ACTIVE / INACTIVE',

    `active_name`   VARCHAR(128)
        GENERATED ALWAYS AS (
            CASE WHEN `is_deleted` = 0 THEN `name` ELSE NULL END
        ) STORED COMMENT '未删除名称唯一键辅助列',

    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_mp_active_name` (`active_name`),
    KEY `idx_mp_type_deleted_created_id`
        (`provider_type`, `is_deleted`, `created_at` DESC, `id` DESC),
    KEY `idx_mp_status_deleted_created_id`
        (`status`, `is_deleted`, `created_at` DESC, `id` DESC)
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_0900_ai_ci
  COMMENT='模型供应商配置';
```

#### 字段说明

| 字段 | 必填 | 说明 |
|------|------|------|
| `name` | 是 | 用户自定义名称。允许同一供应商类型添加多个（如"DeepSeek 生产"和"DeepSeek 测试"），未删除唯一 |
| `provider_type` | 是 | 决定用哪个 Client 实现。`OPENAI` 和 `OPENAI_COMPATIBLE` 技术上用同一个 Client，`OPENAI` 用于前端预填 base_url 和品牌图标展示 |
| `api_key` | 否 | AES 加密存储。Ollama 本地部署不需要 Key，允许为空 |
| `base_url` | 是 | 每个供应商必须指定连接地址，包括 Ollama（`http://localhost:11434`） |
| `extra_config` | 否 | JSON 类型，存放各厂商特有参数。不为每个厂商建独立列 |
| `status` | 是 | `ACTIVE` = 启用，`INACTIVE` = 禁用。禁用后该供应商下所有模型不参与调用 |

### 3.2 `model` — 模型配置

```sql
CREATE TABLE `model` (
    `id`              CHAR(36)     NOT NULL COMMENT '模型 ID',
    `created_at`      DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间，UTC',
    `updated_at`      DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间，UTC',
    `is_deleted`      TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除：0 正常，1 已删除',

    `provider_id`     CHAR(36)     NOT NULL COMMENT '所属供应商 ID',
    `model_name`      VARCHAR(255) NOT NULL COMMENT '模型标识（如 gpt-4o、deepseek-chat）',
    `display_name`    VARCHAR(128) NULL     COMMENT '显示名称（如 GPT-4o），为空时使用 model_name',
    `model_type`      VARCHAR(32)  NOT NULL COMMENT '模型类型：LLM / EMBEDDING / RERANK',
    `enabled`         TINYINT      NOT NULL DEFAULT 1 COMMENT '是否启用：0 禁用，1 启用',
    `default_params`  JSON         NULL     COMMENT '默认调用参数',

    `active_provider_model` VARCHAR(292)
        GENERATED ALWAYS AS (
            CASE
                WHEN `is_deleted` = 0 THEN CONCAT(`provider_id`, '#', `model_name`)
                ELSE NULL
            END
        ) STORED COMMENT '未删除供应商+模型名唯一键辅助列',

    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_model_active_provider_model` (`active_provider_model`),
    KEY `idx_model_provider_deleted_created_id`
        (`provider_id`, `is_deleted`, `created_at` DESC, `id` DESC),
    KEY `idx_model_type_enabled_deleted`
        (`model_type`, `enabled`, `is_deleted`)
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_0900_ai_ci
  COMMENT='模型配置';
```

#### 字段说明

| 字段 | 必填 | 说明 |
|------|------|------|
| `provider_id` | 是 | 归属供应商。索引支撑"查某供应商下所有模型" |
| `model_name` | 是 | 发给 API 的模型标识，如 `gpt-4o`、`deepseek-chat`。用户手填（供应商模型列表变化太快，不做下拉枚举） |
| `display_name` | 否 | 用户给模型起的别名，用于 Agent 下拉框展示。不填就用 `model_name` |
| `model_type` | 是 | `LLM` / `EMBEDDING` / `RERANK`。决定使用场景：Agent 对话 → LLM，知识库向量化 → EMBEDDING |
| `enabled` | 是 | 简单开关，`0` 禁用 `1` 启用。禁用后不出现在 Agent/工作流的模型选择下拉框中 |
| `default_params` | 否 | 该模型的默认调用参数 JSON，可被 Agent 级别参数覆盖 |

`model` 不设 `status` 字段：模型只有开/关两种状态，`enabled` (TINYINT) 比状态枚举更直接。

`model` 不设 `created_by` / `updated_by`：模型归属于供应商，用户通过供应商追溯。

---

## 四、JSON 字段约定

### 4.1 `model_provider.extra_config`

按 `provider_type` 区分：

```jsonc
// OPENAI（官方）
{}

// ANTHROPIC
{ "apiVersion": "2023-06-01" }

// OPENAI_COMPATIBLE（DeepSeek、Ollama、Moonshot、硅基流动等）
{}

// Azure OpenAI（预留，通过 OPENAI_COMPATIBLE 接入）
{ "apiVersion": "2024-08-01-preview", "deploymentName": "gpt-4o-eastus" }
```

**原则**：`provider_type` 决定代码行为（用哪个 Client、怎么拼认证头），`extra_config` 只补充 `provider_type` 覆盖不了的配置项。

### 4.2 `model.default_params`

```jsonc
// GPT-4o 默认参数
{
  "temperature": 0.7,
  "maxTokens": 4096,
  "topP": 1.0
}

// Embedding 模型（不需要这些参数）
null

// DeepSeek-R1（不支持 temperature 调节）
{
  "maxTokens": 8192
}
```

**参数优先级**（高 → 低）：

```
Agent 级别参数 → 工作流节点参数 → model.default_params → application.yml 全局默认值
```

只存 JSON，不做字段校验。校验由 Java DTO + Spring AI `ChatOptions` 在运行时处理。

---

## 五、`provider_type` 到代码的映射

```text
provider_type             Client 实现                      认证方式
─────────────────────────────────────────────────────────────────────────────
OPENAI                  ─┐
OPENAI_COMPATIBLE       ──┤→ OpenAiCompatibleClient        Authorization: Bearer {apiKey}
                           │   (Spring AI OpenAiChatModel)
                           │
ANTHROPIC               ──→ AnthropicClient                 x-api-key: {apiKey}
                               (Spring AI AnthropicChatModel)   + anthropic-version: {extraConfig.apiVersion}
```

`OPENAI` 和 `OPENAI_COMPATIBLE` 共用同一个 Client 实现。`provider_type` 只影响：

1. 前端表单预填（`OPENAI` 预填 `https://api.openai.com`）
2. 供应商卡片上的品牌图标展示
3. 测试连接的端点选择

---

## 六、查询场景与索引

两张表均为小表（供应商 ≤50，模型 ≤200），使用 OFFSET 分页。

| 查询场景 | 命中索引 | 说明 |
|---------|---------|------|
| 查某供应商下所有模型 | `idx_model_provider_deleted_created_id` | Provider 详情页的模型列表 |
| 查所有可用的 LLM 模型 | `idx_model_type_enabled_deleted` | Agent 创建页的模型下拉框 |
| 按 ID 查单个模型 | PRIMARY KEY | 详情/编辑 |
| 按 ID 查单个供应商 | PRIMARY KEY | 详情/编辑 |
| 供应商列表（按类型筛选） | `idx_mp_type_deleted_created_id` | 模型管理页筛选 |
| 供应商列表（按状态筛选） | `idx_mp_status_deleted_created_id` | 模型管理页筛选 |

---

## 七、健康检测

不建表，不持久化，不做定时任务。

### 7.1 供应商连通性检测

| 供应商类型 | 检测方法 |
|-----------|---------|
| OPENAI / OPENAI_COMPATIBLE | `GET {baseUrl}/v1/models`，带 Bearer token |
| ANTHROPIC | `POST {baseUrl}/v1/messages`，发最短请求 |

返回结构（非持久化）：

```json
{
  "success": true,
  "message": "连接成功，发现 47 个可用模型",
  "latencyMs": 1200,
  "availableModels": ["gpt-4o", "gpt-4o-mini", ...]
}
```

### 7.2 模型可用性检测

发一条最短 chat 请求：

```json
POST /v1/chat/completions
{ "model": "gpt-4o", "messages": [{"role": "user", "content": "hi"}], "max_tokens": 1 }
```

返回结构（非持久化）：

```json
{
  "success": true,
  "message": "模型可用",
  "latencyMs": 1500,
  "errorDetail": null
}
```

---

## 八、与原 `glm-docs/11-zify-core-data-model.md` 的变化

| 维度 | 原设计 | 新设计 | 变化说明 |
|------|--------|--------|---------|
| 表数量 | 2 张 | 2 张 | 不变 |
| `model_provider.extra_config` | 无 | 新增 JSON 字段 | 兜底各厂商特有配置 |
| `model_provider.api_key` | 未定义类型 | `VARCHAR(512) NULL` | 加密存储，Ollama 可空 |
| `model.display_name` | 无 | 新增 `VARCHAR(128) NULL` | 支持模型别名 |
| `model.default_params` | 无 | 新增 JSON 字段 | 存默认调用参数 |
| `model.enabled` | 无 | 新增 `TINYINT DEFAULT 1` | 支持禁用单个模型 |
| `model` 唯一约束 | 无 | `(provider_id, model_name)` via generated column | 同供应商不重复添加同名模型 |
| 关系 | `model_provider 1:N model` | 不变 | — |
