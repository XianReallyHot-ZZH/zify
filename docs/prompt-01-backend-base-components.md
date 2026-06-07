# Prompt 01：后端公共基础组件实施

你是 Zify 项目的 AI 编码助手。现在要在正式开发业务功能前，补齐 Zify 的公共基础组件。

## 一、项目背景

Zify 是模块化单体 AI 应用，后端采用 Maven 多模块工程：

```text
zify/
├── zify-common/
├── zify-model/
├── zify-tool/
├── zify-knowledge/
├── zify-workflow/
├── zify-agent/
├── zify-engine/
├── zify-chat/
├── zify-trigger/
├── zify-app/
└── zify-web/
```

技术栈：

- Java 21
- Spring Boot 4.0
- Spring AI 2.0
- MyBatis-Plus
- MySQL 8.x
- PostgreSQL + pgvector
- Redis 7
- React + Vite 前端

项目已有工程骨架：

- Maven 多模块已搭好
- `zify-common` 已有部分基础能力：
  - `Result`
  - 异常处理
  - MyBatis-Plus 配置
  - Redis 配置
- `zify-web` React 工程已搭好

你必须先阅读：

```text
CLAUDE.md
glm-docs/06-zify-code-organization.md
glm-docs/10-zify-database-spec.md
glm-docs/11-zify-core-data-model.md
glm-docs/07-zify-LLM-api-calling.md
```

如涉及部署或性能，也阅读：

```text
glm-docs/08-zify-deployment-architecture.md
glm-docs/09-zify-performance-bottleneck.md
```

## 二、硬性约束

必须遵守以下规则：

1. 跨模块调用只能走目标模块 `api` Facade。
2. Controller 只能调用本模块 Service。
3. Entity / Mapper / Repository / Service 禁止跨模块引用。
4. HTTP Request / Response 不能进入 domain 层。
5. `zify-common` 只能放基础设施和通用能力，不能出现 Agent / Workflow / Knowledge / Tool / Model / Chat / Trigger 等业务概念。
6. 禁止为了绕过模块依赖把业务类塞进 `zify-common`。
7. 所有数据库结构变更必须使用 Flyway 迁移脚本。
8. MySQL 业务表必须包含：
   - `id`
   - `created_at`
   - `updated_at`
   - `is_deleted`
9. MySQL 主键使用 `CHAR(36)` UUID。
10. 软删除唯一约束使用 generated column，禁止 `UNIQUE(field, is_deleted)`。
11. 禁止在事务内调用 LLM、Embedding、MCP、HTTP 工具或其他慢外部 API。
12. 日志禁止输出 API Key、密码、Token。
13. 外部调用必须有超时。
14. 不引入项目技术栈以外的新依赖，除非任务明确要求。

## 三、本次任务目标

补齐 Zify 业务开发前必须具备的公共基础组件，范围包括：

1. 数据库层基础组件
2. 接口层基础组件
3. 外部调用基础组件
4. 缓存基础组件
5. 可观测性基础组件
6. 配置与安全基础组件

本次只实现“公共基础设施”，不实现具体业务功能。

不要创建 Agent、Workflow、Knowledge、Tool、Chat 等业务 CRUD。

---

## 四、实施任务清单

### 任务 1：接入 Flyway 数据库迁移

#### 目标

让所有数据库结构变更通过 Flyway 管理。

#### 需要做

1. 在父 POM 或 `zify-app/pom.xml` 中添加 Flyway 依赖：
   - `flyway-core`
   - `flyway-mysql`
2. 在 `zify-app/src/main/resources/application.yml` 中添加 Flyway 配置。
3. 创建迁移目录：

```text
zify-app/src/main/resources/db/migration/
```

4. 创建一个初始化占位迁移脚本：

```text
zify-app/src/main/resources/db/migration/V1__common__init.sql
```

该脚本暂时只做安全初始化，例如：

```sql
-- Zify initial migration placeholder
SELECT 1;
```

如果 MySQL 不允许该写法，则使用 Flyway 可接受的最小空迁移写法。

