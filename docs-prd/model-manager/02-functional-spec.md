# 模型管理模块 — 功能规格说明

> 模型管理模块管理 LLM 模型供应商的连接配置和模型定义，为 Agent、工作流、知识库提供模型调用能力。
> 本文档定义模块的全部功能范围、交互流程、API 接口和业务规则。

---

## 一、模块定位

```text
┌─────────────────────────────────────────────────────┐
│ 模型管理                                             │
│                                                     │
│  职责：供应商连接配置、模型注册、连通性测试            │
│  不负责：实际的 LLM 调用执行（归 model 模块 Facade）   │
│                                                     │
│  被依赖方：                                           │
│    agent   → 选择 LLM 模型                           │
│    knowledge → 选择 Embedding 模型                    │
│    workflow → LLM 节点选择模型                        │
│    model   → Provider 连通性测试                      │
└─────────────────────────────────────────────────────┘
```

数据模型详见 `01-data-model.md`。

---

## 二、功能总览

| 功能 | 说明 | 一期 |
|------|------|------|
| 供应商 CRUD | 创建、查看、编辑、删除供应商配置 | ✅ |
| 供应商启用/禁用 | 禁用后该供应商下所有模型不参与调用 | ✅ |
| 供应商连通性测试 | 验证 API Key、Base URL 是否正确 | ✅ |
| 模型 CRUD | 创建、查看、编辑、删除模型 | ✅ |
| 模型启用/禁用 | 禁用后不出现在模型选择下拉框中 | ✅ |
| 模型可用性测试 | 发送最短请求验证模型可调用 | ✅ |
| 模型列表总览 | 跨供应商查看所有已注册模型 | ✅ |
| 模型参数配置 | 设置 temperature、maxTokens 等默认参数 | ✅ |
| 供应商自动发现模型 | 连接成功后自动拉取可用模型列表 | ❌ 二期 |
| 定时健康巡检 | 定期检测所有供应商和模型状态 | ❌ 二期 |
| 负载均衡 / 多 Provider 分发 | 多个供应商之间的流量分配 | ❌ 不做 |
| 多租户 Provider 隔离 | 不同租户使用不同供应商配置 | ❌ 不做 |
| 用量计费和额度管控 | Token 用量统计和费用管理 | ❌ 不做 |
| TTS / STT 模型类型 | 语音合成/识别模型 | ❌ 不做 |

---

## 三、供应商管理

### 3.1 创建供应商

**入口**：模型管理页面 → "添加供应商" 按钮

**表单字段**：

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| 供应商名称 | 文本输入 | 是 | 用户自定义，如"我的 DeepSeek" |
| 供应商类型 | 下拉选择 | 是 | OpenAI / Anthropic / OpenAI 兼容 |
| API Key | 密码输入 | 否 | Ollama 等本地服务可留空。显示为 `••••••` |
| Base URL | 文本输入 | 是 | 根据供应商类型预填默认值 |

**供应商类型对应的表单行为**：

| 供应商类型 | Base URL 预填值 | API Key | 额外配置 |
|-----------|----------------|---------|---------|
| OpenAI | `https://api.openai.com` | 必填 | 无 |
| Anthropic | `https://api.anthropic.com` | 必填 | 显示 apiVersion 输入框，默认 `2023-06-01` |
| OpenAI 兼容 | 空（用户手动填） | 可选 | 无 |

**提交流程**：

```text
1. 前端校验：名称不为空、Base URL 格式合法
2. 后端校验：名称未重复、Base URL 格式合法
3. API Key AES 加密后存储
4. extra_config 按 provider_type 写入对应 JSON
5. 保存成功，返回供应商 ID
6. 前端提示"保存成功，建议测试连接"
```

**业务规则**：

- 供应商名称在未删除数据中唯一
- API Key 存入前必须加密，读取时解密，日志中禁止输出原始值
- Base URL 末尾的 `/` 由后端统一处理（去掉末尾斜杠）
- 同一供应商类型可以添加多个实例（如两个 OpenAI 分别用不同 Key）

### 3.2 查看供应商列表

**展示方式**：卡片列表

**每个卡片显示**：

```text
┌─────────────────────────────────────────┐
│  🟢 我的 DeepSeek                        │
│  DeepSeek · OpenAI 兼容                  │
│  模型数: 3                               │
│  ─────────────────────────────────────  │
│  [编辑]  [测试连接]  [启用/禁用]  [删除]   │
└─────────────────────────────────────────┘
```

