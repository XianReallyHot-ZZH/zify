# Dify 核心功能模块总览

更新时间：2026-05-24

Dify 是一个开源的 LLM 应用开发平台，核心定位是帮助团队从原型到生产构建 AI 应用。它把可视化工作流、RAG/知识库、Agent、模型管理、插件工具、发布集成、日志与观测等能力放在同一个平台中。

本文按产品能力域梳理 Dify 的核心功能模块。分析依据包括 Dify 官方文档、官网信息，以及本地源码 `vendors/dify` 中的 README、前端路由、后端模型枚举与核心目录结构。

## 一、应用构建

| 模块 | 说明 |
| --- | --- |
| 应用 Studio / 应用管理 | 创建、导入、复制、管理 AI 应用，是 Dify 的主工作台。本地源码中的应用模式包括 `workflow`、`advanced-chat`、`chat`、`agent-chat`、`completion`、`rag-pipeline` 等。 |
| Workflow 工作流 | 面向单轮任务、批处理任务或后端自动化流程的可视化编排应用。开发者可以通过节点串联模型调用、知识检索、工具调用、代码执行、HTTP 请求、条件分支、循环和人工输入等步骤。 |
| Chatflow 对话流 | 基于工作流引擎构建多轮对话应用，每轮用户消息都会触发流程执行。它支持会话变量、上下文记忆、流式回答和结构化对话控制，适合客服、问答助手、引导式业务流程。 |
| Chatbot 聊天助手 | 更轻量的对话型应用模式，主要通过提示词、模型参数、上下文和应用增强能力快速搭建。适合 FAQ、知识问答、简单客服和个人助手。 |
| Agent 智能体 | 让模型根据目标自主选择工具、解释工具结果并继续推理。Dify 支持基于 Function Calling、ReAct 等策略的 Agent，可用于搜索、诊断、多步骤 API 调用和复杂任务执行。 |
| Text Generator 文本生成器 | 面向非对话式文本生成任务，例如总结、翻译、改写、营销文案、结构化报告生成等。用户提交表单或输入变量后，应用返回一次性生成结果。 |
| Prompt IDE / 应用配置 | 用于配置提示词、模型、参数、变量、输入表单、上下文和调试预览。它承担从提示词实验到应用发布前调优的主要工作。 |
| App Toolkit 应用增强 | 为应用增加开场白、建议问题、文件上传、语音转文字、文字转语音、内容审核、标注回复等能力。这个模块主要服务最终用户体验和生产环境可控性。 |

## 二、工作流编排能力

| 模块 | 说明 |
| --- | --- |
| 可视化画布 | 通过拖拽节点和连线组织应用逻辑，开发者可以在画布上测试、调试和发布流程。源码中的工作流节点覆盖 `llm`、`tool`、`http`、`code`、`knowledge-retrieval`、`if-else`、`iteration`、`loop`、`human-input` 等类型。 |
| LLM 节点 | 调用指定模型完成推理、生成、分类、抽取或结构化输出。它通常是工作流中最核心的智能处理节点。 |
| 条件与分支节点 | 通过 `if-else`、问题分类器、变量判断等方式控制流程走向。适合根据用户意图、输入类型、检索结果或业务规则走不同路径。 |
| 工具与 HTTP 节点 | 调用内置工具、插件工具、自定义 API 或外部 HTTP 服务。用于把模型能力和企业系统、第三方平台、搜索、数据库等能力连接起来。 |
| Code 节点 | 在工作流中执行脚本逻辑，用于数据清洗、格式转换、规则计算和轻量业务逻辑处理。 |
| 循环与迭代节点 | 对列表或重复任务进行循环处理，适合批量生成、批量检索、逐项校验和多步骤数据处理。 |
| Human Input 人工输入节点 | 在工作流执行中暂停并等待人工确认、补充信息或审批。适合高风险操作、需要人工判断的业务流和半自动化场景。 |
| Trigger 触发器 | 支持通过 Webhook、定时任务、插件事件等方式触发工作流运行。它让 Dify 应用不只响应聊天请求，也能成为自动化任务的一部分。 |

## 三、知识库与 RAG