#### 验收标准

- 项目启动时 Flyway 能正常扫描迁移目录。
- `mvn package -DskipTests` 能通过。
- 不创建具体业务表。

---

### 任务 2：补齐 MyBatis-Plus 通用实体和自动填充

#### 目标

统一所有 MySQL 业务表的通用字段。

#### 需要做

在 `zify-common` 中新增：

```text
zify-common/src/main/java/com/zify/common/persistence/entity/BaseEntity.java
zify-common/src/main/java/com/zify/common/persistence/id/IdGenerator.java
zify-common/src/main/java/com/zify/common/persistence/handler/MybatisMetaObjectHandler.java
```

#### BaseEntity 要求

包含字段：

```java
private String id;
private LocalDateTime createdAt;
private LocalDateTime updatedAt;
private Integer isDeleted;
```

要求：

- 使用 MyBatis-Plus 注解映射字段。
- `id` 对应数据库 `CHAR(36)` UUID。
- `createdAt` 对应 `created_at`。
- `updatedAt` 对应 `updated_at`。
- `isDeleted` 对应 `is_deleted`。
- 不要包含任何业务字段。

#### IdGenerator 要求

提供方法：

```java
public static String uuid()
```

返回标准 UUID 字符串。

#### MybatisMetaObjectHandler 要求

插入时自动填充：

- `id`
- `createdAt`
- `updatedAt`
- `isDeleted = 0`

更新时自动填充：

- `updatedAt`

#### 验收标准

- `BaseEntity` 不包含业务概念。
- 自动填充逻辑不影响已有代码编译。
- `mvn package -DskipTests` 通过。

---

### 任务 3：强化 MyBatis-Plus 配置

#### 目标

统一逻辑删除、分页、防止全表更新/删除。

#### 需要做

检查并更新 `zify-common` 中已有 MyBatis-Plus 配置。

必须包含：

1. 分页插件。
2. BlockAttackInnerInterceptor，防止无 WHERE 的 update/delete。
3. 逻辑删除配置。

如果已有配置，则在现有配置基础上补齐，不重复创建配置类。

#### application.yml 配置要求

补充或确认：

```yaml
mybatis-plus:
  global-config:
    db-config:
      logic-delete-field: isDeleted
      logic-delete-value: 1
      logic-not-delete-value: 0
```

#### 验收标准

- 不破坏现有 MyBatis-Plus 配置。
- `mvn package -DskipTests` 通过。

---

### 任务 4：补齐分页基础对象

#### 目标

统一普通分页和游标分页返回结构。

#### 需要做

在 `zify-common` 中新增或补齐：

```text
zify-common/src/main/java/com/zify/common/web/PageRequest.java
zify-common/src/main/java/com/zify/common/web/PageResult.java
zify-common/src/main/java/com/zify/common/web/CursorPageRequest.java
zify-common/src/main/java/com/zify/common/web/CursorPageResult.java
```

如果已有 `PageResult`，不要重复创建，直接扩展现有类或保持兼容。

#### PageRequest 要求

字段：

```java
private Integer page;
private Integer pageSize;
```

规则：

- `page` 默认 1。
- `pageSize` 默认 20。
- `pageSize` 最大 100。

#### PageResult 要求

字段：

```java
private List<T> records;
private Long total;
private Integer page;
private Integer pageSize;
```

#### CursorPageRequest 要求

字段：

```java
private LocalDateTime cursorCreatedAt;
private String cursorId;
private Integer limit;
```

规则：

- `limit` 默认 20。
- `limit` 最大 100。
- `cursorCreatedAt` 和 `cursorId` 要么都为空，要么都不为空。

#### CursorPageResult 要求

字段：

```java
private List<T> records;
private String nextCursorId;
private LocalDateTime nextCursorCreatedAt;
private Boolean hasMore;
```

#### 验收标准

- 类型放在 `zify-common`，无业务概念。
- 支持泛型。
- `mvn package -DskipTests` 通过。

---