| 卡片字段 | 数据来源 |
|---------|---------|
| 供应商名称 | `model_provider.name` |
| 品牌标识 | 根据 `provider_type` 展示对应品牌名 |
| 协议类型标签 | `provider_type` → "OpenAI 兼容" / "Anthropic" |
| 模型数量 | `SELECT COUNT(*) FROM model WHERE provider_id = ? AND is_deleted = 0` |
| 启用/禁用状态 | `model_provider.status` → ACTIVE 显示 🟢，INACTIVE 显示 ⚫ |
| 操作按钮 | 编辑、测试连接、启用/禁用、删除 |

**筛选**：按供应商类型筛选、按状态筛选

**排序**：按创建时间倒序

### 3.3 编辑供应商

**入口**：供应商卡片 → "编辑" 按钮 → 弹窗表单

**可编辑字段**：名称、API Key、Base URL、extra_config

**不可编辑字段**：供应商类型（创建后不可更改，因为不同类型对应不同的 Client 实现）

**业务规则**：

- 修改 API Key 时，新旧 Key 都不在表单中显示明文。用占位符 `••••••` 表示已有 Key，输入新值则覆盖，留空则不修改
- 修改 Base URL 后，已有的模型配置不受影响（模型名不变）

### 3.4 删除供应商

**入口**：供应商卡片 → "删除" 按钮

**交互流程**：

```text
1. 用户点击"删除"
2. 前端弹窗确认：
   - 如果该供应商下有模型："该供应商下有 N 个模型，删除后模型将不可用。确认删除？"
   - 如果该供应商下无模型："确认删除该供应商？"
3. 用户确认
4. 后端执行：
   a. 软删除该供应商下所有模型（is_deleted = 1）
   b. 软删除该供应商（is_deleted = 1）
5. 前端刷新列表
```

**业务规则**：

- 删除是软删除（`is_deleted = 1`）
- 删除供应商时必须同时软删除其下所有模型
- 已被 Agent / 知识库 / 工作流节点引用的模型被删除后，引用关系不自动解除（Agent 使用时校验模型是否可用）
- 删除操作不需要校验是否有 Agent 引用，用户明确确认即可

### 3.5 启用/禁用供应商

**入口**：供应商卡片 → "启用" / "禁用" 按钮

**行为**：

- 禁用：`status` 设为 `INACTIVE`。该供应商下所有模型不参与调用（即使模型本身 `enabled = 1`）
- 启用：`status` 设为 `ACTIVE`。恢复到模型自身的 `enabled` 状态决定是否可用

**判断优先级**：供应商禁用 → 整体不可用；供应商启用 → 再看模型自身的 `enabled`

### 3.6 供应商连通性测试

**入口**：供应商卡片 → "测试连接" 按钮，或供应商编辑弹窗内

**交互流程**：

```text
1. 用户点击"测试连接"
2. 按钮显示 loading 状态
3. 后端执行：
   a. 取出供应商配置（解密 API Key）
   b. 根据 provider_type 选择检测方式
   c. 发送请求（超时 15 秒）
4. 返回测试结果
5. 前端展示结果
```

**各供应商检测方式**：

| provider_type | 检测方法 | 成功判定 |
|--------------|---------|---------|
| OPENAI | `GET {baseUrl}/v1/models`，`Authorization: Bearer {apiKey}` | HTTP 200 |
| OPENAI_COMPATIBLE | `GET {baseUrl}/v1/models`，`Authorization: Bearer {apiKey}` | HTTP 200 |
| ANTHROPIC | `POST {baseUrl}/v1/messages`，`x-api-key: {apiKey}`，最短请求 | HTTP 200 |

**返回结果展示**：

```text
成功：
  ✅ 连接成功 — 已获取到 47 个可用模型

失败场景：
  ❌ 连接失败 — 401 Unauthorized: Invalid API Key
  ❌ 连接失败 — 连接超时，请检查 Base URL
  ❌ 连接失败 — 404 Not Found: 请检查 Base URL 是否正确
```

**业务规则**：

- 测试结果不持久化，每次都是实时检测
- 测试请求超时 15 秒
- OpenAI 兼容类检测成功时，可额外返回 `availableModels` 模型名列表（供用户参考，不自动注册）

---

## 四、模型管理

### 4.1 添加模型

