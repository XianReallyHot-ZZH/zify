# CLAUDE.md

AI 编码助手在 Zify 项目中的工作规范。内容合并自 `glm-docs/02` 到 `glm-docs/10` 的前期决策。

使用方式：

1. 任何改动前，先读 **§1 项目总契约** 和 **§2 任务路由**。
2. 根据任务所属模块，细读对应章节。不相关章节可跳过。
3. 实现完成后，用 **§9 检查清单** 逐项验证。
4. 如果需求与本文冲突，先更新 `glm-docs/*.md` 决策文档，再写代码。

---

## §1 项目总契约

Zify 是模块化单体 AI 应用。一人开发，一期面向 ~50 人内部使用，同时为未来扩展到几千人保留清晰接缝。

技术栈：

- 后端：Spring Boot 应用、**Maven 多模块工程**（一个父 POM + 多个子模块 + 一个启动模块 `zify-app`）、Java 21、Spring Boot 4.0、Spring AI 2.0、MyBatis-Plus。
- 前端：`zify-web` 子模块（React 18 + TypeScript + Vite + React Router + React Flow + Zustand + Axios + Ant Design）。构建产物由 `zify-nginx` 直接服务。
- 数据：MySQL 8.x（业务数据）、PostgreSQL + pgvector（chunk 和 embedding）、Redis 7（短生命周期缓存、进度和限流）。
- 部署：Docker + K8s，单副本。详见 §8。

架构约束：

- Maven 多模块单体，模块边界由编译时依赖强制执行。一期不引入微服务、消息队列、HPA、数据库高可用或独立 Worker，除非用户明确要求。
- 跨模块调用必须走目标模块 `api` Facade。禁止注入其他模块的 Service、Mapper、Repository、Entity 或 Controller。Maven `<dependency>` 声明必须与依赖图完全匹配。详见 §3。
- Spring AI 是 AI 框架（LLM 调用、VectorStore、工具、OpenAI 兼容 Provider、MCP 集成）。MCP 一期只做 Client。
- 文档解析使用 Apache POI、PDFBox，并支持 TXT/Markdown。Cron 触发器使用 Quartz。
- IO 密集异步任务使用 Java 21 Virtual Threads 和 Spring 管理的 Executor。

一期做：Agent 管理、模型管理、统一工具系统（MCP Client / HTTP 工具 / Workflow-as-Tool）、知识库 RAG（pgvector）、简版工作流引擎、Webhook/Cron 触发器、对话和会话管理。

一期不做：多租户/计费/应用市场/插件/SDK/SSO；工作流循环/人工审批/DSL 版本管理/复杂 RAG Pipeline/混合检索/Rerank；默认 Provider 负载均衡或自动 fallback。

---

## §2 任务路由

新增功能时，先判断所属模块。

后端模块：

```text
agent      Agent CRUD、Prompt、模型/工具/知识库/工作流绑定
chat       会话和消息持久化
engine     Agent 执行、ReAct 循环、流式响应编排
workflow   工作流编辑数据、节点执行、变量传递、运行日志
knowledge  文档、解析、分块、Embedding、pgvector 检索
tool       MCP / HTTP / Workflow-as-Tool 定义和工具调用入口描述
trigger    Webhook 接收、Cron 调度、触发日志
model      Provider 配置、API Key 使用、LLM/Embedding/Rerank 调用
common     仅放基础设施：配置、异常、统一响应、工具类
app        启动模块：聚合所有后端子模块，@SpringBootApplication 主类、配置文件、Flyway 迁移脚本
```

前端模块：`zify-web`（React 18 + TypeScript + Vite）。不是 Spring Boot 子模块，由 Vite 独立构建。

允许的模块依赖（每个子模块还隐式依赖 `zify-common`，通过父 POM `<dependencyManagement>` 统一管理版本）：

```text
common    -> 无
model     -> common
tool      -> common
knowledge -> common, model
workflow  -> common, model, knowledge, tool
agent     -> common, model, tool, knowledge, workflow
engine    -> common, agent, model, tool, knowledge, workflow
chat      -> common, agent, engine
trigger   -> common, workflow
app       -> 所有后端子模块（启动模块，聚合打包）
```

功能实现顺序：

