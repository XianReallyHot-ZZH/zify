# Prompt 03：模型管理模块全栈实施

你是 Zify 项目的 AI 编码助手。现在要实施模型管理模块的全部前后端功能。

## 一、项目背景

Zify 是模块化单体 AI 应用。模型管理模块位于 `zify-model` 子模块，负责管理 LLM 模型供应商的连接配置和模型定义，为 Agent、工作流、知识库提供模型调用能力。

后端技术栈：Java 21、Spring Boot 4.0、Spring AI 2.0、MyBatis-Plus、MySQL 8.x、Flyway。

前端技术栈：React 19 + TypeScript + Vite 8 + Ant Design 6 + Zustand 5 + Axios。

项目已有工程骨架和基础组件（BaseEntity、IdGenerator、SecretEncryptor、MaskUtils、Result、ErrorCode、BusinessException、PageRequest、PageResult、GlobalExceptionHandler、TraceIdFilter 等）。

你必须先阅读以下文档：

```text
CLAUDE.md
glm-docs/06-zify-code-organization.md
glm-docs/10-zify-database-spec.md
docs-prd/model-manager/01-data-model.md
docs-prd/model-manager/02-functional-spec.md
```

再阅读以下已有的基础组件代码，理解现有能力后复用，不要重复造轮子：

```text
zify-common 中的 BaseEntity、IdGenerator、SecretEncryptor、MaskUtils、Result、
  ErrorCode、BusinessException、PageRequest、PageResult、MybatisMetaObjectHandler
zify-model 中的已有代码结构
zify-web/src/api/request.ts（apiGet/apiPost/apiPut/apiDelete）
zify-web/src/shared/ui/PageHeader.tsx
zify-web/src/shared/hooks/useOffsetPagination.ts
zify-web/src/shared/hooks/useConfirm.ts
zify-web/src/shared/utils/format.ts
zify-web/src/pages/models/ModelPage.tsx（当前骨架）
```

## 二、硬性约束

1. 跨模块调用只能走目标模块 `api` 包下的 Facade 接口。
2. Controller 只能调用本模块 Service。
3. Entity / Mapper / Repository / Service 禁止跨模块引用。
4. HTTP Request / Response 不能进入 domain 层。DTO 定义在 `api/dto` 包下。
5. Facade 不能返回 Entity 或 MyBatis-Plus 的 Page 对象。
6. 所有数据库结构变更使用 Flyway 迁移脚本。
7. MySQL 表必须包含 `id`(CHAR(36)) / `created_at`(DATETIME(3)) / `updated_at`(DATETIME(3)) / `is_deleted`(TINYINT)。
8. 软删除唯一约束使用 generated column，禁止 `UNIQUE(field, is_deleted)`。
9. 禁止在事务内调用外部 API（健康测试不属于事务操作）。
10. 日志禁止输出 API Key、密码、Token。API Key 加密存储。
11. 状态字段使用 `VARCHAR(32)`，不用 MySQL `ENUM`。
12. 异常使用 `BusinessException` + `ErrorCode` 枚举，禁止硬编码错误码。
13. 不引入项目技术栈以外的新依赖。
14. 前端使用相对 import（`../../`），不用 `@/` 路径别名。
15. 前端类型使用 `type` 关键字，不用 `interface`。
16. 前端 Store 不发 HTTP 请求。页面局部状态用 `useState`。

## 三、模块代码结构

`zify-model` 子模块的包结构：

```text
com.zify.model/
├── api/
│   ├── dto/
│   │   ├── provider/
│   │   │   ├── CreateProviderRequest.java
│   │   │   ├── UpdateProviderRequest.java
│   │   │   ├── UpdateProviderStatusRequest.java
│   │   │   ├── ProviderResponse.java
│   │   │   ├── ProviderListQuery.java
│   │   │   └── ProviderTestResult.java
│   │   └── model/
│   │       ├── CreateModelRequest.java
│   │       ├── UpdateModelRequest.java
│   │       ├── UpdateModelEnabledRequest.java
│   │       ├── ModelResponse.java
│   │       ├── ModelListQuery.java
│   │       ├── ModelTestResult.java
│   │       └── ModelSummary.java
│   ├── ModelFacade.java
│   └── ModelFacadeImpl.java
├── config/
│   └── ModelModuleConfig.java
├── controller/
│   ├── ModelProviderController.java
│   └── ModelController.java
├── domain/
│   ├── constant/
│   │   └── ModelErrorCode.java
│   ├── ModelProviderService.java
│   └── ModelService.java
├── infrastructure/
│   ├── entity/
│   │   ├── ModelProviderEntity.java
│   │   └── ModelEntity.java
│   ├── mapper/
│   │   ├── ModelProviderMapper.java
│   │   └── ModelMapper.java
│   └── converter/
│       ├── ModelProviderConverter.java
│       └── ModelConverter.java
```

---

## 四、实施任务清单

### 任务 1：数据库迁移脚本

#### 目标

创建 `model_provider` 和 `model` 两张表。

#### 需要做

在 `zify-app/src/main/resources/db/migration/` 下创建迁移脚本。文件名中的版本号需要查看该目录下已有的最大版本号，递增即可。命名格式：`V{N}__model__create_model_tables.sql`。

脚本内容严格按照 `docs-prd/model-manager/01-data-model.md` 中的 DDL。两张表写在一个迁移脚本中。

`model_provider` 表 DDL：