**入口**：模型管理页面 → 供应商卡片展开区域 → "添加模型" 按钮

**表单字段**：

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| 模型标识 | 文本输入 | 是 | API 调用时的模型名，如 `gpt-4o` |
| 显示名称 | 文本输入 | 否 | 用户可读名称，如 `GPT-4o`。为空则使用模型标识 |
| 模型类型 | 下拉选择 | 是 | LLM / Embedding |
| 启用状态 | 开关 | 否 | 默认启用 |

**提交流程**：

```text
1. 前端校验：模型标识不为空、模型类型已选
2. 后端校验：该供应商下未删除模型中不存在同名模型标识
3. 保存
4. 前端提示"模型添加成功"
```

**业务规则**：

- 同一供应商下，未删除的模型标识（`model_name`）必须唯一
- `model_name` 用户手填，不提供下拉枚举——供应商模型列表变化太快，硬编码必过时
- 一期 `model_type` 只有 `LLM` 和 `EMBEDDING` 两个选项，`RERANK` 预留但不在下拉中展示
- `display_name` 为空时，展示层统一 fallback 到 `model_name`

### 4.2 查看模型列表

**两种视图**：

#### 视图 A：供应商下的模型列表

供应商卡片展开后显示该供应商下的模型列表。

```text
┌─────────────────────────────────────────┐
│  🟢 我的 DeepSeek                        │
│  DeepSeek · OpenAI 兼容 · 3 个模型       │
│  ─────────────────────────────────────  │
│  deepseek-chat     [LLM]  [测试] [删除]  │
│  deepseek-reasoner [LLM]  [测试] [删除]  │
│  deepseek-embedding [EMB] [测试] [删除]  │
│  ─────────────────────────────────────  │
│  [+ 添加模型]                            │
└─────────────────────────────────────────┘
```

#### 视图 B：全局模型总览

页面底部或 Tab 切换，展示所有供应商下的模型总览，支持筛选。

| 列 | 数据来源 |
|----|---------|
| 显示名称 / 模型标识 | `model.display_name` ?? `model.model_name` |
| 供应商 | `model_provider.name`（JOIN） |
| 供应商类型 | `model_provider.provider_type`（JOIN） |
| 模型类型 | `model.model_type` |
| 启用状态 | `model.enabled` |
| 操作 | 测试、启用/禁用、删除 |

**筛选条件**：按模型类型（LLM / Embedding）、按启用状态

**排序**：按供应商分组，组内按创建时间倒序

### 4.3 编辑模型

**入口**：模型条目 → "编辑" 按钮 → 弹窗或内联编辑

**可编辑字段**：显示名称、模型类型、默认参数

**不可编辑字段**：模型标识（创建后不可更改，因为已有 Agent 可能引用该名称）、所属供应商（不可转移）

**默认参数编辑**：

```text
┌─ 默认调用参数 ──────────────────────────┐
│  temperature: [0.7     ]  范围 0~1      │
│  maxTokens:   [4096    ]  最大输出长度   │
│  topP:        [1.0     ]  范围 0~1      │
│                                         │
│  💡 这些参数可被 Agent 级别参数覆盖       │
└─────────────────────────────────────────┘
```

- 参数为可选填写，留空表示使用全局默认值
- `temperature` 范围限制为 0~1（兼容 Anthropic 的保守范围）
- `maxTokens` 不设上限（各模型上限不同，由 API 侧校验）
- `topP` 范围 0~1

### 4.4 删除模型

**入口**：模型条目 → "删除" 按钮

**交互流程**：

```text
1. 用户点击"删除"
2. 前端弹窗确认：
   - 如果有 Agent 引用该模型："该模型被 N 个 Agent 使用，删除后这些 Agent 将无法对话。确认删除？"
   - 如果无 Agent 引用："确认删除该模型？"
3. 用户确认
4. 后端软删除（is_deleted = 1）
5. 前端刷新列表
```

**业务规则**：

- 删除是软删除
- 引用该模型的 Agent / 知识库 / 工作流节点的配置不自动修改（使用时校验模型是否可用）

### 4.5 启用/禁用模型

**入口**：模型条目 → "启用" / "禁用" 开关

**行为**：

- 禁用：`enabled = 0`。该模型不出现在 Agent 创建页的模型下拉框中。已经在使用该模型的 Agent 不受影响（Agent 保存时已记录模型 ID，运行时再校验）
- 启用：`enabled = 1`

