# Zify v0.1 一期功能模块规划

更新时间：2026-05-24

Zify 是一个面向团队内部使用的轻量 AI Agent 平台。第一期目标不是复刻 Dify，而是在一个人可开发、本地部署、20-50 人内部使用的约束下，做出一条完整可用的 Agent 平台闭环。

第一期核心闭环：

```text
配置模型
-> 创建知识库
-> 配置工具
-> 设计工作流
-> 创建 Agent 并引用工作流
-> 用户通过聊天界面使用 Agent
-> 本地部署运行
```

第一期明确不做账号权限、Dashboard、产品化 Logs、插件市场、RAG Pipeline、外部知识库、计费、多租户和完整 Dify 级生态能力。

## 一期模块总览

| 模块 | 定位 | 核心功能 | 一期边界 |
| --- | --- | --- | --- |
| Agent 管理 | 管理可被用户使用的 AI 助手 | 创建、编辑、删除、调试 Agent；配置提示词、模型、知识库、工具、工作流引用 | 不做多应用类型，不做权限，不做应用市场 |
| Agent 聊天 | 普通内部用户的主要使用入口 | 选择 Agent、发送消息、查看流式回答、查看引用来源和工具调用状态 | 不做登录、私有会话、分享权限、嵌入式 widget |
| Workflow 工作流 | 独立的核心编排能力，也是 Zify 的产品亮点 | 工作流列表、创建、编辑、测试、被 Agent 引用；支持轻量节点体系 | 独立一级菜单，但第一期只做受限工作流，不做完整 Dify 级画布能力 |
| 知识库 | 内部知识接入与 RAG 基础 | 文档上传、文本抽取、切分、向量化、检索测试、引用来源 | 不做复杂 RAG Pipeline、外部知识库、混合检索和 rerank |
| 工具 | Agent 和 Workflow 调用外部能力的入口 | HTTP Tool、MCP Tool、工具测试、工具 schema | 不做插件系统、Marketplace、OAuth 全流程和 Zify MCP Server 发布 |
| 模型 | 全局模型资源配置 | OpenAI-compatible Chat Model、Embedding Model、本地模型 endpoint、连接测试 | 不做多供应商复杂适配、模型负载均衡、TTS/STT、多模态 |
| 本地部署 | 工程交付能力 | Docker Compose、环境变量、数据库、向量库、文件存储、初始化文档 | 不做云部署、Kubernetes、高可用和多租户 |

## 1. Agent 管理

### 定位

Agent 是 Zify 面向用户交付的核心对象。第一期不再区分 Dify 式的多种应用类型，而是直接认为：

```text
应用 = Agent
```

Agent 是一个配置好的 AI 助手，它可以绑定模型、提示词、知识库、工具和工作流，并通过聊天界面对用户提供服务。

### 模块功能

- Agent 列表：展示所有 Agent，支持搜索、启用/停用、删除。
- 创建 Agent：配置名称、描述、图标、系统提示词、默认模型。
- 编辑 Agent：配置模型参数、绑定知识库、绑定工具、引用工作流。
- 调试 Agent：在管理侧直接发起测试对话，验证提示词、RAG、工具和工作流效果。
- Agent 运行参数：配置温度、最大 token、最大工具调用次数、是否启用知识库引用等。

### 与 Workflow 的关系

Workflow 是独立一级模块，Agent 不在编辑页里完整编辑工作流，而是轻量引用工作流。

建议关系：

```text
Agent
  ├── 基础配置
  ├── 模型配置
  ├── 提示词配置
  ├── 知识库绑定
  ├── 工具绑定
  └── 工作流引用
        └── 选择一个 Workflow 作为执行流程
```

第一期可以提供一个默认内置工作流：

```text
用户消息 -> 知识检索 -> Agent Loop -> 最终回答
```

如果 Agent 绑定了自定义 Workflow，则按该 Workflow 执行。

### 一期边界

做：

- Agent 列表
- 创建 / 编辑 / 删除 Agent
- Agent 启用 / 停用
- Agent 调试聊天
- Agent 引用 Workflow
- Agent 绑定知识库和工具

不做：

- 多应用类型
- 应用模板市场
- Agent 权限
- Agent 可见范围
- Agent 发布审批
- 多 Workspace
- 复杂版本管理

## 2. Agent 聊天

### 定位

Agent 聊天是普通内部用户使用 Zify 的主要入口。用户不需要理解模型、RAG、工具调用或工作流，只需要选择一个 Agent 并开始对话。

```text
用户
-> Agent 聊天界面
-> Agent 引用的 Workflow
-> 模型 / 知识库 / 工具
-> 返回回答
```

### 模块功能