### 任务 5：补齐全局异常处理

#### 目标

统一接口错误响应。

#### 需要做

检查 `zify-common` 现有：

```text
BusinessException
ErrorCode
GlobalExceptionHandler
Result
```

如果已有，则补齐以下异常处理：

1. `MethodArgumentNotValidException`
2. `BindException`
3. `ConstraintViolationException`
4. `HttpMessageNotReadableException`
5. `NoHandlerFoundException`
6. `HttpRequestMethodNotSupportedException`
7. `BusinessException`
8. 未知 `Exception`

#### Result 格式

保持项目已有格式，不随意改字段名。

如果已有格式是：

```json
{
  "code": 200,
  "message": "success",
  "data": {}
}
```

则继续沿用。

#### ErrorCode 要求

至少包含：

```text
SUCCESS
PARAM_ERROR
NOT_FOUND
METHOD_NOT_ALLOWED
JSON_PARSE_ERROR
INTERNAL_ERROR
EXTERNAL_CALL_FAILED
UNAUTHORIZED
FORBIDDEN
```

如果已有错误码体系，保持兼容，只补缺失项。

#### 验收标准

- 所有异常返回统一 `Result`。
- 不在 Controller 中手写 try-catch。
- `mvn package -DskipTests` 通过。

---

### 任务 6：请求 TraceId 与日志 MDC

#### 目标

每个 HTTP 请求都有 traceId，方便排查跨模块调用链路。

#### 需要做

在 `zify-common` 中新增：

```text
zify-common/src/main/java/com/zify/common/web/filter/TraceIdFilter.java
zify-common/src/main/java/com/zify/common/web/TraceConstants.java
```

#### 行为要求

1. 请求进入时：
   - 如果 Header 中有 `X-Trace-Id`，使用该值。
   - 如果没有，生成 UUID。
2. 写入 MDC：

```text
traceId
```

3. 响应 Header 返回：

```text
X-Trace-Id
```

4. 请求结束后必须清理 MDC。

#### 日志格式

检查 `application.yml`，如没有日志 pattern，则增加包含 traceId 的日志格式。

示例：

```yaml
logging:
  pattern:
    level: "%5p [traceId:%X{traceId}]"
```

#### 验收标准

- 每个请求响应都有 `X-Trace-Id`。
- 日志中能看到 traceId。
- MDC 在 finally 中清理。
- `mvn package -DskipTests` 通过。

---

### 任务 7：统一配置属性类

#### 目标

避免硬编码上传目录、加密 key、LLM 默认参数等配置。

#### 需要做

在 `zify-common` 中新增配置属性：

```text
zify-common/src/main/java/com/zify/common/config/properties/ZifyProperties.java
zify-common/src/main/java/com/zify/common/config/properties/UploadProperties.java
zify-common/src/main/java/com/zify/common/config/properties/SecurityProperties.java
zify-common/src/main/java/com/zify/common/config/properties/LlmProperties.java
```

也可以使用一个 `ZifyProperties` 聚合内部类，二选一即可，但要结构清晰。

#### application.yml 增加配置

```yaml
zify:
  upload:
    dir: ${UPLOAD_DIR:/data/uploads}
    max-size: 100MB
  security:
    encryption-key: ${ZIFY_ENCRYPTION_KEY:}
  llm:
    provider-defaults:
      max-concurrent: 20
      acquire-timeout: 2s
```

#### 验收标准

- 配置类使用 `@ConfigurationProperties`。
- 启动时能绑定配置。
- 不硬编码上传路径、加密 key、LLM 并发值。
- `mvn package -DskipTests` 通过。

---

### 任务 8：敏感信息脱敏与加解密组件

#### 目标

Provider API Key 等敏感信息必须加密存储、脱敏展示。

#### 需要做

在 `zify-common` 中新增：

```text
zify-common/src/main/java/com/zify/common/security/SecretEncryptor.java
zify-common/src/main/java/com/zify/common/security/MaskUtils.java
```

#### SecretEncryptor 要求

提供：

