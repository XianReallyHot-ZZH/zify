# Zify 技术栈选型结论

更新时间：2026-05-24

Zify 是一个 AI Agent 开发平台，第一期目标是一个人可开发、本地部署、面向团队内部 20-50 人使用；后续可能扩展到千人规模。团队技术栈要求使用 React 前端，后端团队主栈是 Java，业务数据要求存 MySQL，向量数据可以选择 PostgreSQL + pgvector。

基于这些约束，最终推荐采用：

```text
React + TypeScript + Vite + Ant Design + React Flow
Spring Boot 3 + Spring AI + MyBatis-Plus + Flyway
MySQL 8 + PostgreSQL pgvector + Redis
Docker Compose
```

## 一、最终技术栈总览

| 层 | 技术选型 | 说明 |
| --- | --- | --- |
| 前端框架 | React + TypeScript + Vite | React 满足团队前端技术要求；Vite 足够轻量，适合本地部署型管理系统。 |
| UI 组件库 | Ant Design | 表格、表单、弹窗、上传、菜单等后台管理组件成熟，适合一个人快速开发。 |
| 工作流画布 | React Flow | 用于 Workflow 节点画布、拖拽、连线、缩放、节点选择和位置保存。 |
| 请求与服务端状态 | TanStack Query | 管理 API 请求、缓存、刷新、加载状态和错误状态。 |
| 前端本地状态 | Zustand | 管理画布选中节点、侧边栏状态、临时编辑状态、聊天临时状态等轻量状态。 |
| 后端框架 | Spring Boot 3 | Java 主栈，适合团队长期维护和后续扩展。 |
| AI 抽象 | Spring AI | 负责模型调用、Embedding、Tool Calling、Vector Store、MCP Client 等 AI 能力接入。 |
| 数据访问 | MyBatis-Plus | 适合 Java 团队使用，SQL 可控，开发效率高。 |
| 数据库迁移 | Flyway | 管理 MySQL 表结构版本，保证本地部署和后续升级可追踪。 |
| 业务数据库 | MySQL 8 | 存储 Zify 的核心业务数据，满足业务数据存 MySQL 的要求。 |
| 向量数据库 | PostgreSQL + pgvector | 存储文档 chunk 的 embedding 向量，负责知识库 RAG 检索。 |
| 缓存 / 队列 | Redis | 一期可轻量使用，后续可承担任务状态、缓存、限流、队列等能力。 |
| 文件存储 | 本地文件系统 | 一期用于文档上传和本地部署；后续可演进到 MinIO。 |
| 部署 | Docker Compose | 一期本地部署优先；后续千人规模可演进到 Kubernetes。 |
| 模型协议 | OpenAI-compatible API 优先 | 优先接入兼容 OpenAI API 的云模型或本地模型服务。 |
| MCP | Spring AI MCP Client | 一期只做 MCP Tool Client，不做 Zify 发布为 MCP Server。 |

## 二、前端技术方案

推荐前端组合：

```text
React + TypeScript + Vite + Ant Design + React Flow + TanStack Query + Zustand
```

### React + TypeScript + Vite

React 用于构建 Zify 的前端界面和交互，TypeScript 提供类型约束，Vite 负责开发服务器和前端构建。

Zify 一期前端主要页面：

```text
Agents
Workflow
Knowledge
Tools
Models
Chat
```

不建议第一期使用 Next.js。Zify 是本地部署的内部平台，不需要 SEO、SSR 和复杂全栈框架能力，React + Vite 更轻、更直接。

### Ant Design

Ant Design 用于快速构建后台管理界面。

适合 Zify 的场景：

- Agent 列表和表单
- Workflow 列表
- Knowledge 文档列表和上传
- Tool 配置表单
- Model 配置表单
- 弹窗、抽屉、菜单、分页、提示消息

第一期更推荐 Ant Design，而不是 Shadcn UI。原因是 Ant Design 的后台管理组件更完整，可以减少一个人开发时的 UI 组装成本。

### React Flow

React Flow 用于 Workflow 一级模块的前端画布。

它负责：

- 节点展示
- 节点拖拽
- 节点连线
- 画布缩放和平移
- 节点选择
- 节点位置保存
- 小地图和控制器，可选

React Flow 只负责前端编辑，不负责工作流执行。

推荐架构：

```text
React Flow
-> 编辑 Workflow 节点和连线
-> 生成 WorkflowDefinition JSON
-> 保存到后端 MySQL
-> 后端 Workflow Executor 执行
```

### TanStack Query

TanStack Query 负责服务端状态管理。

适用场景：

- 查询 Agent 列表
- 保存 Workflow
- 上传文档后刷新知识库
- 测试模型连接
- 测试工具调用
- 获取聊天历史或流式接口前的元数据

### Zustand

Zustand 负责轻量本地状态。

适用场景：

- 当前选中的 Workflow 节点
- 画布编辑状态
- 节点配置面板状态
- Chat 输入区临时状态
- 侧边栏折叠状态

## 三、后端技术方案

推荐后端组合：

```text
Spring Boot 3 + Spring AI + MyBatis-Plus + Flyway
```

### Spring Boot 3

Spring Boot 作为 Zify 后端主框架，负责提供 API 服务和承载核心业务逻辑。

主要职责：

- Agent 管理 API
- Workflow 管理 API
- Workflow 执行接口
- Knowledge 管理 API
- Tool 管理 API
- Model 管理 API
- Chat 对话接口
- 文件上传接口
- 本地部署健康检查接口

### Spring AI

Spring AI 负责 AI 能力接入和抽象。

