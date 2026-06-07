# Dify 核心功能模块全景梳理

> 基于对 `vendors/dify` 源码的深度分析，涵盖后端（Python/Flask + Celery）和前端（Next.js/TypeScript）全模块。

---

## 一、应用引擎（App Engine）

Dify 的核心抽象是"应用"，支持 5 种应用类型：

| 模块 | 说明 |
|------|------|
| **Chat App** | 对话型应用，支持流式响应和多轮对话 |
| **Advanced Chat** | 高级对话应用，基于 Pipeline 的复杂编排能力 |
| **Completion App** | 单轮文本生成应用，非对话式 |
| **Workflow App** | 由可视化工作流驱动的应用 |
| **Agent Chat** | 带有工具调用能力的对话应用（支持 Function Calling 和 ReAct 策略） |

### 应用配置

| 配置类型 | 说明 |
|----------|------|
| **Easy UI 配置** | 可视化配置 Agent、数据集、模型、Prompt 模板、变量 |
| **Workflow UI 配置** | 基于工作流的配置，含变量管理 |
| **Features 配置** | 文件上传、相似问题推荐、开场白、语音转文字、文字转语音等 |

---

## 二、工作流引擎（Workflow Engine）

| 模块 | 说明 |
|------|------|
| **可视化画布** | 拖拽式工作流编辑器，支持节点连线、实时协作、评论批注 |
| **28+ 内置节点** | 包括 LLM、代码执行、HTTP 请求、条件分支、循环迭代、模板转换、变量赋值、列表操作、文档提取等 |
| **触发器节点** | Webhook 触发、定时调度（Cron）、插件触发三种方式 |
| **Human-in-the-Loop** | 工作流暂停等待人工审批/输入，支持邮件投递和超时处理 |
| **DSL 版本管理** | 工作流定义的导入导出、版本历史、回滚恢复 |

### 工作流节点清单

| 节点 | 说明 |
|------|------|
| LLM | 大语言模型调用 |
| Code | 执行自定义 Python/JS 代码 |
| HTTP Request | 发起 HTTP API 调用 |
| If-Else | 条件分支 |
| Iteration | 遍历数组元素循环 |
| Loop | 通用循环结构 |
| Template Transform | Jinja2 模板处理 |
| Variable Assigner | 变量赋值 |
| Variable Aggregator | 聚合多分支变量 |
| Knowledge Retrieval | 查询知识库 |
| Knowledge Index | 知识入库索引 |
| Tool | 调用工具（内置 / API / MCP / 插件 / Workflow-as-Tool） |
| Answer | 输出回答给用户 |
| Question Classifier | 用户问题分类 |
| Parameter Extractor | 从文本中提取结构化参数 |
| Start / End | 工作流入口 / 终止节点 |
| Agent / Agent V2 | Agent 子工作流节点 |
| Document Extractor | 从文档中提取文本 |
| List Operator | 列表过滤 / 排序 / 切片 |
| Data Source | 从外部数据源获取数据 |
| Human Input | 暂停等待人工输入 |
| Trigger Webhook | Webhook 触发的工作流起点 |
| Trigger Schedule | Cron 定时触发 |
| Trigger Plugin | 插件触发 |
| Note | 画布注释节点 |

---

## 三、RAG 与知识库（Knowledge Base）

| 模块 | 说明 |
|------|------|
| **知识库管理** | 数据集的创建、权限管理、元数据标注 |
| **文档解析** | 支持 PDF、Word、Excel、CSV、HTML、Markdown、Notion、网页抓取等 15+ 格式 |
| **文本分块** | 段落分割、Q&A 分割、父子分块三种策略 |
| **向量存储** | 可插拔的向量数据库后端，支持 Milvus、Qdrant、Weaviate、PGVector、Elasticsearch 等 20+ 种 |
| **检索引擎** | 向量检索、关键词检索、混合检索，支持多知识库智能路由（Function Call / ReAct） |
| **Reranking** | 检索后重排序，支持模型重排和加权评分 |
| **RAG Pipeline** | 独立的可视化 RAG 编排管道，内置 9 套模板（按数据源 × 质量等级组合） |
| **摘要索引** | 自动生成文档摘要以提升检索质量 |
| **命中测试** | 测试知识库检索质量的调试工具 |