1. 判断功能所属模块。如果需要其他模块能力，检查依赖关系是否允许。
2. **验证当前子模块 `pom.xml` 是否已声明对目标模块的 Maven 依赖**；未声明则先添加。
3. 在当前模块 `api/dto` 中新增或复用 Facade DTO。
4. 新增 Entity、Mapper、Converter。
5. 新增 Service 和事务边界。
6. 新增 FacadeImpl。
7. 新增 Controller、HTTP Request、HTTP Response。
8. 确认没有引入禁止的跨模块引用。
9. 确认子模块 `pom.xml` 的 `<dependency>` 声明与依赖图完全匹配。
10. 如果是用户可见功能，再新增前端 API、类型、页面和组件（在 `zify-web` 中）。

---

## §3 后端代码组织

### 目录结构

项目顶层布局（父 POM + 所有子模块）：

```text
zify/                                    父 POM (packaging=pom)
├── pom.xml                              <modules> + <dependencyManagement> + <properties>
├── zify-common/                         基础设施
├── zify-model/                          Provider、LLM/Embedding 调用
├── zify-tool/                           MCP / HTTP / Workflow-as-Tool
├── zify-knowledge/                      文档、解析、分块、Embedding、pgvector
├── zify-workflow/                       工作流编辑、节点执行、变量传递
├── zify-agent/                          Agent CRUD、Prompt、绑定
├── zify-engine/                         Agent 执行、ReAct、流式响应
├── zify-chat/                           会话和消息持久化
├── zify-trigger/                        Webhook、Cron、触发日志
├── zify-app/                            Spring Boot 启动模块
└── zify-web/                            前端（React + Vite，独立构建）
```

父 POM 职责：

- `<packaging>pom</packaging>`，不产出 JAR。
- `<modules>` 声明所有子模块，Maven 反应堆自动计算构建顺序。
- `<dependencyManagement>` 统一管理 Spring Boot、Spring AI、MyBatis-Plus 等第三方版本和 `zify-common` 等项目内模块版本。
- 子模块 POM 省略版本号，从父 POM 继承。

每个后端业务子模块内部结构（以 `zify-agent` 为例）：

```text
zify-agent/
├── pom.xml
└── src/
    ├── main/java/com/zify/agent/
    │   ├── api/
    │   │   ├── {Module}Facade.java
    │   │   └── dto/
    │   ├── domain/
    │   │   ├── {Module}Service.java
    │   │   ├── executor/
    │   │   ├── handler/
    │   │   └── validator/
    │   ├── infrastructure/
    │   │   ├── entity/
    │   │   ├── mapper/
    │   │   ├── repository/        # 可选，仅复杂查询或多 Mapper 编排时创建
    │   │   ├── converter/
    │   │   ├── facade/
    │   │   └── client/
    │   └── adapter/
    │       ├── web/
    │       │   ├── request/
    │       │   └── response/
    │       └── sse/               # 只有需要 SSE/流式接口时创建
    └── main/resources/
        └── mapper/                # MyBatis XML Mapper（如有）
```

`zify-app` 启动模块结构：

```text
zify-app/
├── pom.xml                            依赖所有后端子模块
└── src/
    ├── main/java/com/zify/
    │   └── ZifyApplication.java       @SpringBootApplication 主类
    └── main/resources/
        ├── application.yml            主配置文件
        ├── application-dev.yml        本地开发 profile
        ├── application-prod.yml       生产 profile
        └── db/migration/              Flyway 迁移脚本（按模块前缀命名）
            ├── V1__agent__create_agent_table.sql
            ├── V2__chat__create_message_table.sql
            └── ...
```

`zify-web` 前端模块结构（见 §4）：

```text
zify-web/
├── package.json
├── vite.config.ts
├── tsconfig.json
└── src/
    ├── app/        App.tsx、router.tsx、providers.tsx、layouts/
    ├── pages/      路由页面和页面私有组件/Hook
    ├── features/   可复用业务组件、复杂业务 UI、业务 Hook
    ├── api/        HTTP API 封装
    ├── stores/     Zustand 全局客户端状态
    ├── types/      HTTP 契约类型和前端视图类型
    ├── shared/     无业务含义的 UI、Hook、工具函数
    ├── styles/
    └── main.tsx
```

### 分层与调用规则

- `api`：只放 Facade 接口和跨模块 DTO / Command / Query / Result。这是子模块的公共 API，其他子模块通过 Maven 依赖消费。
- `domain`：业务逻辑、事务边界、本模块持久化编排，以及允许的跨模块 Facade 调用。
- `infrastructure`：Entity、Mapper、可选 Repository、Converter、FacadeImpl、外部 Client。
- `adapter`：HTTP / SSE Controller、请求校验、响应转换。