```java
public String encrypt(String plainText)
public String decrypt(String cipherText)
```

要求：

- 使用 JDK 自带加密能力，不引入额外依赖。
- 使用 AES/GCM 或 AES/CBC + HMAC。
- 加密 key 从 `zify.security.encryption-key` 读取。
- 如果 encryption-key 为空，启动时不报错，但调用 encrypt/decrypt 时抛出 `BusinessException` 或明确异常。
- 禁止日志输出明文。

#### MaskUtils 要求

提供：

```java
public static String maskSecret(String value)
public static String maskApiKey(String value)
```

示例：

```text
sk-1234567890abcdef -> sk-1***cdef
```

#### 验收标准

- 不打印明文 key。
- 加解密可通过简单单元测试或最小验证。
- `mvn package -DskipTests` 通过。

---

### 任务 9：统一外部 HTTP Client 基础配置

#### 目标

所有外部调用必须有超时，避免无限阻塞。

#### 需要做

在 `zify-common` 中新增基础 RestClient 配置：

```text
zify-common/src/main/java/com/zify/common/http/RestClientConfig.java
zify-common/src/main/java/com/zify/common/http/ExternalCallProperties.java
```

#### 要求

提供一个通用 `RestClient.Builder` 或命名 Bean。

默认配置：

```text
connect-timeout: 10s
read-timeout: 60s
```

注意：

- 这里只提供通用基础配置。
- LLM Provider 的具体调用仍必须在 `zify-model` 内实现。
- MCP 的具体调用仍在对应模块实现。
- HTTP Tool 的具体调用仍在 `zify-tool` 或 `zify-workflow` 按职责实现。

#### 验收标准

- 不在 common 中出现业务调用逻辑。
- 不包含 OpenAI / Anthropic / MCP 等具体业务 client。
- `mvn package -DskipTests` 通过。

---

### 任务 10：外部调用异常模型

#### 目标

统一外部调用失败语义，方便上层判断是否可重试、是否可展示。

#### 需要做

在 `zify-common` 中新增通用异常：

```text
zify-common/src/main/java/com/zify/common/exception/ExternalCallException.java
zify-common/src/main/java/com/zify/common/exception/ExternalCallTimeoutException.java
zify-common/src/main/java/com/zify/common/exception/ExternalCallRetryableException.java
zify-common/src/main/java/com/zify/common/exception/ExternalCallNonRetryableException.java
zify-common/src/main/java/com/zify/common/exception/ExternalCallCancelledException.java
```

#### 要求

`ExternalCallException` 至少包含：

```java
private final String provider;
private final String scenario;
private final boolean retryable;
```

注意：

- 不要包含 API Key。
- 不要依赖任何业务模块。
- LLM 具体异常后续可以在 `zify-model` 中继承或包装这些通用异常。

#### 验收标准

- 异常无业务模块依赖。
- `mvn package -DskipTests` 通过。

---

### 任务 11：Redis Key 规范组件

#### 目标

统一 Redis key 命名和 TTL 管理。

#### 需要做

在 `zify-common` 中新增：

```text
zify-common/src/main/java/com/zify/common/redis/RedisKeys.java
```

#### 要求

提供以下 key 生成方法：

```java
public static String chatContext(String conversationId)
public static String documentParseProgress(String documentId)
public static String workflowRunState(String runId)
public static String sseState(String runId)
public static String rateLimit(String userId, String api)
```

返回格式：

```text
chat:ctx:{conversationId}
doc:parse:{documentId}
workflow:run:{runId}
sse:{runId}
rate:{userId}:{api}
```

#### 规则

- 只生成 key，不直接访问 Redis。
- 不存 Java 对象，如 SseEmitter。
- 所有使用 Redis 的业务代码后续必须通过该类生成 key。

#### 验收标准

- `RedisKeys` 无业务依赖。
- `mvn package -DskipTests` 通过。

---

### 任务 12：异步 Executor 配置

#### 目标

统一异步任务线程池，禁止业务代码手动 new Thread。