```sql
CREATE TABLE `model_provider` (
    `id`            CHAR(36)     NOT NULL COMMENT '供应商 ID',
    `created_at`    DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间，UTC',
    `updated_at`    DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间，UTC',
    `is_deleted`    TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除：0 正常，1 已删除',
    `created_by`    CHAR(36)     NULL     COMMENT '创建人用户ID',
    `updated_by`    CHAR(36)     NULL     COMMENT '更新人用户ID',

    `name`          VARCHAR(128) NOT NULL COMMENT '供应商名称',
    `provider_type` VARCHAR(32)  NOT NULL COMMENT '供应商类型：OPENAI / ANTHROPIC / OPENAI_COMPATIBLE',
    `api_key`       VARCHAR(512) NULL     COMMENT 'API Key（AES 加密存储）',
    `base_url`      VARCHAR(512) NOT NULL COMMENT 'API Base URL',
    `extra_config`  JSON         NULL     COMMENT '供应商特有配置',
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

`model` 表 DDL：

```sql
CREATE TABLE `model` (
    `id`              CHAR(36)     NOT NULL COMMENT '模型 ID',
    `created_at`      DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间，UTC',
    `updated_at`      DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间，UTC',
    `is_deleted`      TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除：0 正常，1 已删除',

    `provider_id`     CHAR(36)     NOT NULL COMMENT '所属供应商 ID',
    `model_name`      VARCHAR(255) NOT NULL COMMENT '模型标识',
    `display_name`    VARCHAR(128) NULL     COMMENT '显示名称',
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

#### 验收标准

- `mvn package -DskipTests` 通过。
- 迁移脚本放在正确目录下，文件名版本号正确。

---

### 任务 2：Entity + Mapper + ErrorCode + Converter

#### 目标

创建数据层类：Entity、Mapper、Converter 和模块错误码枚举。

#### 需要做

**2.1 ModelProviderEntity**

路径：`com.zify.model.infrastructure.entity.ModelProviderEntity`

继承 `BaseEntity`（来自 `zify-common`）。使用 MyBatis-Plus 注解映射表名和字段。

字段（不含 BaseEntity 已有的）：

```java
@TableName("model_provider")
public class ModelProviderEntity extends BaseEntity {
    private String createdBy;
    private String updatedBy;
    private String name;
    private String providerType;
    private String apiKey;       // 加密后的密文
    private String baseUrl;
    private Map<String, Object> extraConfig;  // JSON 字段，使用 JacksonTypeHandler
    private String status;
}
```

`extraConfig` 字段使用 MyBatis-Plus `@TableField(typeHandler = JacksonTypeHandler.class)` 注解，并配置 `autoResultMap = true` 在 `@TableName` 上。

`apiKey` 对应数据库 `api_key`，使用 MyBatis-Plus `@TableField("api_key")`。

所有字段使用 MyBatis-Plus 默认驼峰映射或 `@TableField` 显式指定。

**2.2 ModelEntity**

路径：`com.zify.model.infrastructure.entity.ModelEntity`

继承 `BaseEntity`。字段：

```java
@TableName("model")
public class ModelEntity extends BaseEntity {
    private String providerId;
    private String modelName;
    private String displayName;
    private String modelType;
    private Integer enabled;
    private Map<String, Object> defaultParams;  // JSON 字段，使用 JacksonTypeHandler
}
```

`defaultParams` 同样使用 `JacksonTypeHandler`。

**2.3 Mapper 接口**

`ModelProviderMapper`：

```java
路径：com.zify.model.infrastructure.mapper.ModelProviderMapper
public interface ModelProviderMapper extends BaseMapper<ModelProviderEntity> {
}
```

`ModelMapper`：

```java
路径：com.zify.model.infrastructure.mapper.ModelMapper
public interface ModelMapper extends BaseMapper<ModelEntity> {
}
```

**2.4 Converter**

`ModelProviderConverter`（路径：`com.zify.model.infrastructure.converter.ModelProviderConverter`）：

纯静态方法工具类，提供：
- `toResponse(entity, modelCount)` → `ProviderResponse`（modelCount 需要外部传入）
- `toEntity(request)` → `ModelProviderEntity`（创建时用）
- `updateEntity(entity, request)` → `void`（更新时用）

`ModelConverter`（路径：`com.zify.model.infrastructure.converter.ModelConverter`）：

- `toResponse(entity, providerName, providerType, providerStatus)` → `ModelResponse`
- `toEntity(request, providerId)` → `ModelEntity`
- `updateEntity(entity, request)` → `void`
- `toSummary(entity, providerName, providerType)` → `ModelSummary`

Converter 只做字段拷贝，不含业务逻辑。可以使用手动 getter/setter，不强制要求 MapStruct。

**2.5 ModelErrorCode**

路径：`com.zify.model.domain.constant.ModelErrorCode`

实现 `zify-common` 中 `ErrorCode` 接口（如果 `ErrorCode` 是接口的话）或遵循已有的错误码模式。

枚举值：

```text
PROVIDER_NAME_DUPLICATE      "供应商名称已存在"
PROVIDER_NOT_FOUND           "供应商不存在"
PROVIDER_TYPE_IMMUTABLE      "供应商类型不可修改"
MODEL_NAME_DUPLICATE         "同一供应商下模型标识已存在"
MODEL_NOT_FOUND              "模型不存在"
MODEL_NAME_IMMUTABLE         "模型标识不可修改"
MODEL_PROVIDER_IMMUTABLE     "模型所属供应商不可修改"
MODEL_UNAVAILABLE            "模型不可用"
PROVIDER_TEST_ERROR          "供应商连接测试失败"
MODEL_TEST_ERROR             "模型可用性测试失败"
```

#### 验收标准

- 所有类在正确的包路径下。
- Entity 正确继承 BaseEntity，字段与数据库列对应。
- JSON 字段使用了 `JacksonTypeHandler`。
- `mvn package -DskipTests` 通过。

---

### 任务 3：DTO 类

#### 目标

创建所有 Facade 和 Controller 使用的请求/响应 DTO。

#### 需要做

所有 DTO 放在 `com.zify.model.api.dto` 包下，按 `provider/` 和 `model/` 子包组织。

**provider 子包 DTO：**

`CreateProviderRequest`：

```java
public class CreateProviderRequest {
    @NotBlank(message = "供应商名称不能为空")
    private String name;
    @NotBlank(message = "供应商类型不能为空")
    private String providerType;
    private String apiKey;                          // 可选
    @NotBlank(message = "Base URL 不能为空")
    private String baseUrl;
    private Map<String, Object> extraConfig;        // 可选
}
```

`UpdateProviderRequest`：

```java
public class UpdateProviderRequest {
    private String name;
    private String apiKey;                          // null 表示不修改
    private String baseUrl;
    private Map<String, Object> extraConfig;
}
```

`UpdateProviderStatusRequest`：

```java
public class UpdateProviderStatusRequest {
    @NotBlank(message = "状态不能为空")
    private String status;
}
```

`ProviderListQuery`：继承 `PageRequest`（来自 `zify-common`），追加筛选字段。

```java
public class ProviderListQuery extends PageRequest {
    private String providerType;
    private String status;
}
```

`ProviderResponse`：

```java
public class ProviderResponse {
    private String id;
    private String name;
    private String providerType;
    private String baseUrl;
    private Map<String, Object> extraConfig;
    private String status;
    private Boolean hasApiKey;
    private Integer modelCount;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
```

`ProviderTestResult`：

```java
public class ProviderTestResult {
    private Boolean success;
    private String message;
    private Long latencyMs;
    private List<String> availableModels;
}
```

**model 子包 DTO：**

`CreateModelRequest`：

```java
public class CreateModelRequest {
    @NotBlank(message = "模型标识不能为空")
    private String modelName;
    private String displayName;
    @NotBlank(message = "模型类型不能为空")
    private String modelType;
    private Boolean enabled = true;
}
```

`UpdateModelRequest`：

```java
public class UpdateModelRequest {
    private String displayName;
    private String modelType;
    private Boolean enabled;
    private Map<String, Object> defaultParams;
}
```

`UpdateModelEnabledRequest`：

```java
public class UpdateModelEnabledRequest {
    @NotNull(message = "启用状态不能为空")
    private Boolean enabled;
}
```

`ModelListQuery`：继承 `PageRequest`。

```java
public class ModelListQuery extends PageRequest {
    private String modelType;
    private Boolean enabled;
    private String providerId;
}
```

`ModelResponse`：

```java
public class ModelResponse {
    private String id;
    private String providerId;
    private String modelName;
    private String displayName;
    private String modelType;
    private Boolean enabled;
    private Map<String, Object> defaultParams;
    private String providerName;
    private String providerType;
    private String providerStatus;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
```

`ModelTestResult`：

```java
public class ModelTestResult {
    private Boolean success;
    private String message;
    private Long latencyMs;
    private String errorDetail;
}
```

`ModelSummary`（供 Facade 返回给其他模块用于下拉框）：

```java
public class ModelSummary {
    private String id;
    private String displayName;
    private String modelName;
    private String providerName;
    private String providerType;
}
```

#### 规则

- 所有 DTO 使用 `@Data`（Lombok）或手写 getter/setter，与项目已有代码风格一致。
- 校验注解使用 `jakarta.validation`（Spring Boot 4.0 用 Jakarta EE）。
- `ProviderListQuery` 和 `ModelListQuery` 继承 `zify-common` 的 `PageRequest`。
- 不 import 任何 Entity 类。

#### 验收标准

- 所有 DTO 在正确包路径下。
- 校验注解正确。
- `mvn package -DskipTests` 通过。

---

### 任务 4：ModelProviderService

#### 目标

实现供应商的 CRUD、启用/禁用、级联删除。

#### 需要做

路径：`com.zify.model.domain.ModelProviderService`

使用 `@Service` 注解。注入 `ModelProviderMapper`、`ModelMapper`（级联删除需要）、`SecretEncryptor`（来自 `zify-common`）。

**方法清单：**

```java
public PageResult<ProviderResponse> listProviders(ProviderListQuery query)
public ProviderResponse getProvider(String id)
public ProviderResponse createProvider(CreateProviderRequest request)
public ProviderResponse updateProvider(String id, UpdateProviderRequest request)
public void deleteProvider(String id)
public void updateStatus(String id, String status)
```

**各方法业务规则：**

`listProviders`：
- 使用 MyBatis-Plus `Page` + `LambdaQueryWrapper` 分页查询
- 等值筛选：`providerType`（不为空时）、`status`（不为空时）
- 排序：`created_at DESC`
- 每条记录需要关联查 `modelCount`（`SELECT COUNT(*) FROM model WHERE provider_id = ? AND is_deleted = 0`）
- 不返回 `apiKey`，`hasApiKey` 根据 `apiKey != null` 判断
- 使用 Converter 转换为 `ProviderResponse`

`getProvider`：
- 按 ID 查询，`is_deleted = 0`
- 不存在抛 `BusinessException(PROVIDER_NOT_FOUND)`
- 不返回 `apiKey`，设置 `hasApiKey`
- 关联查 `modelCount`

`createProvider`：
- 校验 `name` 未删除唯一（查 `is_deleted = 0 AND name = ?`，存在则抛 `PROVIDER_NAME_DUPLICATE`）
- `baseUrl` 去掉末尾 `/`
- `apiKey` 不为空时调用 `SecretEncryptor.encrypt()` 加密后存储
- `status` 默认 `ACTIVE`
- 使用 `IdGenerator.uuid()` 生成 ID（如果 BaseEntity 的 MetaObjectHandler 已自动生成则不需要手动设）
- 使用 Converter 转换并返回

`updateProvider`：
- 先查实体，不存在抛异常
- `providerType` 不允许修改（如果 request 中带了 providerType，直接忽略或抛异常）
- `name` 修改时校验唯一性
- `apiKey`：不为空则加密后更新，为 null 则不修改
- `baseUrl` 修改时去掉末尾 `/`
- `extraConfig` 直接覆盖

`deleteProvider`：
- 先查实体，不存在抛异常
- 软删除该供应商（`is_deleted = 1`）
- 软删除该供应商下所有模型（`UPDATE model SET is_deleted = 1 WHERE provider_id = ? AND is_deleted = 0`）

`updateStatus`：
- 先查实体，不存在抛异常
- 校验 `status` 值为 `ACTIVE` 或 `INACTIVE`
- 更新 `status`

#### 验收标准

- 所有方法遵循上述业务规则。
- 不在 Service 中直接返回 Entity（通过 Converter 转换为 DTO）。
- API Key 加密存储，不输出明文。
- `mvn package -DskipTests` 通过。

---

### 任务 5：ModelService + 健康测试

#### 目标

实现模型的 CRUD、启用/禁用、供应商连通性测试、模型可用性测试。

#### 需要做

路径：`com.zify.model.domain.ModelService`

使用 `@Service` 注解。注入 `ModelMapper`、`ModelProviderMapper`、`SecretEncryptor`。

需要注入 `RestClient`（使用 `zify-common` 中配置的通用 `RestClient.Builder` 或在 `zify-model` 的 config 中新建一个专用于测试的 `RestClient` Bean）。

**方法清单：**

```java
// 模型 CRUD
public PageResult<ModelResponse> listModels(ModelListQuery query)
public List<ModelResponse> listProviderModels(String providerId)
public ModelResponse getModel(String id)
public ModelResponse createModel(String providerId, CreateModelRequest request)
public ModelResponse updateModel(String id, UpdateModelRequest request)
public void deleteModel(String id)
public void updateEnabled(String id, Boolean enabled)

// 健康测试
public ProviderTestResult testProvider(String providerId)
public ModelTestResult testModel(String modelId)

// 供 Facade 调用
public List<ModelSummary> listAvailableModels(String modelType)
```

**模型 CRUD 业务规则：**

`listModels`：
- 关联查 provider 信息（providerName、providerType、providerStatus）
- 等值筛选：`modelType`、`enabled`、`providerId`
- 不返回 `defaultParams`（列表接口避免大字段）

`listProviderModels`：
- 查指定供应商下所有未删除模型，不分页（小量数据）
- 不返回 `defaultParams`
- 校验供应商存在且未删除

`createModel`：
- 校验供应商存在且未删除
- 校验该供应商下 `modelName` 未删除唯一
- 使用 Converter 转换并保存

`updateModel`：
- `modelName` 不允许修改
- `providerId` 不允许修改
- 其他字段可修改

`deleteModel`：
- 软删除（`is_deleted = 1`）
- 不自动解除 Agent / 知识库 / 工作流的引用

`updateEnabled`：
- 更新 `enabled` 字段

`listAvailableModels`：
- 查询条件：`model.is_deleted = 0 AND model.enabled = 1 AND mp.is_deleted = 0 AND mp.status = 'ACTIVE'`
- 可选按 `modelType` 筛选
- 返回 `List<ModelSummary>`

**健康测试业务规则：**

`testProvider`：
- 取出供应商配置，解密 API Key
- 按 `providerType` 选择测试方式：
  - `OPENAI` / `OPENAI_COMPATIBLE`：`GET {baseUrl}/v1/models`，header `Authorization: Bearer {apiKey}`。Ollama 无 key 时不带 header
  - `ANTHROPIC`：`POST {baseUrl}/v1/messages`，header `x-api-key: {apiKey}` + `anthropic-version: {extraConfig.apiVersion}`，body `{"model":"claude-sonnet-4-20250514","messages":[{"role":"user","content":"hi"}],"max_tokens":1}`
- 超时 15 秒
- 记录耗时
- 成功：返回 `ProviderTestResult(success=true, message="连接成功", latencyMs, availableModels)`
- 失败：返回 `ProviderTestResult(success=false, message=错误信息, latencyMs)`
- OpenAI 兼容类成功时，从响应中提取模型名列表放入 `availableModels`
- **测试失败不抛异常**，返回 `success=false` 的结果

`testModel`：
- 先校验模型存在、未删除
- 先校验供应商 `status = ACTIVE` 且未删除
- 取出模型和供应商配置，解密 API Key
- 按 `modelType` 选择测试方式：
  - `LLM`：发最短 chat 请求（`max_tokens=1`），按供应商类型构造不同请求格式
  - `EMBEDDING`：发最短 embedding 请求（`input=["test"]`）
- 超时 15 秒
- 成功：返回 `ModelTestResult(success=true, message="模型可用", latencyMs)`
- 失败：返回 `ModelTestResult(success=false, message="模型不可用", latencyMs, errorDetail=具体错误)`
- **测试失败不抛异常**

#### RestClient 配置

在 `com.zify.model.config.ModelModuleConfig` 中声明测试用 `RestClient`：

```java
@Bean
public RestClient modelTestRestClient(RestClient.Builder builder) {
    // 连接超时 5s，读取超时 15s
    HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();
    JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
    requestFactory.setReadTimeout(Duration.ofSeconds(15));
    return builder.requestFactory(requestFactory).build();
}
```

#### 验收标准

- 模型 CRUD 遵循业务规则。
- 健康测试失败时返回 `success=false`，不抛异常。
- 测试结果不持久化。
- `mvn package -DskipTests` 通过。

---

### 任务 6：ModelFacade + FacadeImpl

#### 目标

创建跨模块调用的 Facade 接口和实现。

#### 需要做

**ModelFacade 接口**

路径：`com.zify.model.api.ModelFacade`

```java
public interface ModelFacade {
    /**
     * 查询可用的模型列表（供 Agent / 工作流 / 知识库 下拉框使用）
     * 条件：model.enabled=1 AND provider.status=ACTIVE AND 均未删除
     */
    List<ModelSummary> listAvailableModels(String modelType);
}
```

**ModelFacadeImpl**

路径：`com.zify.model.api.ModelFacadeImpl`

```java
@Service
@RequiredArgsConstructor
public class ModelFacadeImpl implements ModelFacade {
    private final ModelService modelService;

    @Override
    public List<ModelSummary> listAvailableModels(String modelType) {
        return modelService.listAvailableModels(modelType);
    }
}
```

#### 规则

- Facade 接口在 `api` 包下，实现在 `api` 包下（不是 `domain` 包）。
- Facade 不返回 Entity。
- 其他模块（agent、workflow、knowledge）后续通过注入 `ModelFacade` 获取模型列表。

#### 验收标准

- 接口和实现类在正确包路径下。
- `mvn package -DskipTests` 通过。

---

### 任务 7：Controller 层

#### 目标

创建供应商和模型的 REST 控制器。

#### 需要做

**ModelProviderController**

路径：`com.zify.model.controller.ModelProviderController`

```java
@RestController
@RequestMapping("/api/model/providers")
@RequiredArgsConstructor
public class ModelProviderController {
    private final ModelProviderService providerService;
```

接口清单：

```text
POST   /api/model/providers              创建供应商
GET    /api/model/providers              查询供应商列表（OFFSET 分页）
GET    /api/model/providers/{id}         查询供应商详情
PUT    /api/model/providers/{id}         更新供应商
DELETE /api/model/providers/{id}         删除供应商
PUT    /api/model/providers/{id}/status  更新供应商状态
POST   /api/model/providers/{id}/test    测试供应商连接
```

各接口参数和返回值：

- `POST /` → `@Valid @RequestBody CreateProviderRequest` → `Result<ProviderResponse>`
- `GET /` → `ProviderListQuery`（Query 参数）→ `Result<PageResult<ProviderResponse>>`
- `GET /{id}` → `@PathVariable String id` → `Result<ProviderResponse>`
- `PUT /{id}` → `@PathVariable String id` + `@Valid @RequestBody UpdateProviderRequest` → `Result<ProviderResponse>`
- `DELETE /{id}` → `@PathVariable String id` → `Result<Void>`
- `PUT /{id}/status` → `@PathVariable String id` + `@Valid @RequestBody UpdateProviderStatusRequest` → `Result<Void>`
- `POST /{id}/test` → `@PathVariable String id` → `Result<ProviderTestResult>`

**ModelController**

路径：`com.zify.model.controller.ModelController`

```java
@RestController
@RequestMapping("/api/model")
@RequiredArgsConstructor
public class ModelController {
    private final ModelService modelService;
```

接口清单：

```text
POST   /api/model/providers/{providerId}/models  添加模型
GET    /api/model/models                         查询全局模型列表（OFFSET 分页）
GET    /api/model/providers/{providerId}/models   查询供应商下模型列表
GET    /api/model/models/{id}                    查询模型详情
PUT    /api/model/models/{id}                    更新模型
DELETE /api/model/models/{id}                    删除模型
PUT    /api/model/models/{id}/enabled            更新模型启用状态
POST   /api/model/models/{id}/test               测试模型可用性
```

各接口参数和返回值：

- `POST /providers/{providerId}/models` → `@PathVariable String providerId` + `@Valid @RequestBody CreateModelRequest` → `Result<ModelResponse>`
- `GET /models` → `ModelListQuery`（Query 参数）→ `Result<PageResult<ModelResponse>>`
- `GET /providers/{providerId}/models` → `@PathVariable String providerId` → `Result<List<ModelResponse>>`
- `GET /models/{id}` → `@PathVariable String id` → `Result<ModelResponse>`
- `PUT /models/{id}` → `@PathVariable String id` + `@Valid @RequestBody UpdateModelRequest` → `Result<ModelResponse>`
- `DELETE /models/{id}` → `@PathVariable String id` → `Result<Void>`
- `PUT /models/{id}/enabled` → `@PathVariable String id` + `@Valid @RequestBody UpdateModelEnabledRequest` → `Result<Void>`
- `POST /models/{id}/test` → `@PathVariable String id` → `Result<ModelTestResult>`

#### 规则

- Controller 只调用 Service，不直接调用 Mapper。
- Controller 不处理业务逻辑，只做参数接收和结果包装。
- 所有接口返回 `Result<T>`（来自 `zify-common`）。
- 使用 `@Valid` 触发请求体校验。
- 删除和测试连接接口的路径遵循 RESTful 风格。

#### 验收标准

- 所有接口路径与上述定义一致。
- Controller 不包含业务逻辑。
- `mvn package -DskipTests` 通过。

---

### 任务 8：前端类型定义 + API 层

#### 目标

创建模型管理模块的前端 TypeScript 类型和 API 调用函数。

#### 需要做

**8.1 创建 `zify-web/src/api/modelApi.ts`**

在文件顶部定义所有类型（与后端 DTO 对齐，对齐 HTTP request/response，不对齐 Facade DTO），然后定义 API 函数。

所有类型使用 `type` 关键字，不用 `interface`。

```typescript
import { apiGet, apiPost, apiPut, apiDelete } from './request'
import type { OffsetPageResponse } from '../types/api'

// ─── Provider Types ───

type CreateProviderRequest = {
  name: string
  providerType: string
  apiKey?: string
  baseUrl: string
  extraConfig?: Record<string, unknown>
}

type UpdateProviderRequest = {
  name?: string
  apiKey?: string
  baseUrl?: string
  extraConfig?: Record<string, unknown>
}

type ProviderListQuery = {
  page?: number
  pageSize?: number
  providerType?: string
  status?: string
}

type ProviderResponse = {
  id: string
  name: string
  providerType: string
  baseUrl: string
  extraConfig: Record<string, unknown> | null
  status: string
  hasApiKey: boolean
  modelCount: number
  createdAt: string
  updatedAt: string
}

type ProviderTestResult = {
  success: boolean
  message: string
  latencyMs: number
  availableModels: string[] | null
}

// ─── Model Types ───

type CreateModelRequest = {
  modelName: string
  displayName?: string
  modelType: string
  enabled?: boolean
}

type UpdateModelRequest = {
  displayName?: string
  modelType?: string
  enabled?: boolean
  defaultParams?: Record<string, unknown>
}

type ModelListQuery = {
  page?: number
  pageSize?: number
  modelType?: string
  enabled?: boolean | string
  providerId?: string
}

type ModelResponse = {
  id: string
  providerId: string
  modelName: string
  displayName: string | null
  modelType: string
  enabled: boolean
  defaultParams: Record<string, unknown> | null
  providerName: string
  providerType: string
  providerStatus: string
  createdAt: string
  updatedAt: string
}

type ModelTestResult = {
  success: boolean
  message: string
  latencyMs: number
  errorDetail: string | null
}

// ─── Provider API ───

function createProvider(data: CreateModelRequest): Promise<ProviderResponse> {
  return apiPost('/api/model/providers', data)
}

function listProviders(query?: ProviderListQuery): Promise<OffsetPageResponse<ProviderResponse>> {
  return apiGet('/api/model/providers', query)
}

function getProvider(id: string): Promise<ProviderResponse> {
  return apiGet(`/api/model/providers/${id}`)
}

function updateProvider(id: string, data: UpdateProviderRequest): Promise<ProviderResponse> {
  return apiPut(`/api/model/providers/${id}`, data)
}

function deleteProvider(id: string): Promise<void> {
  return apiDelete(`/api/model/providers/${id}`)
}

function updateProviderStatus(id: string, status: string): Promise<void> {
  return apiPut(`/api/model/providers/${id}/status`, { status })
}

function testProvider(id: string): Promise<ProviderTestResult> {
  return apiPost(`/api/model/providers/${id}/test`)
}

// ─── Model API ───

function createModel(providerId: string, data: CreateModelRequest): Promise<ModelResponse> {
  return apiPost(`/api/model/providers/${providerId}/models`, data)
}

function listModels(query?: ModelListQuery): Promise<OffsetPageResponse<ModelResponse>> {
  return apiGet('/api/model/models', query)
}

function listProviderModels(providerId: string): Promise<ModelResponse[]> {
  return apiGet(`/api/model/providers/${providerId}/models`)
}

function getModel(id: string): Promise<ModelResponse> {
  return apiGet(`/api/model/models/${id}`)
}

function updateModel(id: string, data: UpdateModelRequest): Promise<ModelResponse> {
  return apiPut(`/api/model/models/${id}`, data)
}

function deleteModel(id: string): Promise<void> {
  return apiDelete(`/api/model/models/${id}`)
}

function updateModelEnabled(id: string, enabled: boolean): Promise<void> {
  return apiPut(`/api/model/models/${id}/enabled`, { enabled })
}

function testModel(id: string): Promise<ModelTestResult> {
  return apiPost(`/api/model/models/${id}/test`)
}

export type {
  CreateProviderRequest,
  UpdateProviderRequest,
  ProviderListQuery,
  ProviderResponse,
  ProviderTestResult,
  CreateModelRequest,
  UpdateModelRequest,
  ModelListQuery,
  ModelResponse,
  ModelTestResult,
}

export {
  createProvider,
  listProviders,
  getProvider,
  updateProvider,
  deleteProvider,
  updateProviderStatus,
  testProvider,
  createModel,
  listModels,
  listProviderModels,
  getModel,
  updateModel,
  deleteModel,
  updateModelEnabled,
  testModel,
}
```

注意：`apiGet` 的第二个参数如果当前实现不支持 query 对象自动序列化，需要手动调用 `toQueryString` 拼接到 URL 上。请阅读 `api/request.ts` 的 `apiGet` 签名确认。

#### 验收标准

- 所有类型和函数都 export。
- 类型字段名与后端 DTO JSON 字段名完全一致。
- `npm run build` 通过。

---

### 任务 9：前端模型管理页面

#### 目标

实现完整的模型管理页面，包含供应商卡片列表、模型管理、表单弹窗、测试连接。

#### 需要做

**9.1 创建 `zify-web/src/pages/models/components/ProviderFormModal.tsx`**

供应商创建/编辑弹窗组件。

Props：

```typescript
type ProviderFormModalProps = {
  open: boolean
  provider?: ProviderResponse  // undefined = 创建模式，有值 = 编辑模式
  onSubmit: (values: CreateProviderRequest | UpdateProviderRequest) => Promise<void>
  onCancel: () => void
}
```

表单字段（使用 Ant Design Form + Modal）：

| 字段 | 组件 | 必填 | 说明 |
|------|------|------|------|
| 供应商名称 | `Input` | 是 | maxLength 128 |
| 供应商类型 | `Select` | 是 | 选项：OpenAI / Anthropic / OpenAI 兼容。编辑模式下禁用 |
| API Key | `Input.Password` | 否 | 编辑模式下 placeholder="留空则不修改" |
| Base URL | `Input` | 是 | 选择供应商类型时自动预填默认值 |
| API Version | `Input` | 否 | 仅当供应商类型为 Anthropic 时显示，默认 "2023-06-01" |

供应商类型到值的映射：

```typescript
const PROVIDER_TYPE_OPTIONS = [
  { label: 'OpenAI', value: 'OPENAI' },
  { label: 'Anthropic', value: 'ANTHROPIC' },
  { label: 'OpenAI 兼容', value: 'OPENAI_COMPATIBLE' },
]
```

供应商类型到默认 Base URL 的映射：

```typescript
const DEFAULT_BASE_URLS: Record<string, string> = {
  OPENAI: 'https://api.openai.com',
  ANTHROPIC: 'https://api.anthropic.com',
  OPENAI_COMPATIBLE: '',
}
```

当供应商类型变化时，如果 Base URL 当前值等于之前类型的默认值，则自动更新为新类型的默认值。如果用户已手动修改过，则不覆盖。

提交时：
- 创建模式：调用 `onSubmit(createProviderRequest)`
- 编辑模式：`providerType` 不提交，`apiKey` 留空时不提交

**9.2 创建 `zify-web/src/pages/models/components/ModelFormModal.tsx`**

模型创建/编辑弹窗组件。

Props：

```typescript
type ModelFormModalProps = {
  open: boolean
  providerId: string
  model?: ModelResponse  // undefined = 创建模式
  onSubmit: (values: CreateModelRequest | UpdateModelRequest) => Promise<void>
  onCancel: () => void
}
```

表单字段：

| 字段 | 组件 | 必填 | 说明 |
|------|------|------|------|
| 模型标识 | `Input` | 是 | 编辑模式禁用。placeholder="如 gpt-4o、deepseek-chat" |
| 显示名称 | `Input` | 否 | placeholder="如 GPT-4o，为空则使用模型标识" |
| 模型类型 | `Select` | 是 | 选项：LLM / Embedding |
| 启用 | `Switch` | 否 | 默认开启 |

编辑模式额外字段（可折叠的"高级设置"区域）：

| 字段 | 组件 | 说明 |
|------|------|------|
| temperature | `InputNumber` | 范围 0~1，步长 0.1 |
| maxTokens | `InputNumber` | 最小 1 |
| topP | `InputNumber` | 范围 0~1，步长 0.1 |

创建模式不显示高级设置（创建后再编辑配置）。

**9.3 创建 `zify-web/src/pages/models/components/ProviderCard.tsx`**

供应商卡片组件，可展开显示模型列表。

Props：

```typescript
type ProviderCardProps = {
  provider: ProviderResponse
  onEdit: (provider: ProviderResponse) => void
  onDelete: (provider: ProviderResponse) => void
  onToggleStatus: (provider: ProviderResponse) => void
  onTest: (provider: ProviderResponse) => void
  onAddModel: (providerId: string) => void
  onEditModel: (model: ModelResponse) => void
  onDeleteModel: (model: ModelResponse) => void
  onToggleModel: (model: ModelResponse) => void
  onTestModel: (model: ModelResponse) => void
}
```

卡片布局：

```text
┌─────────────────────────────────────────────────────┐
│  🟢/⚫  {provider.name}                    [展开/收起] │
│  {providerTypeLabel} · {provider.modelCount} 个模型    │
│  ───────────────────────────────────────────────────│
│  [编辑]  [测试连接]  [启用/禁用]  [删除]              │
│                                                     │
│  ─────── 展开区域（加载模型列表）────────────────── │
│  {每个模型一行：                                      │
│    modelName | displayName | [LLM/EMB Tag]           │
│    [测试] [启用/禁用] [编辑] [删除]                    │
│  }                                                   │
│  [+ 添加模型]                                        │
└─────────────────────────────────────────────────────┘
```

- 状态圆点：`ACTIVE` → 绿色 🟢，`INACTIVE` → 灰色 ⚫
- 供应商类型标签映射：`OPENAI` → "OpenAI"，`ANTHROPIC` → "Anthropic"，`OPENAI_COMPATIBLE` → "OpenAI 兼容"
- 模型类型标签颜色：`LLM` → 蓝色 Tag，`EMBEDDING` → 绿色 Tag
- 模型启用/禁用使用 Ant Design `Switch` 组件
- 测试按钮点击后显示 loading，结果用 `message.success` 或 `message.error` 展示
- 删除使用 `Popconfirm` 确认
- 展开时调用 `listProviderModels(provider.id)` 加载模型列表，只在首次展开时加载，之后使用缓存
- 供应商禁用时，操作按钮中"禁用"变为"启用"

**9.4 修改 `zify-web/src/pages/models/ModelPage.tsx`**

将现有的骨架页面替换为完整的模型管理页面。

页面布局：

```text
┌─────────────────────────────────────────────────────┐
│  PageHeader: "模型管理" / "配置 LLM 模型供应商和连接"  │
│  extra: [+ 添加供应商] 按钮                           │
├─────────────────────────────────────────────────────┤
│  筛选栏：供应商类型 Select + 状态 Select + 刷新按钮    │
├─────────────────────────────────────────────────────┤
│                                                     │
│  ProviderCard × N（供应商卡片列表）                    │
│                                                     │
├─────────────────────────────────────────────────────┤
│  分页：Ant Design Pagination                         │
└─────────────────────────────────────────────────────┘
```

组件状态（全部使用 `useState`，不用 Zustand Store）：

```typescript
const [providers, setProviders] = useState<ProviderResponse[]>([])
const [total, setTotal] = useState(0)
const [page, setPage] = useState(1)
const [pageSize, setPageSize] = useState(20)
const [loading, setLoading] = useState(false)
const [filterType, setFilterType] = useState<string | undefined>()
const [filterStatus, setFilterStatus] = useState<string | undefined>()
const [providerFormOpen, setProviderFormOpen] = useState(false)
const [editingProvider, setEditingProvider] = useState<ProviderResponse | undefined>()
const [modelFormOpen, setModelFormOpen] = useState(false)
const [currentProviderId, setCurrentProviderId] = useState('')
const [editingModel, setEditingModel] = useState<ModelResponse | undefined>()
const [testLoading, setTestLoading] = useState<Record<string, boolean>>({})
```

数据加载：

```typescript
async function loadProviders() {
  setLoading(true)
  try {
    const result = await listProviders({
      page, pageSize,
      providerType: filterType || undefined,
      status: filterStatus || undefined,
    })
    setProviders(result.records)
    setTotal(result.total)
  } catch (err) {
    message.error('加载供应商列表失败')
  } finally {
    setLoading(false)
  }
}
```

在 `useEffect` 中首次加载和筛选变化时加载。

事件处理：

```typescript
// 打开创建供应商弹窗
function handleCreateProvider() {
  setEditingProvider(undefined)
  setProviderFormOpen(true)
}

// 打开编辑供应商弹窗
function handleEditProvider(provider: ProviderResponse) {
  setEditingProvider(provider)
  setProviderFormOpen(true)
}

// 提交供应商表单（创建或编辑）
async function handleProviderSubmit(values: CreateProviderRequest | UpdateProviderRequest) {
  if (editingProvider) {
    await updateProvider(editingProvider.id, values)
    message.success('供应商更新成功')
  } else {
    await createProvider(values)
    message.success('供应商创建成功')
  }
  setProviderFormOpen(false)
  loadProviders()
}

// 删除供应商
async function handleDeleteProvider(provider: ProviderResponse) {
  await deleteProvider(provider.id)
  message.success('供应商已删除')
  loadProviders()
}

// 启用/禁用供应商
async function handleToggleProviderStatus(provider: ProviderResponse) {
  const newStatus = provider.status === 'ACTIVE' ? 'INACTIVE' : 'ACTIVE'
  await updateProviderStatus(provider.id, newStatus)
  message.success(newStatus === 'ACTIVE' ? '供应商已启用' : '供应商已禁用')
  loadProviders()
}

// 测试供应商连接
async function handleTestProvider(provider: ProviderResponse) {
  setTestLoading(prev => ({ ...prev, [provider.id]: true }))
  try {
    const result = await testProvider(provider.id)
    if (result.success) {
      message.success(result.message)
    } else {
      message.error(result.message)
    }
  } catch {
    message.error('测试请求失败')
  } finally {
    setTestLoading(prev => ({ ...prev, [provider.id]: false }))
  }
}

// 类似地处理模型相关的增删改查、启用/禁用、测试
```

整体结构：

```tsx
<div className="zify-page">
  <PageHeader
    title="模型管理"
    description="配置 LLM 模型供应商和连接"
    extra={
      <Button type="primary" onClick={handleCreateProvider}>
        添加供应商
      </Button>
    }
  />

  {/* 筛选栏 */}
  <div style={{ marginBottom: 16, display: 'flex', gap: 12 }}>
    <Select
      placeholder="供应商类型"
      allowClear
      style={{ width: 160 }}
      value={filterType}
      onChange={setFilterType}
      options={[
        { label: 'OpenAI', value: 'OPENAI' },
        { label: 'Anthropic', value: 'ANTHROPIC' },
        { label: 'OpenAI 兼容', value: 'OPENAI_COMPATIBLE' },
      ]}
    />
    <Select
      placeholder="状态"
      allowClear
      style={{ width: 120 }}
      value={filterStatus}
      onChange={setFilterStatus}
      options={[
        { label: '已启用', value: 'ACTIVE' },
        { label: '已禁用', value: 'INACTIVE' },
      ]}
    />
  </div>

  {/* 供应商卡片列表 */}
  <Spin spinning={loading}>
    {providers.length === 0 && !loading ? (
      <Empty description="暂无供应商，点击上方按钮添加" />
    ) : (
      <div style={{ display: 'flex', flexDirection: 'column', gap: 16 }}>
        {providers.map(provider => (
          <ProviderCard
            key={provider.id}
            provider={provider}
            onEdit={handleEditProvider}
            onDelete={handleDeleteProvider}
            onToggleStatus={handleToggleProviderStatus}
            onTest={handleTestProvider}
            onAddModel={handleAddModel}
            onEditModel={handleEditModel}
            onDeleteModel={handleDeleteModel}
            onToggleModel={handleToggleModel}
            onTestModel={handleTestModel}
          />
        ))}
      </div>
    )}
  </Spin>

  {/* 分页 */}
  {total > pageSize && (
    <div style={{ marginTop: 16, textAlign: 'right' }}>
      <Pagination
        current={page}
        pageSize={pageSize}
        total={total}
        onChange={(p, ps) => { setPage(p); setPageSize(ps) }}
        showSizeChanger={false}
      />
    </div>
  )}

  {/* 弹窗 */}
  <ProviderFormModal
    open={providerFormOpen}
    provider={editingProvider}
    onSubmit={handleProviderSubmit}
    onCancel={() => setProviderFormOpen(false)}
  />
  <ModelFormModal
    open={modelFormOpen}
    providerId={currentProviderId}
    model={editingModel}
    onSubmit={handleModelSubmit}
    onCancel={() => setModelFormOpen(false)}
  />
</div>
```

#### 规则

- 不使用 Zustand Store，所有状态使用 `useState`。
- API 调用使用 `api/modelApi.ts` 中的函数，不在组件中直接构造 URL。
- 使用 `Popconfirm` 做删除确认，不用 `window.confirm`。
- 使用 `message.success` / `message.error` 展示操作结果。
- 表单使用 Ant Design `Form` 组件，校验使用其内置规则。
- 组件使用函数式组件 + hooks。
- import 使用相对路径，不用 `@/`。
- 不创建 `features/` 目录下的文件。
- 已有的 `PageHeader`、`Empty`、`Loading` 组件从 `shared/ui` 导入。

#### 验收标准

- 页面加载时显示供应商列表。
- 点击"添加供应商"弹出表单，填写后创建成功刷新列表。
- 点击"编辑"弹出表单预填已有数据，修改后更新成功。
- 点击"测试连接"显示 loading，完成后展示成功或失败消息。
- 点击"启用/禁用"切换状态，列表刷新。
- 点击"删除"弹确认框，确认后删除并刷新。
- 展开供应商卡片显示模型列表。
- 模型的增删改查、启用/禁用、测试全部可用。
- `npm run build` 通过。

---

## 五、禁止实现的内容

本次不要做：

1. 不要实现 Agent CRUD 和对话。
2. 不要实现 Workflow 引擎。
3. 不要实现 Knowledge 文档上传和 Embedding。
4. 不要实现 Tool 配置。
5. 不要引入 Spring AI 的 ChatModel / EmbeddingModel（健康测试用 RestClient 直接调 API）。
6. 不要创建 LLM 调用的完整执行链路（那是 engine 模块的职责）。
7. 不要做定时健康巡检。
8. 不要做负载均衡和多 Provider 自动切换。
9. 不要修改 `zify-common` 中已有的类（可以新增）。
10. 不要修改 `zify-web/src/api/request.ts`。
11. 不要修改 `zify-web/src/shared/` 下的已有文件。
12. 不要修改 `zify-web/src/app/router.tsx`（`/models` 路由已存在）。
13. 不要安装新的前端依赖。

---

## 六、实施顺序

严格按以下顺序执行，每个任务完成后执行构建验证，确保无编译错误再进入下一个任务：

1. 阅读所有必须文档和已有代码。
2. 任务 1：数据库迁移脚本 → `mvn package -DskipTests`
3. 任务 2：Entity + Mapper + ErrorCode + Converter → `mvn package -DskipTests`
4. 任务 3：DTO 类 → `mvn package -DskipTests`
5. 任务 4：ModelProviderService → `mvn package -DskipTests`
6. 任务 5：ModelService + 健康测试 → `mvn package -DskipTests`
7. 任务 6：ModelFacade + FacadeImpl → `mvn package -DskipTests`
8. 任务 7：Controllers → `mvn package -DskipTests`
9. 任务 8：前端类型 + API 层 → `cd zify-web && npm run build`
10. 任务 9：前端模型管理页面 → `cd zify-web && npm run build`

---

## 七、验证命令

后端每个任务完成后：

```bash
mvn package -DskipTests
```

全部后端完成后，如能启动应用，验证接口：

```bash
# 启动应用（需要 MySQL 和 Redis 运行）
java -jar zify-app/target/zify-app-0.1.0-SNAPSHOT.jar

# 测试创建供应商
curl -X POST http://localhost:8080/api/model/providers \
  -H "Content-Type: application/json" \
  -d '{"name":"Test OpenAI","providerType":"OPENAI","apiKey":"sk-test","baseUrl":"https://api.openai.com"}'

# 测试列表
curl http://localhost:8080/api/model/providers
```

前端每个任务完成后：

```bash
cd zify-web
npm run build
```

全部前端完成后：

```bash
cd zify-web
npm run build
npm run lint  # 如果有 lint 配置
```

---

## 八、输出要求

每个任务完成后输出：

1. 新增了哪些文件（逐一列出完整路径）。
2. 修改了哪些已有文件（逐一列出，说明改了什么）。
3. 该任务实现了什么功能（一句话）。
4. 构建结果（通过 / 失败 + 关键错误信息）。
5. 是否有未完成事项或需要后续补充的内容。

如果构建失败，必须贴出完整错误信息并说明原因和修复方案，不要隐瞒。

全部任务完成后，额外输出：

1. 所有 API 接口清单（路径 + 方法 + 简述）。
2. 与其他模块的集成点（ModelFacade 的使用方式）。
3. 后续开发 Agent / 知识库 / 工作流模块时需要注意的事项。