- Agent 选择页：展示可用 Agent。
- 对话页面：展示消息列表、输入框、流式输出。
- 多轮对话：保留当前会话上下文。
- 引用来源：当回答使用知识库内容时展示来源文档或 chunk。
- 工具调用状态：展示“正在调用工具”“工具调用完成”等简单状态。
- 会话操作：清空当前会话、重新生成回答，可选。

### 与 Chatflow 的关系

Chatflow 不作为独立一级产品模块。它是 Agent 聊天背后的执行方式：

```text
Chatflow = 聊天消息触发的 Workflow 执行
```

用户看到的是聊天界面，开发者管理的是 Agent 和 Workflow。

### 一期边界

做：

- 稳定的 Chatbot UI
- 多轮上下文
- 流式回答
- 引用来源展示
- 简单工具调用状态展示

不做：

- 用户登录
- 会话归属用户
- 私有会话
- 分享链接权限
- Web App 发布配置系统
- iframe / widget 嵌入
- 移动端深度适配

第一期可以简单约定：

```text
每个启用的 Agent 自动拥有聊天入口：/agents/:agentId/chat
```

## 3. Workflow 工作流

### 定位

Workflow 是 Zify 的独立核心模块，也是第一期保留的产品亮点。它负责定义 Agent 背后的执行逻辑，让 Agent 不只是一个提示词加模型的聊天机器人，而是具备可配置流程、工具调用和知识检索能力的自动化助手。

Workflow 应作为一级菜单独立存在，因为它后续会演进为重量级能力：

- 工作流管理
- 工作流编辑
- 工作流测试
- 节点体系扩展
- 工作流复用
- 被多个 Agent 引用

### 模块功能

- Workflow 列表：查看、创建、编辑、删除工作流。
- Workflow 编辑：配置节点、节点连接、输入输出、执行顺序。
- Workflow 测试：输入测试消息或参数，查看执行结果。
- Workflow 引用：Agent 可以选择一个 Workflow 作为运行流程。
- Workflow 执行：按节点顺序执行，支持条件分支和 Agent Loop。

### 一期节点体系

第一期只做最小节点集：

| 节点 | 说明 |
| --- | --- |
| Start | 工作流入口，接收用户消息或测试输入。 |
| Knowledge Retrieval | 从绑定知识库中检索相关内容。 |
| LLM | 调用模型生成、总结、分类或结构化输出。 |
| Agent Loop | 让模型在限定轮数内决定是否调用工具，并最终生成回答。 |
| HTTP Tool | 调用已配置的 HTTP 工具。 |
| MCP Tool | 调用已配置的 MCP 工具。 |
| Condition | 根据变量或模型判断结果走不同分支。 |
| End | 工作流结束，返回最终结果。 |

### 推荐的一期执行形态

默认 Agent 工作流：

```text
Start
-> Knowledge Retrieval
-> Agent Loop
-> End
```

带条件分支的工作流：

```text
Start
-> Condition
   -> 知识问题：Knowledge Retrieval -> LLM
   -> 工具问题：Agent Loop / Tool
-> End
```

### 一期边界

做：

- Workflow 作为一级菜单
- Workflow 列表
- Workflow 创建 / 编辑 / 删除
- Workflow 测试运行
- Agent 引用 Workflow
- 最小节点体系
- 节点顺序执行
- 简单条件分支
- Agent Loop 多轮 tool call
- 最大工具调用次数限制
- 工具超时限制

不做：

- 完整 Dify 级自由画布
- 复杂拖拽体验，除非实现成本可控
- 循环节点
- 并行节点
- 人工审批节点
- 定时触发
- Webhook 触发
- 子工作流
- 代码节点
- 复杂变量系统
- 工作流版本管理
- 工作流权限
- 工作流 Marketplace

第一期重点是：

```text
让 Agent 的执行过程可配置、可复用、可测试。
```

而不是：

```text
做一个万能自动化编排平台。
```

## 4. 知识库

### 定位

知识库是 Zify 支撑内部 Agent 的基础能力。团队内部场景下，Agent 的价值往往来自企业文档、制度、产品资料、FAQ、运维手册和项目文档等私有知识。

RAG 检索不作为独立一级模块，而是知识库模块和 Workflow 节点能力的一部分。

### 模块功能

- 知识库列表
- 创建知识库
- 上传文档
- 文本抽取
- 文档切分
- 生成 embedding
- 存入向量库
- 查看文档 chunk
- 检索测试
- 被 Workflow 的 Knowledge Retrieval 节点调用
- 在 Agent 回答中展示引用来源

### 一期边界

做：

- 文件上传
- Markdown / TXT 优先，PDF 可作为增强项
- 固定切分策略
- 向量检索
- topK 配置
- 相似度阈值
- 引用来源展示
- 检索测试

不做：

- 可视化 RAG Pipeline
- 外部知识库接入
- 网页爬取
- Notion / Confluence / Google Drive 同步
- 混合检索
- rerank
- 摘要索引
- 元数据复杂过滤
- 文档权限