硬性规则（不可违反）：

- `Controller -> Service`，Controller 只能调用本模块 Service。
- 跨模块只允许 `Service -> 目标模块 Facade`。
- **`<dependency>` 声明必须与 §2 依赖图完全匹配**，编译时强制边界。
- Entity 不能跨模块。Facade 方法不能返回 Entity 或 MyBatis-Plus 分页对象。
- HTTP Request / Response 不能进入 domain 层。
- `common` 不能出现业务概念（Agent / Workflow / Knowledge / Tool / Model / Chat / Trigger）。
- 不允许为绕过循环依赖把业务类塞进 `common`；必须重新划分模块边界。

事务规则：

- 写操作事务放在 public Service 方法上。事务必须短。
- **禁止在数据库事务内调用 LLM、Embedding、MCP、HTTP 工具或其他慢外部 API。**

### 命名规范

- 类名 UpperCamelCase 名词，方法 / 变量 lowerCamelCase。禁止拼音、随意缩写。
- 常量全大写下划线 `MAX_RETRY_COUNT`，放 `private static final` 或 `common/constants`。禁止魔法数字。
- 布尔字段不加 `is` 前缀：用 `deleted`、`enabled`，不用 `isDeleted`。（MyBatis-Plus 和 JSON 序列化有歧义）
- 抽象类前缀 `Abstract` / `Base`，异常类后缀 `Exception`，测试类后缀 `Test`。
- 包名全小写单数，不使用下划线。
- 本项目后缀约定：

```text
接口       {Module}Service / {Module}Facade
实现       {Module}ServiceImpl / {Module}FacadeImpl
持久化     {Module}Entity / {Module}Mapper / {Module}Converter
请求响应   XxxRequest / XxxResponse / XxxQuery
```

### 异常处理

- 捕获你能处理的最具体异常类型，不要捕获 `Exception` / `Throwable`。
- catch 块不能为空——至少记录 WARN 日志。
- 项目统一使用 `BusinessException`（`zify-common` 子模块的 `exception` 包）+ 枚举 `ErrorCode`，不传硬编码字符串。Service 抛出，`@RestControllerAdvice` 统一捕获。
- finally 中禁止 return / continue / break。
- try-catch 放在循环外层。如果只有单次迭代需要捕获，先判断能否用前置条件替代。

### 日志规范

- 使用 SLF4J + Logback（`@Slf4j`）。禁止 `System.out`、`e.printStackTrace()`。
- 使用 `{}` 占位符，禁止字符串拼接。DEBUG 日志用 `isDebugEnabled()` 或 Lambda 形式。
- 异常日志必须同时输出 message 和堆栈：`log.error("msg {}", ctx, e)`。禁止 `log.error(e.getMessage())`。
- 禁止输出 API Key、密码、Token。Provider 日志最多显示 key 前 4 位 + `***`。

日志级别：

```text
ERROR  影响功能的异常，需人工介入。必须附异常对象。
WARN   可容忍的异常，系统可自动恢复。
INFO   关键业务节点：请求入口、外部调用、状态变更、定时任务触发/完成。
DEBUG  调试细节，生产默认不输出。循环内日志默认用 DEBUG。
```

### 并发规范

- 线程池必须用 `ThreadPoolExecutor` 构造函数创建，禁止 `Executors.newXxx()`。显式指定核心线程数、最大线程数、队列容量、线程名前缀（`zify-{module}-{purpose}-`）和拒绝策略。项目统一使用 Spring 管理的 `Executor` Bean。
- 禁止 `SimpleDateFormat` 作为静态或实例变量共享。优先使用 `java.time`（`LocalDateTime` / `Instant` / `ZonedDateTime`）和 `DateTimeFormatter`。
- `ThreadLocal` 使用后必须 `remove()`，在 `finally` 块中调用。
- 共享可变状态优先使用 `java.util.concurrent`（`AtomicLong` / `ConcurrentHashMap` / `CompletableFuture`），不要手写 `wait/notify`。禁止在 domain 对象中使用 `synchronized`。

---

## §4 前端代码组织（`zify-web`）

前端代码在 `zify-web` 子模块中，使用 Vite + React Router。禁止 Next.js App Router。`zify-web` 不是 Spring Boot 模块，由 Vite 独立构建（`npm run build`），产物输出到 `zify-web/dist/`，由 `zify-nginx` 直接服务静态资源。

