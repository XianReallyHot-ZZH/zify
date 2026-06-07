# CLAUDE.md

AI 编码助手在 Zify 项目中的工作规范。

使用方式：

1. 任何改动前，先读本文件了解硬性规则。
2. 根据任务所属模块，按需阅读 `glm-docs/` 下的决策文档（§11 文档索引）。
3. 实现完成后，用 **§10 检查清单** 逐项验证。
4. 如果需求与本文冲突，先更新 `glm-docs/*.md` 决策文档，再写代码。

---

## §1 项目总契约

Zify 是模块化单体 AI 应用。一人开发，一期面向 ~50 人内部使用。

技术栈：

- 后端：**Maven 多模块工程**（父 POM + 10 个子模块 + 启动模块 `zify-app`）、Java 21、Spring Boot 4.0、Spring AI 2.0、MyBatis-Plus。
- 前端：`zify-web`（React 18 + TypeScript + Vite + React Router + React Flow + Zustand + Axios + Ant Design），由 Vite 独立构建。
- 数据：MySQL 8.x（业务数据）、PostgreSQL + pgvector（chunk 和 embedding）、Redis 7（缓存、进度、限流）。
- 部署：Docker + K8s，单副本。

架构硬约束：

- 跨模块调用必须走目标模块 `api` Facade。禁止注入其他模块的 Service / Mapper / Repository / Entity / Controller。
- `<dependency>` 声明必须与 §2 依赖图完全匹配，编译时强制边界。
- **禁止在数据库事务内调用 LLM、Embedding、MCP、HTTP 工具或其他慢外部 API。**

一期做：Agent 管理、模型管理、统一工具系统（MCP Client / HTTP 工具 / Workflow-as-Tool）、知识库 RAG、简版工作流引擎、Webhook/Cron 触发器、对话管理。

一期不做：多租户/计费/应用市场/插件/SDK/SSO；工作流循环/人工审批/复杂 RAG；默认 Provider 负载均衡。

---

## §2 任务路由

后端模块：

```text
agent      Agent CRUD、Prompt、绑定
chat       会话和消息持久化
engine     Agent 执行、ReAct 循环、流式响应编排
workflow   工作流编辑数据、节点执行、变量传递、运行日志
knowledge  文档、解析、分块、Embedding、pgvector 检索
tool       MCP / HTTP / Workflow-as-Tool 定义
trigger    Webhook 接收、Cron 调度、触发日志
model      Provider 配置、LLM/Embedding 调用
common     仅放基础设施：配置、异常、统一响应、工具类
app        启动模块：聚合所有后端子模块
```

前端模块：`zify-web`（React 18 + TypeScript + Vite）。不是 Spring Boot 子模块。