| 模块 | 说明 |
| --- | --- |
| Knowledge / 知识库 | 管理团队或个人文档、FAQ、网页、外部数据等，并将其转换为可检索上下文。源码中许多实现仍沿用 `datasets` 命名。 |
| 文档导入与抽取 | 支持上传 PDF、Word、PPT、文本、Markdown 等常见格式，并进行文本抽取。该能力是构建 RAG 应用的入口。 |
| 清洗、切分与索引 | 对文档进行清洗、分段、chunk 生成、embedding、索引和存储。合理的分块与索引策略直接影响召回质量。 |
| 检索与重排 | 支持语义检索、关键词检索、混合检索、元数据过滤和 rerank。它负责从知识库中找到最相关的片段，作为模型回答的上下文。 |
| Knowledge Retrieval 节点 | 在 Workflow 或 Chatflow 中检索一个或多个知识库，并把相关片段传递给后续 LLM 节点。它是 RAG 应用接入知识库的主要方式。 |
| Knowledge Pipeline / RAG Pipeline | 用可视化流程定义知识入库管道，包括数据源、抽取、处理、分块、索引和测试。适合需要自定义数据处理流程和持续优化检索质量的企业场景。 |
| 外部知识库接入 | 允许连接已有 RAG 系统或第三方知识服务，而不必把全部数据迁移进 Dify。应用通过统一接口获取外部检索结果。 |

## 四、模型、工具与扩展

| 模块 | 说明 |
| --- | --- |
| Model Providers 模型供应商 | 在工作空间级配置 OpenAI、Anthropic、Google、Cohere、Ollama、OpenAI API 兼容模型等供应商凭据。配置后可被所有应用、工作流和知识库能力复用。 |
| 模型类型管理 | 覆盖 LLM、Embedding、Rerank、TTS、STT、图像生成、内容审核等模型类型。不同模块会根据任务选择合适的模型能力。 |
| Tools 工具 | 让 LLM、Agent 或工作流调用外部服务，例如搜索、计算、数据库、图片生成、企业系统 API 等。工具是 Dify 连接外部世界的核心抽象之一。 |
| 自定义工具 | 支持通过 OpenAPI Schema、MCP 或插件方式接入企业内部 API 和第三方服务。适合把 CRM、工单、订单、知识系统等业务能力暴露给 AI 应用。 |
| Plugins 插件系统 | Dify 的扩展机制，模型供应商、工具、Agent 策略、数据源、触发器和 Endpoint 等都可以插件化。插件可从 Marketplace、GitHub 或本地包安装。 |
| Marketplace 插件市场 | 用于发现、安装和管理插件。前端源码中 `plugins` 页面同时包含已安装插件面板和 Marketplace 面板。 |
| MCP 支持 | Dify 可以通过 MCP 连接外部工具生态，也可以把应用作为 MCP Server 发布给支持 MCP 的客户端使用。 |

## 五、发布与集成

| 模块 | 说明 |
| --- | --- |
| Web App 发布 | 每个应用可以发布为独立 Web 应用，并通过 URL 分享给最终用户使用。可用于客服入口、内部助手、知识问答和业务工具。 |
| API Access | 应用发布后自动提供 API 调用入口，业务系统可通过 API Key 调用 Dify 应用能力。本地前端应用详情页把 `API Access` 作为核心导航项。 |
| 网站嵌入 | 已发布应用可以嵌入到现有网站、产品或内部系统中，常见形式包括 iframe、聊天气泡或脚本集成。 |
| 应用模板与 DSL | 应用可以导出为 DSL/YAML，也可以从 DSL 导入。该能力便于跨环境迁移、版本管理、模板复用和团队协作。 |
| Explore / 应用发现 | 用于浏览、安装和运行可复用应用或模板。本地前端路由中有 `explore/apps` 与 `explore/installed`。 |
| Backend-as-a-Service | Dify 的应用、知识库、工具和运行能力都可以通过 API 接入外部业务系统，使其成为 AI 应用后端服务。 |

## 六、运维、观测与持续优化