路由：

```text
/                  -> ChatPage
/agents            -> AgentListPage / AgentFormPage
/workflows         -> WorkflowListPage / WorkflowEditorPage
/knowledge         -> KnowledgeListPage / KnowledgeDetailPage
/tools             -> ToolListPage / ToolFormPage
/models            -> ModelPage
```

依赖规则：

- `app` -> `pages`、`shared`、`stores`，不写业务 API 调用。
- `pages` -> `features`、`api`、`types`、`stores`、`shared`。页面不能被其他页面引用。
- `features` -> `api`、`types`、`stores`、`shared`。不能引用 `pages`。
- `api` -> `types`、`shared/utils`、`api/request.ts`。不依赖 React、组件或 Store。
- `shared/ui` 不依赖业务 API、Store 或业务类型。
- `stores` 不发 HTTP 请求，不互相 import。

组件放置：

- 只被一个页面使用：`pages/{page}/components`。
- 可复用业务组件：`features/{domain}/components`。`ModelSelector` → `features/model`，`ToolSelector` → `features/tool`，`KnowledgeSelector` → `features/knowledge`。
- 无业务展示组件：`shared/ui`。

API 和类型：

- `api/*Api.ts` 调用后端 `/api/**`（HTTP Controller），不是内部 Facade。
- `types/*` 对齐后端 `adapter/web/request` 和 `adapter/web/response`，不对齐 Facade DTO。
- 类型命名 `XxxRequest` / `XxxResponse` / `XxxQuery` / `XxxView`。组件中禁止直接调用 Axios。

Zustand 和 SSE：

- Zustand 只存跨组件状态（当前会话、流式状态、画布状态、选中节点、侧边栏）。表单草稿、弹窗开关、列表过滤不进全局 Store。
- SSE 连接创建放 `api/engineApi.ts`（不走 Axios），UI 状态放 `features/chat/hooks/useChatStream.ts`。先 POST 提交消息，再 SSE 订阅。流事件名：`message_delta`、`tool_call`、`done`、`run_error`。中断按钮必须关闭 `EventSource` 并调用取消接口。

---

## §5 数据库规则

数据库分工：

- MySQL 8.x：业务数据、配置、对话、工作流、工具、触发器、Provider、日志、文件元数据。
- PostgreSQL + pgvector：知识库 chunk 和 embedding。编码 `UTF8`。
- Redis：缓存、进度、限流、临时状态。**Redis 不是最终数据源**，丢失后业务必须可恢复。禁止存 `SseEmitter` 等 Java 对象。

迁移：所有表结构变更必须使用 Flyway，禁止手工修改生产表。迁移脚本集中在 `zify-app/src/main/resources/db/migration/`，按模块前缀命名（如 `V1__agent__create_table.sql`）。建表迁移应同时包含必要索引。

### MySQL 规则

每张业务表包含：

```sql
`id`         CHAR(36)    NOT NULL,
`created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
`updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
`is_deleted` TINYINT     NOT NULL DEFAULT 0,
PRIMARY KEY (`id`)
```

- ID 由应用生成 UUID，主键 `CHAR(36)` 非 `VARCHAR(36)`。时间按 UTC 存储，精度毫秒。
- 用户资源表还要包含可空 `created_by`、`updated_by`。
- 可选字段用 `NULL`，禁止空字符串或 `0` 表示未知。
- 禁止给 `is_deleted` 单列建索引（可放入联合索引）。一期不建物理外键，由 Service 层校验。

软删除唯一约束：禁止 `UNIQUE(field, is_deleted)`。使用 generated column：`is_deleted = 0` 时返回唯一值，已删除行返回 `NULL`。

索引：

- 所有关联 ID 必须建索引。单表索引默认不超过 8 个。
- 大表父对象分页索引：`parent_id, is_deleted, created_at DESC, id DESC`。
- 状态列表索引：`status, is_deleted, created_at DESC, id DESC`。
- 禁止 `SELECT *`。禁止给 JSON / 大 TEXT 字段建普通索引，需要时用 generated column。

大表（message、tool_call_log、workflow_run、workflow_node_run、trigger_log、document_parse_log）：

- 使用 Keyset 分页，禁止 OFFSET。固定排序 `created_at DESC, id DESC`。游标必须同时包含 `cursor_created_at` 和 `cursor_id`。
- 默认每页 20 条，最大 100 条。列表只返回轻量字段。
- 历史删除分批执行，每批最多 1000 条，避开备份时间窗口。
- 保留时间：message 180 天、其余日志 30-90 天。

