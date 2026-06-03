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

### 方案 B：模块化单体（✅ 选用）

按功能模块分包，每个模块内部再分四层。模块间通过 Facade 接口调用，禁止直接引用其他模块的 Service / Repository / Entity。

```
com.zify/
├── agent/
│   ├── api/              # 对外暴露的接口和 DTO
│   ├── domain/           # 核心业务逻辑
│   ├── infrastructure/   # 数据访问、Facade 实现
│   └── adapter/          # Controller
├── chat/
├── engine/
├── workflow/
├── knowledge/
├── tool/
├── trigger/
├── model/
└── common/               # 公共基础设施
```

- **优点**：边界清晰，Facade 接口为将来拆分留好接缝，能降低迁移成本
- **缺点**：比方案 A 多写一层 Facade 接口

### 方案 C：Maven 多模块

每个业务模块拆成独立 Maven module。

```
zify/
├── zify-agent/
├── zify-chat/
├── zify-workflow/
├── ...
├── zify-common/
├── zify-app/          # 组装启动
└── pom.xml
```

- **优点**：编译级隔离，边界最强
- **缺点**：一个人维护 8-9 个 module，改一个字段要跨多个 module 同步，一期过度设计

---

## 方案对比

| | 传统分包 | 模块化单体（✅） | Maven 多模块 |
|--|--|--|--|
| 开发速度 | ⭐⭐⭐ | ⭐⭐ | ⭐ |
| 边界清晰度 | ❌ | ✅ 接口级隔离 | ✅ 编译级隔离 |
| 将来拆分 | ❌ 很难 | ✅ Facade 作为稳定契约，降低拆分成本 | ✅ 天然独立 |
| 一个人维护 | ✅ | ✅ | ❌ 繁琐 |
| 适合阶段 | 原型验证 | 一期 | 二期/团队多人 |

---

## 选型结论

**方案 B：模块化单体。**

一期采用 **单 Spring Boot 应用、单 Maven 工程、按业务能力划分逻辑模块** 的模块化单体。不提前拆微服务，也不提前拆成 8-9 个 Maven module。

核心规则：**模块间调用只走 Facade / API 契约，禁止直接引用其他模块的 Service / Repository / Entity。**

Facade 的定位是稳定模块契约，不是“未来零成本微服务迁移”的保证。将来如果拆成远程服务，仍然需要处理超时、重试、幂等、鉴权、事务边界、链路追踪和接口版本兼容等问题；但只要一期就把模块 API 边界守住，拆分时的改动会集中在接口实现和调用适配层，而不是散落到各业务模块内部。

边界约束方式保持轻量：一期不引入 Maven 多模块作为强隔离，优先用约定和少量自动化检查保障边界，例如 ArchUnit 或 Spring Modulith 的模块依赖校验。这样既不增加单人开发维护负担，也能避免后期模块依赖失控。

未来演进路径：

1. 一期：单体应用 + 逻辑模块边界。
2. 团队变多或模块边界需要强约束时：按需拆成 Maven 多模块。
3. 某些模块出现独立部署、独立扩缩容或独立团队维护需求时：再拆成微服务。

代码组织规范（模块内部分层细节、跨模块依赖方向、命名约定等）后续单独设计。