### 支持的文档解析格式

PDF、Word（DOCX）、Excel、CSV、HTML、Markdown、纯文本、Notion 导出、网页（Jina）、Firecrawl、Watercrawl、Unstructured.io（DOC/EML/EPUB/MSG/PPT/PPTX/XML）、二进制 Blob

### 支持的向量数据库

Chroma、Couchbase、Elasticsearch、Iris、MatrixOne、Milvus、MyScale、OceanBase、OpenGauss、OpenSearch、Oracle、PGVector、PGVecto.rs、Qdrant、SeekDB、VastBase、Weaviate 等 20+ 种

---

## 四、Agent 系统

| 模块 | 说明 |
|------|------|
| **Agent 运行时** | 支持 Function Calling 和 ReAct（CoT）两种策略，可插拔切换 |
| **Agent 花名册（Roster）** | 可复用的工作区级 Agent 定义，包含人设（Soul Prompt）、工具集和知识库绑定 |
| **Dify Agent SDK** | 独立的 Python Agent 运行时包（`dify-agent/`），实现了 Agenton 协议 |
| **Agent 回调** | 工具调用的追踪和日志记录 |

---

## 五、模型管理（Model Management）

| 模块 | 说明 |
|------|------|
| **Provider 管理** | 统一管理 LLM、Embedding、Rerank、TTS、STT 各类模型的供应商配置和密钥 |
| **负载均衡** | 多 Provider 配置间的模型调用分发，提升可靠性和成本优化 |
| **Feature Gate** | 按计费计划控制功能可用性（如自定义 Logo、负载均衡等） |

---

## 六、工具系统（Tools）

| 模块 | 说明 |
|------|------|
| **内置工具** | 50+ 预置工具（Google 搜索、DALL-E、Stable Diffusion、WolframAlpha 等） |
| **API 工具** | 用户通过 OpenAPI/Swagger Schema 自定义工具 |
| **MCP 工具** | 通过 Model Context Protocol 连接外部 MCP Server 消费工具 |
| **插件工具** | 由 Dify 插件提供的工具 |
| **Workflow-as-Tool** | 将已发布的 Workflow 暴露为可复用工具 |

---

## 七、插件系统（Plugin System）

| 模块 | 说明 |
|------|------|
| **插件市场** | 从 Marketplace 安装、管理插件，支持自动升级 |
| **插件权限** | OAuth 授权和细粒度权限管理 |
| **Plugin Daemon** | 独立沙箱进程运行插件，保障安全隔离 |

---

## 八、MCP 协议支持

| 模块 | 说明 |
|------|------|
| **MCP Client** | 作为客户端连接外部 MCP Server，消费工具和资源 |
| **MCP Server** | 将 Dify 工具暴露为 MCP Server，供外部 MCP 客户端调用 |
| **MCP Auth** | 支持 API Key 认证，内置 Firecrawl、Jina、Watercrawl 等认证 Provider |

---

## 九、Prompt 工程

| 模块 | 说明 |
|------|------|
| **Prompt 模板** | 支持变量注入的模板化 Prompt 构建（简单 / 高级两种模式） |
| **LLM Generator** | 使用 LLM 程序化生成内容（如推荐问题、检索查询改写） |

---

## 十、内容安全与审核（Moderation）

| 模块 | 说明 |
|------|------|
| **输入审核** | 审查用户输入内容 |
| **输出审核** | 审查模型输出内容 |
| **多策略支持** | 关键词过滤、OpenAI Moderation API、自定义 API 三种审核策略 |

---

## 十一、数据源（Data Sources）

| 模块 | 说明 |
|------|------|
| **本地文件** | 上传本地文件作为知识来源 |
| **在线文档** | 连接在线文档平台 |
| **云盘** | Google Drive 等云存储集成 |
| **网页抓取** | Firecrawl、Jina、Watercrawl 等网页爬取 |