### pgvector 规则

- `vector` extension 通过迁移脚本初始化。`document_chunk` 使用 `JSONB`、`TIMESTAMPTZ`、`VECTOR(1536)`。
- Embedding 维度变更时必须迁移或新建表，禁止混写不同维度。
- 必须创建 HNSW 索引，以及 `knowledge_id` / `enabled` / `is_deleted` 过滤索引。
- 向量检索必须带 `knowledge_id` 过滤。默认 Top-K = 5，最大 20。API 禁止返回 `embedding` 字段。日常上传禁止 DROP / 重建 HNSW 索引。

---

## §6 LLM 调用规则

所有外部模型调用归口到 `model` 模块。

```text
zify-engine / zify-workflow / zify-knowledge -> zify-model 内 ModelFacade -> ModelService -> LlmGateway
```

硬性规则：

- 只有 `zify-model` 子模块内的 `infrastructure/client` 可以直接访问 LLM API。使用 Java 21 Virtual Threads + Spring `RestClient`。
- API Key 只在 `zify-model` 子模块内读取和使用，禁止返回给其他模块或前端，禁止记录到日志。
- Controller 不开线程。SSE 场景下 Controller 只创建 `SseEmitter`，交给 Service。
- SSE 断开 / 超时 / 发送失败时，必须取消上游 LLM 调用。Provider Client 必须定期检查中断状态。

超时和重试：

```text
连接: 10s  |  首 token: 30s  |  idle: 45s  |  Chat 总: 120s  |  工作流 LLM: 180s  |  Embedding: 60s
```

- 每个 Provider 最大并发许可 20，获取许可超时 2s。
- 使用显式 retry wrapper，禁止 `@Retryable`。
- 可重试：429、5xx、连接超时、首 token 超时。不重试：4xx、上下文超长、用户取消、已输出后的 idle 超时。
- 最大重试 2 次（总尝试 3 次），指数退避 + jitter + 尊重 `Retry-After`。流式调用只在尚未发送 chunk 给前端前才重试。

Fallback 和熔断：

- Provider fallback 默认关闭。只有用户显式配置时才 fallback，且只能在流式输出开始前。
- 进程内轻量熔断：连续 5 次可重试失败 → OPEN 60s → HALF_OPEN 单次探测。401/403 不计入。

日志：每次 LLM 调用记录结构化日志（traceId、scenario、provider、model、attempt、firstTokenMs、durationMs、token 数、status、errorType）。

---

## §7 产品规则

导航：左侧导航 + 内容区。一级：对话、Agents、工作流、知识库、工具、模型管理。触发器在工作流编辑页内配置。

核心路径：

- 首次配置：模型管理 → 工具 → 知识库 → 工作流（可选）→ Agents → 对话。
- 日常使用：对话页 → 选择 Agent → 开始或继续对话。

Agent：ReAct Agent 绑定模型、工具、知识库。Workflow Agent 绑定一个工作流，工具/知识库在工作流节点里配置。

工具：HTTP 工具在工具管理中注册，供 ReAct 选择。Workflow-as-Tool 在工作流发布时自动注册。工作流 HTTP Request 节点 ≠ HTTP 工具。

知识库：一期支持 PDF、DOCX、TXT、Markdown。检索只做 pgvector 向量检索。

工作流：编辑页是全屏画布（节点面板 + 画布 + 节点配置面板 + 运行日志面板）。一期 9 个节点：Start、End、LLM、If-Else、HTTP Request、Knowledge Retrieval、Code、Tool、Answer。触发器配置是工作流属性，不是画布节点。

---

## §8 部署与性能

生产拓扑：Ingress（TLS）→ zify-nginx（`zify-web/dist/` 静态资源 + `/api/**` 代理，SSE 路径关闭 buffering）→ zify-server（`zify-app` 启动模块打包）→ MySQL / PostgreSQL+pgvector / Redis / uploads PVC / 外部 LLM。

构建：后端项目根目录 `mvn package`，Docker 镜像只从 `zify-app/target/` 打包。前端在 `zify-web/` 目录下 `npm run build`，产物由 Nginx 服务。

核心约束：