允许的模块依赖（每个子模块隐式依赖 `zify-common`）：

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
app       -> 所有后端子模块
```

功能实现顺序：

1. 判断功能所属模块。检查依赖关系是否允许。
2. **验证 `pom.xml` 是否已声明目标模块依赖**；未声明则先添加。
3. 新增/复用 `api/dto` 中的 DTO → Entity、Mapper、Converter → Service（事务）→ FacadeImpl → Controller。
4. 确认没有跨模块引用 Service / Mapper / Entity。
5. 用户可见功能再新增前端 API、类型、页面。

---

## §3 后端硬性规则

**代码组织和详细分层规则** → 阅读 `glm-docs/06-zify-code-organization.md`

硬性规则（不可违反）：

- `Controller -> Service`，Controller 只能调用本模块 Service。
- 跨模块只允许 `Service -> 目标模块 Facade`。
- Entity 不能跨模块。Facade 不能返回 Entity 或分页对象。
- HTTP Request / Response 不能进入 domain 层。
- `common` 不能出现业务概念。不允许为绕过循环依赖把业务类塞进 `common`。
- 事务必须短，禁止覆盖外部调用。
- 异常使用 `BusinessException` + `ErrorCode` 枚举，禁止硬编码。
- 日志使用 SLF4J `{}` 占位符，禁止 `System.out`。禁止输出 API Key / 密码。
- 线程池用 `ThreadPoolExecutor` 构造，禁止 `Executors.newXxx()`。

---

## §4 前端硬性规则

**详细前端代码组织** → 阅读 `glm-docs/06-zify-code-organization.md`

硬性规则：

- 使用 Vite + React Router，禁止 Next.js。
- `pages` 不能被其他页面引用；`features` 不能引用 `pages`。
- `stores` 不发 HTTP 请求，不互相 import。
- API 文件 `api/*Api.ts` 对齐后端 HTTP request/response，不对齐 Facade DTO。
- Zustand 只存跨组件状态。表单草稿、弹窗开关不进全局 Store。
- SSE 创建放 `api/engineApi.ts`，UI 状态放 `features/chat/hooks/useChatStream.ts`。

---

## §5 数据库硬性规则

**完整数据库规范（字段类型、索引、分页、JSON、事务、建表模板）** → 阅读 `glm-docs/10-zify-database-spec.md`

**核心数据模型（21 张表和关系）** → 阅读 `glm-docs/11-zify-core-data-model.md`

硬性规则：

- 所有变更用 Flyway 迁移脚本，禁止手工改表。
- MySQL 业务表必须包含 `id / created_at / updated_at / is_deleted`。ID 用 `CHAR(36)` UUID。
- 软删除唯一约束用 generated column，禁止 `UNIQUE(field, is_deleted)`。
- 关联 ID 必须建索引。大表用 Keyset 分页，禁止 OFFSET。
- 列表接口禁止 `SELECT *`，大字段只在详情按主键查询。
- pgvector 检索必须带 `knowledge_id` 过滤，禁止返回 `embedding`。
- 事务隔离级别 `READ_COMMITTED`，事务内禁止外部调用。

---

## §6 LLM 调用硬性规则

**完整 LLM 调用规范（超时、重试、熔断、线程模型）** → 阅读 `glm-docs/07-zify-LLM-api-calling.md`

硬性规则：

- 所有外部模型调用归口到 `model` 模块 `ModelFacade`。
- API Key 只在 `model` 模块内使用，禁止返回或记录。
- SSE 断开/超时时必须取消上游 LLM 调用。
- 使用显式 retry wrapper，禁止 `@Retryable`。

---

## §7 部署与性能

**完整部署架构和性能瓶颈分析** → 阅读 `glm-docs/08-zify-deployment-architecture.md` 和 `glm-docs/09-zify-performance-bottleneck.md`

核心约束：

- TLS 在 Ingress 终止。上传写 PVC 不写本地磁盘。API Key 加密存储。
- Docker Compose 只用于本地开发，生产用 K8s。
- 每日备份 MySQL / PostgreSQL / uploads，保留 14 天。

---

## §8 产品规则

**完整模块定义和前端页面结构** → 阅读 `glm-docs/02-zify-v01-modules.md` 和 `glm-docs/03-zify-v01-frontend-view.md`

核心路径：模型管理 → 工具 → 知识库 → 工作流（可选）→ Agents → 对话。

---

## §9 行为指令

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

---

## §10 实现检查清单

后端：

- [ ] 模块归属正确，跨模块依赖在允许列表中。
- [ ] `pom.xml` 的 `<dependency>` 声明与依赖图匹配。
- [ ] 跨模块调用只走 Facade，Controller 只调用本模块 Service。
- [ ] 没有 Entity / Mapper / Service 跨模块。
- [ ] HTTP Request / Response 没有进入 domain 层。
- [ ] 事务没有覆盖外部调用。
- [ ] 命名、异常、日志、并发遵守规范（→ `glm-docs/06`）。

前端：

- [ ] 路由在 `zify-web/src/app/router.tsx`，页面在 `zify-web/src/pages/`。
- [ ] API 文件是 `zify-web/src/api/{module}Api.ts`，类型对齐 HTTP request/response。
- [ ] Zustand 只存跨组件状态。SSE 逻辑在 `api/engineApi.ts` 和 `features/chat/hooks/`。

数据库（→ `glm-docs/10`）：

- [ ] 使用迁移脚本。MySQL 表包含 `id / created_at / updated_at / is_deleted`。
- [ ] 没有 `UNIQUE(field, is_deleted)`。关联 ID 已建索引。
- [ ] 列表接口避免 `SELECT *` 和大字段。

LLM（→ `glm-docs/07`）：

- [ ] 调用经过 `ModelFacade`。已配置超时、重试、并发保护。
- [ ] API Key 不记录、不返回。

---

## §11 文档索引

详细决策文档，按需阅读：

| 文档 | 内容 |
|------|------|
| `glm-docs/02-zify-v01-modules.md` | 一期功能模块定义、功能范围、边界 |
| `glm-docs/03-zify-v01-frontend-view.md` | 前端页面结构、导航、用户路径 |
| `glm-docs/04-zify-tech-stack.md` | 技术选型及理由 |
| `glm-docs/05-zify-app-architecture.md` | 应用架构选型（Maven 多模块） |
| `glm-docs/06-zify-code-organization.md` | 后端和前端代码组织规范（分层、命名、异常、日志、并发） |
| `glm-docs/07-zify-LLM-api-calling.md` | LLM 调用技术方案（超时、重试、熔断、线程模型） |
| `glm-docs/08-zify-deployment-architecture.md` | 部署架构（K8s、Nginx、备份） |
| `glm-docs/09-zify-performance-bottleneck.md` | 性能瓶颈预判和应对策略 |
| `glm-docs/10-zify-database-spec.md` | 完整数据库规范（字段类型、索引、分页、JSON、事务、建表模板、实例配置） |
| `glm-docs/11-zify-core-data-model.md` | 核心数据模型（21 张表和关系） |