#### 需要做

在 `zify-common` 中新增或补齐：

```text
zify-common/src/main/java/com/zify/common/config/AsyncExecutorConfig.java
```

#### 要求

提供以下 Bean：

```java
asyncTaskExecutor
documentParseExecutor
workflowExecutor
```

如果项目已经决定 LLM 使用虚拟线程，可在 `zify-model` 后续单独实现 `llmTaskExecutor`，本任务不强行实现 LLM executor。

线程池要求：

- 使用 `ThreadPoolExecutor` 显式构造。
- 禁止 `Executors.newXxx()`。
- 指定：
  - corePoolSize
  - maximumPoolSize
  - queueCapacity
  - threadNamePrefix
  - RejectedExecutionHandler

线程名前缀：

```text
zify-async-
zify-doc-parse-
zify-workflow-
```

#### 验收标准

- 没有使用 `Executors.newXxx()`。
- Bean 由 Spring 管理。
- `mvn package -DskipTests` 通过。

---

### 任务 13：慢调用日志工具

#### 目标

统一记录慢 SQL、慢外部调用、慢任务，先打日志，后续可接指标系统。

#### 需要做

在 `zify-common` 中新增：

```text
zify-common/src/main/java/com/zify/common/observability/SlowLogUtils.java
```

#### 要求

提供方法：

```java
public static void logIfSlow(String event, long durationMs, long thresholdMs, Map<String, Object> context)
```

行为：

- `durationMs >= thresholdMs` 时输出 WARN 日志。
- 使用 SLF4J。
- 日志中包含：
  - event
  - durationMs
  - thresholdMs
  - context
  - traceId（从 MDC 里自动带出即可）
- 禁止打印敏感信息。

#### 验收标准

- 工具类无业务依赖。
- `mvn package -DskipTests` 通过。

---

## 五、禁止实现的内容

本次不要做：

1. 不要实现 Agent CRUD。
2. 不要实现 Model Provider CRUD。
3. 不要实现 Chat 对话。
4. 不要实现 Workflow 引擎。
5. 不要实现 Knowledge 文档上传。
6. 不要创建 21 张业务表。
7. 不要引入 Kafka、RabbitMQ、Elasticsearch、Langfuse、Prometheus。
8. 不要引入 MapStruct、Lombok 以外的新工具依赖，除非项目已经使用。
9. 不要修改模块依赖图。
10. 不要把业务枚举或业务 DTO 放入 `zify-common`。

---

## 六、实施顺序

严格按以下顺序执行：

1. 先阅读项目现有代码和配置。
2. 检查 `zify-common` 已有哪些类，避免重复创建。
3. 接入 Flyway。
4. 实现 BaseEntity / IdGenerator / MetaObjectHandler。
5. 强化 MyBatis-Plus 配置。
6. 补齐分页对象。
7. 补齐全局异常处理。
8. 实现 TraceIdFilter。
9. 实现配置属性类。
10. 实现 SecretEncryptor 和 MaskUtils。
11. 实现通用 RestClient 配置。
12. 实现外部调用异常模型。
13. 实现 RedisKeys。
14. 实现 AsyncExecutorConfig。
15. 实现 SlowLogUtils。
16. 运行构建验证。
17. 汇总修改内容和后续建议。

---

## 七、验证命令

完成后必须运行：

```bash
mvn package -DskipTests
```

如果前端没有改动，不需要运行前端构建。

如修改了 `application.yml`，需要确认后端能启动：

```bash
java -jar zify-app/target/zify-app-0.1.0-SNAPSHOT.jar
```

然后访问：

```bash
curl http://localhost:8080/api/health
```

---

## 八、输出要求

完成后输出：

1. 修改了哪些文件。
2. 每个基础组件解决什么问题。
3. 是否新增依赖。
4. 是否修改 `application.yml`。
5. 构建结果。
6. 是否有未完成事项。
7. 后续建议做哪些业务模块。

如果构建失败，不要隐瞒，必须贴出关键错误信息并说明原因。