- TLS 在 Ingress 终止。Ingress 只转发到 `zify-nginx`，不直接暴露 `zify-server`。
- 上传写入 `/data/uploads` PVC，禁止写 Pod 本地磁盘。MySQL 只存文件元数据。
- Provider API Key 存 MySQL 时必须加密。所有密钥使用 K8s Secret，禁止提交 Git。
- Docker Compose 只用于本地开发，生产用 K8s。
- 每日备份 MySQL / PostgreSQL / uploads，保留 14 天。备份不能只放在同一 PVC。

默认限制：

```text
文档解析并发: 2              上传最大大小: 100MB
单知识库文档数目标: 100       Embedding 批大小: 16-64 chunks
LLM Provider 最大并发: 20    Hikari 最大连接池: 20
```

扩到多副本前必须完成：Quartz 集群模式、上传迁移到对象存储、SSE / 异步任务状态不依赖单 Pod 内存、限流迁移到 Redis。

必须始终遵守：事务中不做外部调用、所有慢操作有并发限制、所有外部调用有超时、所有增长型列表分页、所有增长型表有索引和保留策略、所有核心数据有备份、所有关键瓶颈有日志或指标。

---

## §9 实现检查清单

后端：

- [ ] 模块归属正确，跨模块依赖在允许列表中。
- [ ] 子模块 `pom.xml` 的 `<dependency>` 声明与 §2 依赖图完全匹配。
- [ ] 新增业务子模块时，同步更新父 POM `<modules>` 和 `zify-app` 的依赖。
- [ ] 跨模块调用只走目标 Facade，Controller 只调用本模块 Service。
- [ ] 没有 Entity / Mapper / Repository / Service 跨模块。
- [ ] HTTP Request / Response 没有进入 domain 层。
- [ ] 事务没有覆盖外部调用。
- [ ] Converter 负责 Entity / DTO / HTTP 对象转换。
- [ ] 命名、异常、日志、并发遵守 §3 规范。

前端（`zify-web`）：

- [ ] 路由在 `zify-web/src/app/router.tsx` 中声明，页面在 `zify-web/src/pages/`。
- [ ] 可复用业务 UI 在 `zify-web/src/features/`，无业务共享 UI 在 `zify-web/src/shared/ui`。
- [ ] API 文件是 `zify-web/src/api/{module}Api.ts`，类型对齐 HTTP request/response。
- [ ] Zustand 只存跨组件状态。SSE 逻辑拆分在 `zify-web/src/api/engineApi.ts` 和 `zify-web/src/features/chat/hooks/`。

数据库：

- [ ] 使用迁移脚本。MySQL 表包含 `id / created_at / updated_at / is_deleted`。
- [ ] 没有错误使用 `UNIQUE(field, is_deleted)`。
- [ ] 关联 ID 已建索引，大表有 Keyset 分页索引。
- [ ] 列表 API 避免 `SELECT *` 和大字段。pgvector 检索按知识库过滤且不返回 embedding。

LLM：

- [ ] 调用经过 `ModelFacade`，Provider Client 在 `zify-model` 子模块的 `infrastructure/client`。
- [ ] 已配置超时、重试、并发保护和取消行为。流式重试只在首 chunk 前。
- [ ] API Key 不记录、不返回。记录结构化日志。

---

## §10 来源文档

详细决策来源：

```text
glm-docs/02-zify-v01-modules.md
glm-docs/03-zify-v01-frontend-view.md
glm-docs/04-zify-tech-stack.md
glm-docs/05-zify-app-architecture.md
glm-docs/06-zify-code-organization.md
glm-docs/07-zify-LLM-api-calling.md
glm-docs/08-zify-deployment-architecture.md
glm-docs/09-zify-performance-bottleneck.md
glm-docs/10-zify-database-spec.md
```

---

## 行为指令

### 写代码时
- 每个功能用最简单直接的方式实现
- 不引入不必要的设计模式，除非我明确要求
- 不做过度抽象，不过度工程化
- 不引入技术栈以外的依赖，需要时先问我
- 所有外部调用必须有超时设置
- 配置项外化到 application.yml，不硬编码
- 异常处理必须使用 ErrorCode 枚举，禁止硬编码错误码和错误信息

### 改代码时
- 先理解相关模块的设计意图
- 不要为了新功能破坏已有接口契约
- 改完确保已有测试通过

### 不确定时
- 架构不确定时，选择给我 2-3 个方案对比，我来拍板
- 规范没覆盖的情况，先问我，不要自己编规矩
- 碰到任何不明确的情况，先问我，不要自己下结论