**可用性判断**：

```text
模型可用的条件（缺一不可）：
  1. model_provider.status = 'ACTIVE'（供应商已启用）
  2. model.enabled = 1（模型已启用）
  3. model.is_deleted = 0（未删除）
```

### 4.6 模型可用性测试

**入口**：模型条目 → "测试" 按钮

**交互流程**：

```text
1. 用户点击"测试"
2. 按钮显示 loading 状态
3. 后端执行：
   a. 取出模型配置和供应商配置（解密 API Key）
   b. 发送最短 chat 请求：messages=[{role:"user",content:"hi"}], max_tokens=1
   c. 超时 15 秒
4. 返回测试结果
5. 前端展示结果
```

**返回结果展示**：

```text
成功：
  ✅ 模型可用 — 响应时间 1.2s

失败场景：
  ❌ 模型不可用 — 404 Model not found: gpt-4o-typo
  ❌ 模型不可用 — 402 Insufficient quota: 请检查账户余额
  ❌ 模型不可用 — 429 Rate limit exceeded: 请稍后重试
  ❌ 模型不可用 — 供应商连接失败，请先测试供应商连接
```

**Embedding 模型测试**：

Embedding 模型不支持 chat 请求，使用专用的测试方式：

```text
发送最短 embedding 请求：input=["test"]
判断成功：返回 200 且 embedding 向量非空
```

**业务规则**：

- 测试前先校验供应商是否启用，未启用直接返回"供应商已禁用"
- 测试结果不持久化
- 测试请求消耗 1~3 个 token（约 ¥0.0001），对用户透明

---

## 五、API 接口设计

### 5.1 供应商接口

#### 创建供应商

```text
POST /api/model/providers
```

请求：

```json
{
  "name": "我的 DeepSeek",
  "providerType": "OPENAI_COMPATIBLE",
  "apiKey": "sk-xxx",
  "baseUrl": "https://api.deepseek.com",
  "extraConfig": null
}
```

响应：

```json
{
  "code": "SUCCESS",
  "data": {
    "id": "uuid",
    "name": "我的 DeepSeek",
    "providerType": "OPENAI_COMPATIBLE",
    "baseUrl": "https://api.deepseek.com",
    "status": "ACTIVE",
    "createdAt": "2026-06-07T12:00:00.000Z"
  }
}
```

规则：

- `apiKey` 请求中传明文，后端加密存储，响应中不返回
- `baseUrl` 后端去掉末尾 `/`
- `extraConfig` 为 null 时不写入

#### 查询供应商列表

```text
GET /api/model/providers?page=1&pageSize=20&providerType=&status=
```

响应：

```json
{
  "code": "SUCCESS",
  "data": {
    "records": [
      {
        "id": "uuid",
        "name": "我的 DeepSeek",
        "providerType": "OPENAI_COMPATIBLE",
        "baseUrl": "https://api.deepseek.com",
        "status": "ACTIVE",
        "modelCount": 3,
        "createdAt": "2026-06-07T12:00:00.000Z",
        "updatedAt": "2026-06-07T12:00:00.000Z"
      }
    ],
    "total": 5,
    "page": 1,
    "pageSize": 20
  }
}
```

规则：

- `modelCount` 为关联的未删除模型数量，通过子查询或 JOIN 聚合
- 不返回 `apiKey`
- 小表，使用 OFFSET 分页

#### 查询供应商详情

```text
GET /api/model/providers/{id}
```

响应：

```json
{
  "code": "SUCCESS",
  "data": {
    "id": "uuid",
    "name": "我的 DeepSeek",
    "providerType": "OPENAI_COMPATIBLE",
    "baseUrl": "https://api.deepseek.com",
    "extraConfig": null,
    "status": "ACTIVE",
    "createdAt": "2026-06-07T12:00:00.000Z",
    "updatedAt": "2026-06-07T12:00:00.000Z"
  }
}
```

规则：

- 不返回 `apiKey`
- 前端需要判断"是否已配置 API Key"时，返回 `hasApiKey: true/false` 布尔值，不返回实际值

#### 更新供应商

```text
PUT /api/model/providers/{id}
```

请求：

```json
{
  "name": "我的 DeepSeek（生产）",
  "apiKey": null,
  "baseUrl": "https://api.deepseek.com",
  "extraConfig": null
}
```

规则：