主要用于：

- Chat Model 调用
- Embedding Model 调用
- Tool Calling
- Vector Store 接入
- MCP Client
- RAG 相关基础能力

Spring AI 不应成为 Zify 的产品数据模型。Zify 的 Agent、Workflow、Tool、Knowledge 等核心对象应由 Zify 自己定义和持久化。

### MyBatis-Plus

MyBatis-Plus 用于访问 MySQL 业务数据。

适用原因：

- Java 团队熟悉
- SQL 可控
- 适合后台管理系统 CRUD
- 便于后续复杂查询优化

### Flyway

Flyway 用于管理数据库结构迁移。

迁移文件示例：

```text
db/migration/
  V1__init_schema.sql
  V2__create_agent_tables.sql
  V3__create_workflow_tables.sql
  V4__create_tool_tables.sql
  V5__create_knowledge_tables.sql
```

Flyway 确保本地部署、团队协作和后续版本升级时数据库结构可追踪、可重复执行。

## 四、数据存储方案

### MySQL：业务主库

MySQL 存储所有核心业务数据。

建议业务表包括：

```text
agents
workflows
workflow_nodes
workflow_edges
tools
mcp_servers
model_providers
knowledge_bases
documents
conversations
messages
```

建议原则：

```text
MySQL = 业务事实
```

也就是说，Zify 的业务配置、实体关系和用户操作产生的结构化数据都以 MySQL 为准。

### PostgreSQL + pgvector：向量库

PostgreSQL + pgvector 只负责向量检索相关数据。

建议存储：

```text
document_chunks
chunk_embeddings
chunk_metadata
```

建议原则：

```text
PostgreSQL + pgvector = 向量索引和检索
```

不要把业务主数据分散到 PostgreSQL，避免 MySQL 和 PostgreSQL 双业务主库带来的心智负担。

### Redis：缓存与后续队列能力

Redis 一期可以轻量使用，后续逐步承担更多职责。

一期可用于：

- 临时会话缓存，可选
- Workflow 执行临时状态，可选
- 文件处理任务状态，可选

后续可用于：

- 异步任务队列
- 限流
- 分布式锁
- 缓存模型配置
- 缓存 MCP tools/list 结果

### 文件存储

一期使用本地文件系统。

用于：

- 知识库原始文档
- 文档抽取中间结果
- 上传文件

后续如果扩展到更大规模或多实例部署，可以迁移到 MinIO。

## 五、Workflow 架构原则

Workflow 是 Zify 的核心一级模块，应自研产品模型和执行器。

核心原则：

```text
React Flow 负责编辑
MySQL 保存 WorkflowDefinition JSON 和元数据
Spring Boot 自研 Workflow Executor
Spring AI 负责模型、工具、Embedding、MCP 调用
```

不要把 Workflow 产品模型绑定死在 Spring AI、LangChain 或其他 Agent 框架里。

推荐的 WorkflowDefinition JSON：

```json
{
  "nodes": [
    { "id": "start", "type": "start" },
    { "id": "rag", "type": "knowledge_retrieval" },
    { "id": "agent", "type": "agent_loop" },
    { "id": "end", "type": "end" }
  ],
  "edges": [
    { "source": "start", "target": "rag" },
    { "source": "rag", "target": "agent" },
    { "source": "agent", "target": "end" }
  ]
}
```

一期节点范围：

```text
Start
Knowledge Retrieval
LLM
Agent Loop
HTTP Tool
MCP Tool
Condition
End
```

## 六、工具与 MCP 方案

工具模块统一管理：

```text
Tools
  ├── HTTP Tool
  └── MCP Tool
```

### HTTP Tool

HTTP Tool 适合接入内部系统 API。

一期支持：

- URL
- Method
- Headers
- 参数 schema
- 返回值说明
- 工具测试

### MCP Tool

MCP 一期只做 Client：

```text
Zify -> MCP Server -> 外部工具
```

一期支持：

- 配置 MCP Server 地址
- 获取 tools/list
- 调用 tools/call
- 简单 Bearer/API Key 鉴权
- 工具 schema 展示
- 工具调用测试

一期不做：

- Zify Agent 发布成 MCP Server
- MCP Marketplace
- OAuth 全流程
- stdio MCP server 托管
- MCP resources
- MCP prompts

## 七、部署方案

一期使用 Docker Compose。

服务组成：

```text
zify-web
zify-api
mysql
postgres-pgvector
redis
```

一期重点：

- 一条命令启动
- `.env.example`
- MySQL 初始化
- PostgreSQL pgvector 初始化
- 本地文件存储目录挂载
- 健康检查
- 清晰 README

后续扩展到千人规模时，可以演进为：

```text
Nginx / Gateway
多 zify-api 实例
独立 worker
MySQL 主从和备份
PostgreSQL pgvector 独立资源
Redis 队列
MinIO
Kubernetes
```

## 八、最终结论

Zify 最终技术选型为：

```text
React + TypeScript + Vite + Ant Design + React Flow
Spring Boot 3 + Spring AI + MyBatis-Plus + Flyway
MySQL 8 + PostgreSQL pgvector + Redis
Docker Compose
```

这个方案满足：

- 前端使用 React
- 后端符合 Java 团队技术栈
- 业务数据存 MySQL
- 向量数据使用 pgvector
- 一期适合一个人开发和本地部署
- 后续可以平滑扩展到千人规模

最重要的架构判断：

```text
Zify 的核心产品模型自研，
Spring AI 只作为 AI 能力适配层，
Workflow Executor 由 Zify 自己实现。
```