---

## 十二、应用发布与分享

| 模块 | 说明 |
|------|------|
| **Web 应用发布** | 一键生成可访问的 Web 应用 |
| **访问控制** | 公开 / 指定成员或组的访问权限管理 |
| **嵌入式聊天** | 可嵌入第三方网站的聊天组件 |
| **Web App 认证** | 终端用户的邮箱验证码、密码、SSO 登录 |
| **API 访问** | 开发者文档、API Key 管理、代码示例 |
| **应用导入** | 通过 DSL（JSON/YAML）文件导入导出应用 |

---

## 十三、对话与消息管理

| 模块 | 说明 |
|------|------|
| **会话管理** | 会话的创建、列表、删除、变量管理 |
| **消息管理** | 消息 CRUD、反馈（点赞/点踩）、收藏 |
| **标注回复** | 人工标注 AI 回复，用于质量改进和标注回复功能 |
| **语音服务** | 文字转语音（TTS）和语音转文字（STT） |

---

## 十四、可观测性与日志（LLMOps）

| 模块 | 说明 |
|------|------|
| **Ops Trace** | 集成 Langfuse、Opik、Arize Phoenix 等平台追踪工作流和 LLM 调用 |
| **结构化日志** | 带请求上下文的结构化日志系统 |
| **统计分析** | 应用使用量统计、消息日志、工作流运行日志，带图表分析 |
| **遥测** | OpenTelemetry 和 Sentry 集成用于生产环境监控 |

---

## 十五、用户与工作区管理

| 模块 | 说明 |
|------|------|
| **账号体系** | 注册、登录、OAuth、密码重置、账号激活 |
| **工作区管理** | 多租户工作区，成员管理，角色分配（Owner / Admin / Editor / Normal / Dataset Operator） |
| **终端用户管理** | 与应用交互的终端用户管理 |

---

## 十六、计费系统

| 模块 | 说明 |
|------|------|
| **订阅计划** | 云版本的套餐管理、用量追踪、额度管控 |
| **功能门控** | 按计费计划控制功能可用性 |

---

## 十七、后台任务与调度

| 模块 | 说明 |
|------|------|
| **Celery 任务** | 50+ 异步任务模块（文档索引、邮件发送、工作流执行、触发器处理等） |
| **定时任务** | 13 个调度任务（缓存清理、消息保留、插件更新检查、队列监控等） |
| **事件系统** | 事件驱动的解耦处理（文档索引、应用生命周期等） |

---

## 十八、探索 / 应用市场

| 模块 | 说明 |
|------|------|
| **Explore** | 应用发现市场，推荐应用、已安装应用、试用功能 |

---

## 十九、扩展与定制

| 模块 | 说明 |
|------|------|
| **API 扩展** | 外部 API Hook 点（数据获取、审核） |
| **外部数据工具** | 运行时动态获取外部数据 |
| **自定义品牌** | 自托管部署的白标品牌定制 |

---

## 二十、部署与基础设施

| 模块 | 说明 |
|------|------|
| **Docker Compose** | 完整的容器化部署方案，含 Nginx、SSRF 代理（Squid） |
| **数据库迁移** | Alembic 管理的 SQLAlchemy 数据库版本管理 |
| **Flask 扩展** | 数据库、Redis、Celery、存储、邮件、Session 等 20+ 扩展 |
| **SDK** | Node.js 和 PHP 官方 API 客户端 |
| **共享包** | Contracts（自动生成的 API 客户端）、Dify UI（组件库）、Dev Proxy（开发代理）等 |

---

## 总览

| 维度 | 数量 |
|------|------|
| 应用类型 | 5 种 |
| 工作流节点 | 28+ 个 |
| 向量数据库 | 20+ 种 |
| 文档解析格式 | 15+ 种 |
| 内置工具 | 50+ 个 |
| 后台任务模块 | 50+ 个 |
| 定时调度任务 | 13 个 |
| Flask 扩展 | 20+ 个 |
| 官方 SDK | 2 个（Node.js、PHP） |
