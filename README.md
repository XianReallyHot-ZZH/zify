# Zify

AI Agent 开发平台

## 技术栈

### 后端

- Java 21、Spring Boot 4.0、Spring AI 2.0
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
- make（可选，用于 Makefile 命令）

### 一键启动

```bash
# Linux / macOS / Git Bash
bash start.sh

# Windows cmd 或双击
start.bat
```

脚本自动完成：检查环境 → 构建后端 → 启动后端 → 健康检查 → 启动前端。

### 手动启动

```bash
# 后端
mvn package -DskipTests
java -jar zify-app/target/zify-app-0.1.0-SNAPSHOT.jar

# 前端
cd zify-web && npm install && npm run dev
```

### 停止服务

```bash
# Linux / macOS / Git Bash
bash stop.sh

# Windows
stop.bat
```

### Makefile（需要安装 make）

```bash
make help      # 查看所有命令
make build     # 构建后端 + 前端
make start     # 启动本地开发
make stop      # 优雅停止
make restart   # 重启
make clean     # 清理构建产物
make package   # 打包为 tar.gz
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

| 文档 | 内容 |
|------|------|
| `02-zify-v01-modules.md` | 一期功能模块定义、功能范围、边界 |
| `03-zify-v01-frontend-view.md` | 前端页面结构、导航、用户路径 |
| `04-zify-tech-stack.md` | 技术选型及理由 |
| `05-zify-app-architecture.md` | 应用架构选型（Maven 多模块） |
| `06-zify-code-organization.md` | 后端和前端代码组织规范 |
| `07-zify-LLM-api-calling.md` | LLM 调用规范（超时、重试、熔断） |
| `08-zify-deployment-architecture.md` | 部署架构（K8s、Nginx、备份） |
| `09-zify-performance-bottleneck.md` | 性能瓶颈预判和应对策略 |
| `10-zify-database-spec.md` | 数据库规范（索引、分页、建表模板） |
| `11-zify-core-data-model.md` | 核心数据模型（21 张表和关系） |

## License

Private — 内部使用
