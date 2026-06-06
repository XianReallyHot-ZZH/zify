# Zify

AI Agent 开发平台

## 技术栈

### 后端

- Java 21、Spring Boot 4.0、Spring AI 2.0（待接入）
- Maven 多模块工程（1 个父 POM + 9 个业务子模块 + 1 个启动模块）
- MyBatis-Plus 3.5.9（分页、自动填充、逻辑删除）
- MySQL 8.x（业务数据）、PostgreSQL + pgvector（知识库向量检索）、Redis 7（缓存/限流）

### 前端

- React 18 + TypeScript + Vite
- React Router、React Flow（工作流画布）、Zustand（状态管理）
- Axios（请求封装）、Ant Design（UI 组件库）

### 部署

- Docker + Kubernetes，单副本
- Nginx 静态资源 + `/api/**` 反向代理

## 项目结构

```
zify/
├── zify-common/       基础设施：统一响应、异常处理、Redis/MyBatis-Plus 配置
├── zify-model/         Provider 配置、LLM/Embedding 调用
├── zify-tool/          MCP / HTTP / Workflow-as-Tool
├── zify-knowledge/     文档解析、分块、Embedding、pgvector 检索
├── zify-workflow/      工作流编辑、节点执行、变量传递
├── zify-agent/         Agent CRUD、Prompt、模型/工具/知识库绑定
├── zify-engine/        Agent 执行、ReAct 循环、流式响应编排
├── zify-chat/          会话和消息持久化
├── zify-trigger/       Webhook 接收、Cron 调度
├── zify-app/           Spring Boot 启动模块（聚合打包）
└── zify-web/           前端（React + Vite，独立构建）
```

每个业务子模块内部遵循四层分包：

```
src/main/java/com/zify/{module}/
├── api/              Facade 接口、跨模块 DTO
├── domain/           业务逻辑、事务边界
├── infrastructure/   Entity、Mapper、Converter、FacadeImpl
└── adapter/          HTTP / SSE Controller
```

## 快速开始

### 环境要求

- JDK 21+
- Maven 3.9+
- Node.js 20+、npm 10+
- MySQL 8.x、Redis 7.x

### 后端

```bash
# 构建全部模块
mvn clean install

# 启动应用（默认端口 8080）
mvn spring-boot:run -pl zify-app
```

### 前端

```bash
cd zify-web

# 安装依赖
npm install

# 启动开发服务器（默认端口 5173，/api 代理到 8080）
npm run dev
```

### 验证

```bash
curl http://localhost:8080/api/health
# {"code":200,"data":"Zify is running","message":"success"}
```

## 模块依赖图

```
common    → 无
model     → common
tool      → common
knowledge → common, model
workflow  → common, model, knowledge, tool
agent     → common, model, tool, knowledge, workflow
engine    → common, agent, model, tool, knowledge, workflow
chat      → common, agent, engine
trigger   → common, workflow
app       → 所有后端子模块
```

跨模块调用只允许 `Service → 目标模块 Facade`，编译时由 Maven 依赖强制执行。

## 文档

详细设计决策见 `glm-docs/` 目录：

- `02-zify-v01-modules.md` — 模块划分
- `03-zify-v01-frontend-view.md` — 前端视图
- `04-zify-tech-stack.md` — 技术选型
- `05-zify-app-architecture.md` — 应用架构
- `06-zify-code-organization.md` — 代码组织
- `07-zify-LLM-api-calling.md` — LLM 调用规范
- `08-zify-deployment-architecture.md` — 部署架构
- `09-zify-performance-bottleneck.md` — 性能瓶颈预判
- `10-zify-database-spec.md` — 数据库规范

## License

Private — 内部使用