- `apiKey` 为 null 或空字符串时表示不修改；有值时覆盖更新
- `providerType` 不可修改
- `name` 修改后仍需满足未删除唯一约束

#### 删除供应商

```text
DELETE /api/model/providers/{id}
```

规则：

- 软删除供应商及其下所有模型
- 无请求体

#### 启用/禁用供应商

```text
PUT /api/model/providers/{id}/status
```

请求：

```json
{
  "status": "INACTIVE"
}
```

#### 测试供应商连接

```text
POST /api/model/providers/{id}/test
```

响应：

```json
{
  "code": "SUCCESS",
  "data": {
    "success": true,
    "message": "连接成功，发现 47 个可用模型",
    "latencyMs": 1200,
    "availableModels": ["gpt-4o", "gpt-4o-mini", "text-embedding-3-small"]
  }
}
```

失败响应：

```json
{
  "code": "SUCCESS",
  "data": {
    "success": false,
    "message": "401 Unauthorized: Invalid API Key",
    "latencyMs": 850,
    "availableModels": null
  }
}
```

规则：

- 接口本身不抛业务异常（HTTP 200），通过 `data.success` 判断测试结果
- `availableModels` 仅 OpenAI 兼容类返回，Anthropic 返回 null
- 超时 15 秒

### 5.2 模型接口

#### 添加模型

```text
POST /api/model/providers/{providerId}/models
```

请求：

```json
{
  "modelName": "gpt-4o",
  "displayName": "GPT-4o",
  "modelType": "LLM",
  "enabled": true
}
```

#### 查询模型列表（全局总览）

```text
GET /api/model/models?page=1&pageSize=20&modelType=&enabled=
```

响应：

```json
{
  "code": "SUCCESS",
  "data": {
    "records": [
      {
        "id": "uuid",
        "modelName": "gpt-4o",
        "displayName": "GPT-4o",
        "modelType": "LLM",
        "enabled": true,
        "providerId": "uuid",
        "providerName": "我的 OpenAI",
        "providerType": "OPENAI",
        "providerStatus": "ACTIVE",
        "createdAt": "2026-06-07T12:00:00.000Z"
      }
    ],
    "total": 12,
    "page": 1,
    "pageSize": 20
  }
}
```

规则：

- `providerName`、`providerType`、`providerStatus` 通过 JOIN `model_provider` 获取
- 不返回 `defaultParams`（列表接口避免大字段）

#### 查询模型列表（某供应商下）

```text
GET /api/model/providers/{providerId}/models
```

响应同上，但只返回该供应商下的模型。

#### 查询模型详情

```text
GET /api/model/models/{id}
```

响应：

```json
{
  "code": "SUCCESS",
  "data": {
    "id": "uuid",
    "modelName": "gpt-4o",
    "displayName": "GPT-4o",
    "modelType": "LLM",
    "enabled": true,
    "defaultParams": {
      "temperature": 0.7,
      "maxTokens": 4096,
      "topP": 1.0
    },
    "providerId": "uuid",
    "providerName": "我的 OpenAI",
    "providerType": "OPENAI",
    "providerStatus": "ACTIVE",
    "createdAt": "2026-06-07T12:00:00.000Z",
    "updatedAt": "2026-06-07T12:00:00.000Z"
  }
}
```

#### 更新模型

```text
PUT /api/model/models/{id}
```

请求：

```json
{
  "displayName": "GPT-4o",
  "modelType": "LLM",
  "enabled": true,
  "defaultParams": {
    "temperature": 0.7,
    "maxTokens": 4096
  }
}
```

规则：

- `modelName` 不可修改
- `providerId` 不可修改（不能转移到其他供应商）
- `defaultParams` 传 null 表示清除默认参数

#### 删除模型

```text
DELETE /api/model/models/{id}
```

#### 启用/禁用模型

```text
PUT /api/model/models/{id}/enabled
```

请求：

```json
{
  "enabled": false
}
```

#### 测试模型可用性

```text
POST /api/model/models/{id}/test
```

响应：

```json
{
  "code": "SUCCESS",
  "data": {
    "success": true,
    "message": "模型可用",
    "latencyMs": 1500,
    "errorDetail": null
  }
}
```

失败响应：

```json
{
  "code": "SUCCESS",
  "data": {
    "success": false,
    "message": "模型不可用",
    "latencyMs": 800,
    "errorDetail": "404 Model not found: gpt-4o-typo"
  }
}
```

