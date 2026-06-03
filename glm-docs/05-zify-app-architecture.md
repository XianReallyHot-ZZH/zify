# Zify 应用架构选型

> 约束：一个人开发，一期 50 人使用，后续可能扩到几千人。
> 原则：不过度设计，模块边界清晰，为将来拆分留好接缝。

---

## 备选方案

### 方案 A：传统分包

按功能模块分包，每个包内 Controller / Service / Repository 平铺。

```
com.zify/
├── agent/
│   ├── AgentController.java
│   ├── AgentService.java
│   ├── AgentRepository.java
│   └── AgentEntity.java
├── chat/
├── workflow/
├── knowledge/
├── tool/
├── trigger/
├── model/
└── common/
```

- **优点**：最简单，上手最快
- **缺点**：模块间无边界，ChatService 可以直接注入 AgentRepository，时间一长依赖全乱，将来拆不了

### 方案 B：Maven 多模块 + 模块内分层（✅ 选用）

每个业务模块拆成独立 Maven 子模块，模块内部再分四层（api / domain / infrastructure / adapter）。模块间通过 Facade 接口调用，由 Maven 编译时依赖强制边界。

```
zify/                                  父 POM (packaging=pom)
├── pom.xml                            <modules> + <dependencyManagement>
├── zify-common/                       基础设施：配置、异常、统一响应、工具类
├── zify-model/                        Provider、API Key、LLM/Embedding 调用
├── zify-tool/                         MCP / HTTP / Workflow-as-Tool
├── zify-knowledge/                    文档、解析、分块、Embedding、pgvector
├── zify-workflow/                     工作流编辑、节点执行、变量传递
├── zify-agent/                        Agent CRUD、Prompt、绑定
├── zify-engine/                       Agent 执行、ReAct、流式响应
├── zify-chat/                         会话和消息持久化
├── zify-trigger/                      Webhook、Cron、触发日志
├── zify-app/                          Spring Boot 启动模块（聚合所有后端子模块）
└── zify-web/                          前端（React + Vite，独立构建）
```

每个子模块内部结构（以 `zify-agent` 为例）：

```
zify-agent/
├── pom.xml
└── src/main/java/com/zify/agent/
    ├── api/              # 对外暴露的 Facade 接口和 DTO
    ├── domain/           # 核心业务逻辑
    ├── infrastructure/   # 数据访问、Facade 实现
    └── adapter/          # Controller
```

- **优点**：编译级隔离，边界最强。Facade 接口为将来拆分留好接缝。Maven 反应堆自动处理构建顺序。
- **缺点**：一个人维护 11 个子模块，改字段要跨模块同步 DTO。但 Facade 接口约束已在单模块阶段验证过，迁移成本可控。

### 方案 C：微服务拆分（❌ 一期不用）

每个业务模块独立部署，独立数据库，通过 HTTP/gRPC 通信。

- **优点**：完全独立，可独立扩缩容
- **缺点**：运维复杂度指数级增长，不适合一期单人开发

---

## 方案对比

| | 传统分包 | Maven 多模块（✅） | 微服务拆分 |
|--|--|--|--|
| 开发速度 | ⭐⭐⭐ | ⭐⭐ | ⭐ |
| 边界清晰度 | ❌ | ✅ 编译级隔离 | ✅ 进程级隔离 |
| 将来拆分 | ❌ 很难 | ✅ Facade 作为稳定契约 | ✅ 天然独立 |
| 一个人维护 | ✅ | ✅ 子模块数可控 | ❌ |
| 适合阶段 | 原型验证 | 一期/二期 | 团队多人 + 独立部署需求 |

---

## 选型结论

**方案 B：Maven 多模块 + 模块内分层。**

一期采用 **单 Spring Boot 应用、Maven 多模块工程**。每个业务模块是一个 Maven 子模块（`zify-{module}`），由 `zify-app` 启动模块聚合为单个可部署 JAR。不提前拆微服务。

核心规则：

1. **模块间调用只走 Facade / API 契约**，禁止直接引用其他模块的 Service / Repository / Entity。
2. **Maven `<dependency>` 声明必须与允许的依赖图完全匹配**，编译时强制边界。未声明的模块的类不可访问。
3. **所有子模块都依赖 `zify-common`**，版本由父 POM `<dependencyManagement>` 统一管理。

Facade 的定位是稳定模块契约，不是"未来零成本微服务迁移"的保证。将来如果拆成远程服务，仍然需要处理超时、重试、幂等、鉴权、事务边界、链路追踪和接口版本兼容等问题；但只要一期就把模块 API 边界守住，拆分时的改动会集中在接口实现和调用适配层，而不是散落到各业务模块内部。

未来演进路径：

1. 一期：Maven 多模块单体应用，`zify-app` 打包为单个 Spring Boot fat JAR。
2. 某些模块出现独立部署、独立扩缩容或独立团队维护需求时：再拆成微服务。

代码组织规范（模块内部分层细节、跨模块依赖方向、命名约定等）见 `06-zify-code-organization.md`。