| 模块 | 说明 |
| --- | --- |
| Overview / Dashboard | 监控消息量、活跃用户、平均交互、Token 使用、成本和应用趋势。用于评估应用使用情况和运营效果。 |
| Logs 日志 | 记录 Web App/API 的真实用户交互、输入输出、耗时、Token、错误和反馈。开发者可用它排查失败案例、分析用户行为并改进应用。 |
| Run History / 节点追踪 | 工作流每次运行会保留执行历史，可查看输入、输出、节点执行顺序、耗时和数据流向。适合调试复杂流程和定位瓶颈。 |
| Annotations 标注回复 | 把高质量问答沉淀为标注库；当相似问题出现时，Dify 可直接返回人工确认过的答案。它能降低幻觉并提升关键问题的一致性。 |
| Tracing / 外部观测集成 | 支持连接 Langfuse、LangSmith、Opik、Arize Phoenix 等平台，追踪模型调用、工具调用、知识检索和工作流执行细节。 |
| 反馈与持续优化闭环 | 通过日志、用户反馈、标注、检索命中和 tracing 数据持续调整提示词、知识库、模型和工作流逻辑。 |

## 七、组织与平台管理

| 模块 | 说明 |
| --- | --- |
| Workspace 工作空间 | Dify 的资源隔离和协作单位，应用、知识库、模型配置、插件、工具、成员和账单都归属于工作空间。 |
| 成员、角色与权限 | 支持按角色管理团队成员，不同角色对应成员管理、模型配置、应用开发、知识库管理和应用使用权限。 |
| 标签与资源管理 | 应用、知识库等资源可以通过标签和列表进行管理，便于团队规模扩大后的检索、分类和治理。 |
| 账号与认证 | 提供注册、登录、邀请成员、OAuth 回调、密码重置等基础账号能力。企业版或云版本通常还会扩展更多身份与访问控制能力。 |
| 计费与额度 | 云服务和企业版本涉及套餐、额度、调用量和账单管理。自托管社区版则主要依赖本地配置和外部模型供应商计费。 |
| 自托管与系统配置 | 社区版可通过 Docker Compose 等方式部署，也支持环境变量、存储、向量库、队列、worker、插件服务和监控集成等配置。 |

## 八、核心模块之间的关系

Dify 的产品结构可以理解为以下链路：

1. 开发者在 Workspace 中配置模型供应商、工具、插件和知识库。
2. 在 Studio 中创建应用，选择 Chatbot、Agent、Text Generator、Workflow、Chatflow 或 RAG Pipeline 等模式。
3. 应用通过 Prompt、Workflow 节点、知识检索、工具调用和模型调用完成业务逻辑。
4. 应用通过 Web App、API、嵌入代码、MCP 或模板方式发布给最终用户或业务系统。
5. 运行后通过 Dashboard、Logs、Annotations、Tracing 和用户反馈持续优化。

## 本地源码线索

本次梳理重点参考了以下本地源码位置：

| 路径 | 线索 |
| --- | --- |
| `vendors/dify/README.md` | Dify 产品定位与核心能力：Workflow、模型支持、Prompt IDE、RAG Pipeline、Agent、LLMOps、Backend-as-a-Service。 |
| `vendors/dify/api/models/model.py` | `AppMode` 枚举定义了 `completion`、`workflow`、`chat`、`advanced-chat`、`agent-chat`、`rag-pipeline` 等应用模式。 |
| `vendors/dify/web/app/(commonLayout)/app/(appDetailLayout)/[appId]/layout-main.tsx` | 应用详情页核心导航包含 Prompt/Workflow 配置、API Access、Logs/Annotations、Overview。 |
| `vendors/dify/web/app/components/workflow/nodes` | 工作流节点目录覆盖 LLM、工具、HTTP、代码、知识检索、条件分支、循环、人审、触发器等。 |
| `vendors/dify/api/core` | 后端核心能力目录包含 `agent`、`app`、`rag`、`workflow`、`tools`、`plugin`、`mcp`、`ops`、`telemetry`、`moderation` 等。 |
| `vendors/dify/web/app/(commonLayout)/plugins/page.tsx` | 插件页同时包含已安装插件面板与 Marketplace。 |
| `vendors/dify/web/app/(commonLayout)/tools/page.tsx` | 工具页展示工具供应商列表。 |
| `vendors/dify/web/app/(commonLayout)/explore` | Explore 模块包含应用列表和已安装应用入口。 |

## 参考资料

- Dify 官网：https://dify.ai
- Dify 官方文档：https://docs.dify.ai
- Dify GitHub 仓库：https://github.com/langgenius/dify
- 本地源码：`vendors/dify`