### 5.3 供其他模块调用的内部接口

以下接口通过 `ModelFacade`（Java 内部调用），不走 HTTP。

#### 查询可用 LLM 模型列表

供 Agent 创建页、工作流 LLM 节点配置页的模型下拉框调用。

```text
ModelFacade.listAvailableModels(modelType: "LLM") → List<ModelSummary>
```

返回条件：

```sql
WHERE m.is_deleted = 0
  AND m.enabled = 1
  AND m.model_type = 'LLM'
  AND mp.is_deleted = 0
  AND mp.status = 'ACTIVE'
```

返回结构：

```json
[
  {
    "id": "uuid",
    "displayName": "GPT-4o",
    "modelName": "gpt-4o",
    "providerName": "我的 OpenAI",
    "providerType": "OPENAI"
  }
]
```

#### 查询可用 Embedding 模型列表

供知识库创建页调用。

```text
ModelFacade.listAvailableModels(modelType: "EMBEDDING") → List<ModelSummary>
```

#### 获取模型调用配置

供 Agent 对话、工作流 LLM 节点、知识库 Embedding 调用时使用。

```text
ModelFacade.getModelCallConfig(modelId: String) → ModelCallConfig
```

返回结构：

```java
type ModelCallConfig = {
    providerType: string,      // 决定用哪个 Client
    baseUrl: string,           // API 地址
    apiKey: string,            // 解密后的 Key
    modelName: string,         // 模型标识
    extraConfig: map,          // 供应商特有配置
    defaultParams: map         // 模型默认参数
}
```

**硬性规则**：

- `ModelCallConfig` 只在 `zify-model` 模块内部使用，不返回给其他模块（其他模块通过 `ModelFacade.chat()` 调用）
- `apiKey` 只在 `zify-model` 的 `infrastructure/client/` 包内解密和使用

---

## 六、前端页面设计

### 6.1 页面结构

```text
/models（模型管理页，一级导航）
├── 顶部：页面标题 + 描述 + "添加供应商"按钮
│
├── 供应商区域
│   ├── 筛选：按类型、按状态
│   ├── 供应商卡片列表
│   │   ├── 供应商信息（名称、类型、状态、模型数）
│   │   ├── 操作按钮（编辑、测试连接、启用/禁用、删除）
│   │   └── 展开区域：该供应商下的模型列表 + "添加模型"按钮
│   │       ├── 每个模型：名称、类型标签、操作（编辑、测试、启用/禁用、删除）
│   │       └── 添加模型按钮
│   └── 分页
│
└── 模型总览区域（可折叠或 Tab 切换）
    ├── 筛选：按模型类型、按启用状态
    ├── 全局模型表格（跨供应商）
    └── 分页
```

### 6.2 弹窗

| 弹窗 | 触发 | 内容 |
|------|------|------|
| 添加供应商 | 点击"添加供应商"按钮 | 供应商表单 |
| 编辑供应商 | 供应商卡片"编辑"按钮 | 供应商表单（类型不可改） |
| 添加模型 | 供应商展开区"添加模型"按钮 | 模型表单 |
| 编辑模型 | 模型条目"编辑"按钮 | 模型表单（标识和供应商不可改） |

### 6.3 前端状态

模型管理页不需要全局 Zustand Store。页面状态（列表数据、筛选条件、弹窗开关、loading）使用组件本地状态（`useState`）即可。

理由：模型管理页的数据只在当前页面使用，不跨页面共享。

### 6.4 前端 API 文件

```text
zify-web/src/api/modelApi.ts
```

包含：

- `createProvider` / `listProviders` / `getProvider` / `updateProvider` / `deleteProvider`
- `updateProviderStatus` / `testProvider`
- `createModel` / `listModels` / `getModel` / `updateModel` / `deleteModel`
- `updateModelEnabled` / `testModel`

---

## 七、与其他模块的集成

### 7.1 被引用场景

| 模块 | 引用方式 | 说明 |
|------|---------|------|
| Agent 创建 | 模型下拉框 | `ModelFacade.listAvailableModels("LLM")`，选择后存 `agent.model_id` |
| Agent 对话 | 模型调用 | `ModelFacade.chat(modelId, messages, options)` |
| 知识库创建 | Embedding 模型下拉框 | `ModelFacade.listAvailableModels("EMBEDDING")`，选择后存 `knowledge.embedding_model_id` |
| 知识库 Embedding | 模型调用 | `ModelFacade.embed(modelId, texts)` |
| 工作流 LLM 节点 | 模型下拉框 | `ModelFacade.listAvailableModels("LLM")`，选择后存节点配置 |