## 5. 工具

### 定位

工具让 Agent 和 Workflow 能调用外部系统，是 Zify 从“问答平台”变成“能办事平台”的关键。

第一期工具模块包含：

```text
HTTP Tool + MCP Tool
```

MCP 不单独做一级模块，而是工具模块下的一种工具来源。

### 模块功能

HTTP Tool：

- 配置工具名称和描述
- 配置 URL、Method、Headers
- 配置参数 schema
- 配置返回值说明
- 测试工具调用

MCP Tool：

- 配置 MCP Server 地址
- 读取 MCP tools/list
- 选择可用 MCP tools
- 调用 MCP tools/call
- 测试 MCP 工具

Workflow / Agent 侧：

- Workflow 节点可调用工具
- Agent Loop 可由模型决定是否调用工具
- Agent 可绑定允许使用的工具
- 限制最大工具调用轮数和超时时间

### 一期边界

做：

- HTTP Tool
- 最小 MCP Tool Client
- 工具测试
- 工具绑定到 Agent / Workflow
- 工具调用超时

MCP 第一期开口：

- 支持连接已有 MCP Server
- 支持 tools/list
- 支持 tools/call
- 支持简单 Bearer/API Key 鉴权
- 支持工具 schema 展示

不做：

- MCP Marketplace
- stdio MCP Server 托管
- OAuth 全流程
- MCP resources
- MCP prompts
- 把 Zify Agent 发布成 MCP Server
- 插件系统
- 工具版本管理
- 工具权限

## 6. 模型

### 定位

模型是 Zify 的全局基础资源。Agent、Workflow、知识库 embedding 都需要复用模型配置。

第一期模型配置应尽量务实：

```text
只要兼容 OpenAI API 格式，就能接入。
```

### 模块功能

- 配置模型供应商
- 配置 Chat Model
- 配置 Embedding Model
- 配置 Base URL、API Key、Model Name
- 测试模型连接
- 设置默认 Chat Model
- 设置默认 Embedding Model

### 一期边界

做：

- OpenAI-compatible Chat Model
- OpenAI-compatible Embedding Model
- 本地模型 endpoint 配置
- 连接测试
- 默认模型设置

不做：

- 多供应商复杂适配
- 模型负载均衡
- 模型额度管理
- Rerank Model
- TTS / STT
- 图像生成
- 内容审核模型
- 模型供应商市场

## 7. 本地部署

### 定位

Zify 第一期面向内部本地部署，因此部署体验是产品能否被团队试用的关键。本地部署不是前端菜单模块，而是工程交付模块。

### 模块功能

- Docker Compose
- `.env.example`
- 数据库初始化
- 向量库初始化
- 文件存储目录
- 初始化脚本
- 本地启动 README
- 健康检查

### 一期边界

做：

- 一条命令启动
- 本地数据库
- 本地文件存储
- 本地向量库或内置向量存储
- 清晰部署文档
- 初始化脚本

不做：

- Kubernetes
- 云部署
- 多环境部署平台
- 高可用
- 横向扩容
- 计费
- 租户隔离
- 权限系统

## 明确不进一期的模块

第一期不做：

- 账号登录
- 权限控制
- Workspace
- 成员管理
- 角色管理
- Dashboard
- Logs 产品页面
- API Access
- Web App 发布配置系统
- 插件系统
- Marketplace
- 完整 Dify Workflow
- RAG Pipeline
- 外部知识库
- 多模态
- TTS / STT
- 应用模板市场
- 计费额度

关于日志需要单独说明：

```text
不做 Logs 产品模块，但开发阶段应保留控制台日志或内部调试记录。
```

否则后续排查模型调用、工具调用、MCP 调用、RAG 检索问题会比较困难。

## 一期前端信息架构建议

推荐一级菜单：

```text
Agents
Workflow
Knowledge
Tools
Models
Chat
```

说明：

- `Agents`：管理 Agent，配置 Agent 引用哪个 Workflow。
- `Workflow`：独立管理和编辑工作流，是后续重点扩展模块。
- `Knowledge`：管理知识库和检索测试。
- `Tools`：管理 HTTP Tool 和 MCP Tool。
- `Models`：管理 Chat Model 和 Embedding Model。
- `Chat`：普通用户使用 Agent 的聊天入口。

`本地部署` 不放在前端菜单中，作为工程交付和文档内容。

## 一期最终定位

Zify v0.1 的定位是：

```text
一个本地部署的内部 Agent 平台。
开发者可以配置模型、知识库、工具和独立工作流，
再把工作流绑定到 Agent。
普通用户通过聊天界面使用这些 Agent。
```

这样既能保证第一期有清晰可用的 Agent 闭环，也为后续把 Workflow 做成 Zify 的核心重量级能力留下空间。
