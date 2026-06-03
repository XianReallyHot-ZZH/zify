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

- **优点**：边界清晰，Facade 接口为将来拆微服务留好接缝（改实现不改接口）
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
| 将来拆分 | ❌ 很难 | ✅ Facade 改远程调用即可 | ✅ 天然独立 |
| 一个人维护 | ✅ | ✅ | ❌ 繁琐 |
| 适合阶段 | 原型验证 | 一期 | 二期/团队多人 |

---

## 选型结论

**方案 B：模块化单体。**

核心规则就一条：**模块间调用只走 Facade 接口，禁止直接引用其他模块的 Service / Repository / Entity。** 将来拆微服务时，只需把 Facade 实现从本地调用改为远程调用，其他模块代码一行不改。

代码组织规范（模块内部分层细节、跨模块依赖方向、命名约定等）后续单独设计。
