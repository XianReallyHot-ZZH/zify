# Zify 技术选型

> 约束：一个人开发，团队技术栈为 Java，业务数据存 MySQL，向量数据用 PGVector，目标 20-50 人使用（未来可扩展至千人），本地部署。

---

## 选型结论

**Spring Boot 4.0 + Spring AI 2.0 + React**

---

## 技术栈清单

### 后端

| 层 | 技术 | 版本 | 说明 |
|----|------|------|------|
| 语言 | Java | 21 LTS | Virtual Threads 适合 IO 密集的 Agent 调用场景 |
| 框架 | Spring Boot | 4.0 | 模块化单体，Maven 多模块组织 |
| AI 框架 | Spring AI | 2.0.0 | 完整 AI 应用框架：LLM 调用、向量检索、工具调用、原生 OpenAI SDK、MCP 集成 |
| MCP | MCP Java SDK | - | 连接外部 MCP Server（Spring 团队维护，Spring AI 2.0 内置） |
| ORM | MyBatis-Plus | 3.5+ | 业务数据（MySQL） |
| 向量存储 | Spring AI VectorStore + PGVector | - | 知识库向量检索（PostgreSQL） |
| 文档解析 | Apache POI + PDFBox | - | PDF / Word / TXT / Markdown |
| 定时任务 | Quartz | - | 触发器 - Cron 调度 |
| 异步任务 | Virtual Threads + @Async | - | 文档解析、工作流执行、Agent 流式响应 |
| 缓存 | Redis | 7.x | 流式响应中转、文档解析进度、工作流运行状态、对话上下文缓存、接口限流 |

### 前端

| 层 | 技术 | 说明 |
|----|------|------|
| 框架 | React 18 + TypeScript | |
| 工作流画布 | React Flow | 可视化节点编排 |
| UI 组件库 | Ant Design / shadcn/ui | 待定 |
| 状态管理 | Zustand | 轻量，适合中等规模应用 |
| HTTP 客户端 | Axios | |

### 数据库

| 用途 | 技术 | 说明 |
|------|------|------|
| 业务数据 | MySQL 8 | Agent、对话、工作流、工具、触发器等所有业务表 |
| 向量数据 | PostgreSQL + PGVector | 知识库文档 chunk、Embedding 向量、相似度检索 |
| 缓存 | Redis 7 | 临时性、高频、短生命周期的数据 |

### Redis 使用场景

| 场景 | 说明 |
|------|------|
| Agent 流式响应中转 | SSE/WebSocket 连接状态、断线续传位置 |
| 文档解析进度 | 上传后前端轮询解析进度 |
| 工作流运行状态 | 执行中的中间变量、节点状态（生命周期仅几分钟） |
| 对话上下文缓存 | 多轮对话最近 N 条消息，高频读写 |
| 接口限流 | 千人规模时 LLM API 调用限流（原子计数器） |

### 部署

| 组件 | 技术 | 说明 |
|------|------|------|
| 容器化 | Docker Compose | MySQL + PostgreSQL + Redis + 后端 + 前端 + Nginx |
| 反向代理 | Nginx | 前端静态资源 + 后端 API 代理 |

---

## 双数据库架构

```
Spring Boot 4.0 应用（zify-app 启动模块聚合所有 Maven 子模块）
├── MySQL 数据源（MyBatis-Plus）
│   ├── Agent 配置
│   ├── 对话记录
│   ├── 工作流定义
│   ├── 工具配置
│   ├── 触发器配置
│   └── 用户 / 权限（二期）
│
└── PostgreSQL 数据源（Spring AI VectorStore）
    ├── 文档 chunk
    ├── Embedding 向量
    └── 相似度检索
```

---

## 选型理由

### 为什么选 Spring Boot 4.0 + Spring AI 2.0

| 维度 | Spring Boot 4.0 + Spring AI 2.0 | Python FastAPI + LangChain |
|------|----------------------------------|---------------------------|
| 团队匹配 | 团队都是 Java，后续可交接 | 团队不会 Python，只有一个人维护 |
| 规模化 | 千人规模下的事务管理、连接池、监控是框架级支持 | 需要额外工程化建设 |
| 工程化 | 依赖注入、模块化、AOP、定时任务全部开箱即用 | 需要自己搭建或引入第三方库 |
| AI 生态 | Spring AI 2.0 已升级为完整 AI 框架，原生 OpenAI SDK、MCP 内置 | LangChain + LlamaIndex，生态最丰富 |
| 一个人开发效率 | Java 代码量多，但 IDE 辅助好 | Python 代码量少，迭代快 |
| 长期维护 | Spring Boot 3.x 于 2026-06-30 EOL，4.0 是当前长期支持版本 | 无此问题 |

**结论**：团队匹配和规模化是硬约束。Spring AI 2.0 从集成库升级为完整 AI 框架，AI 生态差距进一步缩小。项目尚未开始编码，选用最新版本迁移成本为零。

### 为什么选 Spring Boot 4.0 而不是 3.x

- Spring Boot 3.x 于 2026-06-30 EOL，不再获得安全更新和 Bug 修复
- Zify 尚未开始编码，没有历史代码迁移成本
- Spring AI 2.0（完整 AI 框架）需要 Spring Boot 4.0

### 为什么选 React 而不是 Vue

工作流画布是 Zify 的核心前端能力，[React Flow](https://reactflow.dev/) 是该领域最成熟的库，Vue 没有等价物。

### 为什么选 Java 21 而不是 Java 17

Java 21 是当前 LTS 版本，Virtual Threads 正式可用——Agent 的 ReAct 循环中大量等待 LLM 响应、工具调用等 IO 操作，虚拟线程可以大幅简化异步编程模型，不需要手动管理线程池。Spring Boot 4.0 完全支持。