### 7.2 模型可用性校验时机

| 时机 | 校验内容 | 失败处理 |
|------|---------|---------|
| Agent 保存 | 模型存在 + 未删除 + 已启用 + 供应商已启用 | 返回错误，阻止保存 |
| Agent 对话启动 | 同上 | 返回错误提示 |
| 知识库创建 | Embedding 模型同上校验 | 返回错误，阻止保存 |
| 工作流 LLM 节点保存 | 模型同上校验 | 返回错误，阻止保存 |
| 工作流执行 LLM 节点 | 同上 | 节点失败，记录运行日志 |

---

## 八、业务规则汇总

### 8.1 供应商规则

| 编号 | 规则 |
|------|------|
| P-01 | 供应商名称在未删除数据中唯一 |
| P-02 | 供应商类型创建后不可修改 |
| P-03 | API Key 加密存储（AES），日志中禁止输出 |
| P-04 | Base URL 存储前去掉末尾 `/` |
| P-05 | 同一供应商类型可添加多个实例 |
| P-06 | 禁用供应商后，其下所有模型不参与调用（不论模型自身 enabled 状态） |
| P-07 | 删除供应商时级联软删除其下所有模型 |
| P-08 | 供应商连通性测试不持久化，每次实时检测 |

### 8.2 模型规则

| 编号 | 规则 |
|------|------|
| M-01 | 同一供应商下，未删除的模型标识唯一 |
| M-02 | 模型标识创建后不可修改 |
| M-03 | 模型创建后不可转移到其他供应商 |
| M-04 | display_name 为空时，展示层 fallback 到 model_name |
| M-05 | 模型可用 = 供应商启用 + 模型启用 + 均未删除 |
| M-06 | 模型可用性测试不持久化，每次实时检测 |
| M-07 | 删除模型不自动解除 Agent / 知识库 / 工作流节点的引用 |
| M-08 | model_name 由用户手填，不提供下拉枚举 |

### 8.3 默认参数规则

| 编号 | 规则 |
|------|------|
| D-01 | temperature 范围 0~1（兼容 Anthropic 的保守范围） |
| D-02 | topP 范围 0~1 |
| D-03 | maxTokens 不设上限（由 API 侧校验模型限制） |
| D-04 | 参数优先级：Agent 参数 > 工作流节点参数 > 模型默认参数 > 全局配置 |
| D-05 | defaultParams 为 null 时表示使用全局配置默认值 |
| D-06 | 只暴露 temperature、maxTokens、topP 三个参数给用户 |

### 8.4 跨模块调用规则

| 编号 | 规则 |
|------|------|
| C-01 | 其他模块只能通过 `ModelFacade` 获取模型信息，禁止直接查 model 表 |
| C-02 | API Key 只在 model 模块的 `infrastructure/client/` 包内使用 |
| C-03 | Agent / 知识库 / 工作流节点保存时必须校验模型可用性 |
| C-04 | 模型不可用时，已配置的引用不自动清除，由用户手动处理 |

---

## 九、错误码

| 错误码 | 说明 | HTTP 状态 |
|--------|------|----------|
| `PROVIDER_NAME_DUPLICATE` | 供应商名称已存在 | 400 |
| `PROVIDER_NOT_FOUND` | 供应商不存在或已删除 | 404 |
| `PROVIDER_TYPE_IMMUTABLE` | 供应商类型不可修改 | 400 |
| `MODEL_NAME_DUPLICATE` | 同一供应商下模型标识已存在 | 400 |
| `MODEL_NOT_FOUND` | 模型不存在或已删除 | 404 |
| `MODEL_NAME_IMMUTABLE` | 模型标识不可修改 | 400 |
| `MODEL_PROVIDER_IMMUTABLE` | 模型所属供应商不可修改 | 400 |
| `MODEL_UNAVAILABLE` | 模型不可用（未启用、供应商未启用、或已删除） | 400 |
| `PROVIDER_TEST_FAILED` | 供应商连接测试失败（作为测试结果返回，非异常） | 200 |
| `MODEL_TEST_FAILED` | 模型可用性测试失败（作为测试结果返回，非异常） | 200 |
