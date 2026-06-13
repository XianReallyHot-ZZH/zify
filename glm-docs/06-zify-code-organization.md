# Zify 代码组织规范

> 本文定义后端和前端的代码组织规范。
> 后端基于模块化单体架构，使用 Spring Boot + MyBatis-Plus。
> 前端基于 React 18 + TypeScript，使用 React Flow 作为工作流画布。
> 目标是让 AI 生成代码时可以直接按规则落文件、写依赖、做跨模块调用。

---

## 一、全局原则

- Zify 后端一期采用单 Spring Boot 应用、**Maven 多模块工程**。
- 每个业务模块是独立的 Maven 子模块（`zify-{module}`），位于项目根目录下。
- 启动模块 `zify-app` 聚合所有子模块依赖，包含 `@SpringBootApplication` 主类和配置文件。
- 模块名使用小写英文：`agent`、`chat`、`engine`、`workflow`、`knowledge`、`tool`、`trigger`、`model`、`common`。
- `common` 是独立 Maven 子模块 `zify-common`，只放全局基础设施和通用工具，所有子模块都依赖它。
- 任何跨模块调用都必须走被调用模块的 `api` 层 Facade。
- **Maven `<dependency>` 声明必须与”模块间依赖关系”完全匹配**，编译时强制边界。
- 未在本文”模块间依赖关系”中列出的跨模块依赖，一律禁止。确实需要新增依赖时，先更新本文档，再写代码。

---

## 二、模块清单

```text
zify/                              父 POM (packaging=pom)
├── pom.xml                        <modules> + <dependencyManagement>
├── zify-common/                   # 公共基础设施：配置、异常、统一响应、通用工具
├── zify-model/                    # 模型管理：Provider 配置、模型连通性测试
├── zify-tool/                     # 统一工具系统：MCP / HTTP / Workflow-as-Tool
├── zify-knowledge/                # 知识库 RAG：文档上传、解析、分块、向量化、检索
├── zify-workflow/                 # 工作流引擎：画布编排、节点执行、变量传递、运行日志
├── zify-agent/                    # Agent 管理：创建/编辑 Agent，配置 Prompt、模型、工具、知识库、工作流
├── zify-engine/                   # Agent 对话引擎：ReAct 循环、流式响应、工具调用编排
├── zify-chat/                     # 对话管理：会话列表、消息流、新建/删除会话
├── zify-trigger/                  # 触发器：Webhook 接收、Cron 调度、触发记录
├── zify-app/                      # Spring Boot 启动模块（聚合所有后端子模块）
└── zify-web/                      # 前端（React + Vite，独立构建）
```

---

## 三、业务模块目录结构

每个业务模块统一使用以下结构（以 `zify-agent` 为例）：

```text
zify-agent/
├── pom.xml
└── src/main/java/com/zify/agent/
    ├── api/
    │   ├── {Module}Facade.java
    │   └── dto/
    │       ├── XxxDTO.java
    │       ├── XxxCommand.java
    │       ├── XxxQuery.java
    │       └── XxxResult.java
    ├── domain/
    │   ├── {Module}Service.java
    │   ├── executor/
    │   ├── handler/
    │   └── validator/
    ├── infrastructure/
    │   ├── entity/
    │   │   └── XxxEntity.java
    │   ├── mapper/
    │   │   └── XxxMapper.java
    │   ├── repository/        # 可选：复杂查询或多 Mapper 编排时才建
    │   ├── converter/
    │   │   └── XxxConverter.java
    │   ├── facade/
    │   │   └── {Module}FacadeImpl.java
    │   └── client/
    │       └── XxxClient.java
    └── adapter/
        ├── web/
        │   ├── {Module}Controller.java
        │   ├── request/
        │   │   └── XxxRequest.java
        │   └── response/
        │       └── XxxResponse.java
        └── sse/               # 只有需要 SSE/流式接口的模块才建
            └── XxxSseController.java
```

规则：

- `{Module}` 使用首字母大写的模块名，例如 `AgentFacade`、`WorkflowService`。
- `api/dto` 只放跨模块 Facade 使用的数据结构。
- `adapter/web/request` 和 `adapter/web/response` 只放 HTTP 接口请求/响应对象。
- 不允许把 HTTP `Request` / `Response` 放进 `api/dto`。
- 不允许把 Entity、Mapper、Repository、Controller 放进 `api` 层。

---

## 四、各层职责

### 4.1 api 层

**包含**

- `{Module}Facade.java`
- `api/dto/*.java`

**职责**

- 定义本模块对其他模块暴露的能力。
- 定义跨模块调用时使用的 DTO、Command、Query、Result。

**命名**

- Facade 接口：`{Module}Facade.java`
- 数据对象：`XxxDTO.java`
- 命令对象：`XxxCommand.java`
- 查询对象：`XxxQuery.java`
- 结果对象：`XxxResult.java`

**硬性规则**

- Facade 只能是接口，不写业务逻辑。
- Facade 方法参数和返回值只能使用 Java 基础类型、`common` 中的通用类型、当前模块 `api/dto` 中的类型。
- `api/dto` 不允许 import 当前模块或其他模块的 `Entity`、`Mapper`、`Repository`、`Service`、`Controller`。
- `api/dto` 不允许写 `from(Entity)`、`toEntity()` 这类依赖 Entity 的转换方法。
- Entity 与 DTO 的转换统一放在当前模块 `infrastructure/converter/` 下。
- `api/dto` 不使用 `Request` / `Response` 命名，避免和 HTTP 对象混淆。

### 4.2 domain 层

**包含**

- Service：业务用例入口，例如 `AgentService`
- Executor：执行类，例如 `WorkflowExecutor`
- Handler：策略处理器，例如 `LlmNodeHandler`
- Validator：业务校验器，例如 `AgentConfigValidator`

**职责**

- 实现本模块业务逻辑。
- 处理事务边界。
- 负责编排本模块 Mapper/Repository/Client。
- 按允许的模块依赖关系调用其他模块 Facade。

**硬性规则**

- 可以注入本模块 `infrastructure/mapper` 下的 Mapper。
- 可以注入本模块 `infrastructure/repository` 下的 Repository。
- 可以使用本模块 `infrastructure/entity` 下的 Entity。
- 可以使用本模块 `infrastructure/converter` 下的 Converter。
- 可以注入“模块间依赖关系”允许的其他模块 Facade。
- 禁止注入其他模块的 Service、Mapper、Repository、Entity、Controller。
- 禁止使用 HTTP Request/Response 对象。
- 写操作的事务放在 Service 方法上，例如 `@Transactional` 标注在 `createAgent()`、`updateWorkflow()` 这类 public 方法上。
- Controller 不承载业务逻辑；跨模块编排必须写在 Service 中。

示例：

```java
// 正确：同模块 Mapper + 跨模块 Facade
@Service
@RequiredArgsConstructor
public class ChatService {
    private final ChatMapper chatMapper;
    private final AgentFacade agentFacade;
    private final EngineFacade engineFacade;
}

// 错误：跨模块直接依赖 Service / Mapper / Entity
@Service
@RequiredArgsConstructor
public class ChatService {
    private final AgentService agentService;
    private final AgentMapper agentMapper;
    private final AgentEntity agentEntity;
}
```

### 4.3 infrastructure 层

**包含**

- `entity/`：数据库表映射 Entity
- `mapper/`：MyBatis-Plus Mapper
- `repository/`：可选，复杂查询或多 Mapper 编排
- `converter/`：Entity、DTO、HTTP 对象之间的转换
- `facade/`：Facade 接口实现
- `client/`：外部服务调用客户端，例如 LLM Provider、MCP Server、HTTP 工具

**职责**

- 数据库访问。
- 外部系统访问。
- Entity 与 DTO / Response 的转换。
- 实现本模块 Facade，并把调用转发给本模块 Service。

**硬性规则**

- Entity 统一命名为 `XxxEntity`，放在 `infrastructure/entity/`。
- Mapper 统一命名为 `XxxMapper`，放在 `infrastructure/mapper/`，继承 MyBatis-Plus `BaseMapper<XxxEntity>`。
- XML Mapper 放在 `zify-{module}/src/main/resources/mapper/XxxMapper.xml`。
- 一期默认只创建 Mapper，不创建 Repository。
- 只有当一个查询需要组合多个 Mapper 或包含复杂查询语义时，才创建 `infrastructure/repository/XxxRepository.java`。
- Facade 实现统一放在 `infrastructure/facade/`，命名为 `{Module}FacadeImpl`。
- Facade 实现只做参数转发和 DTO 转换，不写核心业务逻辑。
- Mapper、Repository、Entity 不允许被其他模块引用。
- 外部服务 Client 不允许被其他模块直接引用；其他模块需要能力时调用当前模块 Facade。

示例：

```java
@Mapper
public interface AgentMapper extends BaseMapper<AgentEntity> {
}

@Service
@RequiredArgsConstructor
public class AgentFacadeImpl implements AgentFacade {
    private final AgentService agentService;

    @Override
    public AgentDTO getById(String id) {
        return agentService.getById(id);
    }
}
```

### 4.4 adapter 层

**包含**

- `adapter/web/*Controller.java`
- `adapter/web/request/*Request.java`
- `adapter/web/response/*Response.java`
- `adapter/sse/*SseController.java`

**职责**

- 接收 HTTP / SSE 请求。
- 做参数校验，例如 `@Valid`。
- 调用本模块 Service。
- 返回统一响应体。

**硬性规则**

- Controller 只能注入本模块 domain 层 Service。
- Controller 禁止注入任何 Mapper、Repository、Entity。
- Controller 禁止注入其他模块 Facade。
- Controller 禁止调用其他模块 Service。
- HTTP Request / Response 只能在 adapter 层使用，不能传入 domain 层。
- Controller 中不写业务判断、业务计算、跨模块编排。
- Controller 方法超过 30 行时，必须把逻辑下沉到 Service 或 Converter。

示例：

```java
// 正确
@RestController
@RequestMapping("/api/agents")
@RequiredArgsConstructor
public class AgentController {
    private final AgentService agentService;
}

// 错误
@RestController
@RequiredArgsConstructor
public class AgentController {
    private final AgentMapper agentMapper;
    private final ModelFacade modelFacade;
}
```

---

## 五、层间依赖方向

### 5.1 本模块内依赖

| 来源层 | 允许依赖 |
|----|----|
| api | `common`、Java 标准库 |
| adapter | 本模块 `domain`、本模块 `api/dto`、`common` |
| domain | 本模块 `api`、本模块 `infrastructure/mapper`、本模块 `infrastructure/repository`、本模块 `infrastructure/entity`、本模块 `infrastructure/converter`、允许依赖模块的 Facade、`common` |
| infrastructure/entity | `common`、Java 标准库、MyBatis-Plus 注解 |
| infrastructure/mapper | 本模块 `infrastructure/entity`、MyBatis-Plus |
| infrastructure/repository | 本模块 `mapper`、本模块 `entity`、`common` |
| infrastructure/converter | 本模块 `entity`、本模块 `api/dto`、本模块 `adapter/web/request`、本模块 `adapter/web/response` |
| infrastructure/facade | 本模块 `api`、本模块 `domain`、本模块 `infrastructure/converter`、`common` |
| infrastructure/client | `common`、外部 SDK、Java 标准库 |

### 5.2 跨模块依赖

跨模块只允许一种形式：

```text
当前模块 domain Service -> 目标模块 api Facade
```

禁止以下形式：

```text
当前模块 Controller -> 目标模块 Facade
当前模块 Service    -> 目标模块 Service
当前模块 Service    -> 目标模块 Mapper / Repository / Entity
当前模块 Mapper     -> 目标模块 Mapper
当前模块 Entity     -> 目标模块 Entity
当前模块 common     -> 任意业务模块
```

---

## 六、跨模块调用规则

### 规则 1：只注入目标模块 Facade

```java
// 正确
@Service
@RequiredArgsConstructor
public class EngineService {
    private final AgentFacade agentFacade;
    private final ModelFacade modelFacade;
    private final ToolFacade toolFacade;
}

// 错误
@Service
@RequiredArgsConstructor
public class EngineService {
    private final AgentService agentService;
    private final ModelMapper modelMapper;
    private final ToolEntity toolEntity;
}
```

### 规则 2：Entity 不跨模块

```java
// 正确
public interface AgentFacade {
    AgentDTO getById(String id);
}

// 错误
public interface AgentFacade {
    AgentEntity getById(String id);
}
```

### 规则 3：HTTP 对象不跨层

```java
// 正确：Controller 接收 HTTP Request，转换后调用 Service
public R<AgentDetailResponse> create(@Valid @RequestBody CreateAgentRequest request) {
    CreateAgentCommand command = AgentConverter.toCommand(request);
    AgentDTO agent = agentService.create(command);
    return R.ok(AgentConverter.toResponse(agent));
}

// 错误：Service 直接接收 HTTP Request
public AgentDTO create(CreateAgentRequest request) {
    // forbidden
}
```

### 规则 4：Facade 接口保持稳定

- 新增能力：新增 Facade 方法。
- 修改能力：先新增新方法，旧方法标记 `@Deprecated`，迁移完成后再删除。
- 删除能力：确认没有调用方后再删除。
- Facade 方法不返回 HTTP Response，不返回 Entity，不返回 MyBatis-Plus 分页对象。

---

## 七、模块间依赖关系

允许的依赖关系如下（每个子模块还隐式依赖 `zify-common`，表中省略）：

```text
model     -> 无
tool      -> 无
knowledge -> model
workflow  -> model, knowledge, tool
agent     -> model, tool, knowledge, workflow
engine    -> agent, model, tool, knowledge, workflow
chat      -> agent, engine
trigger   -> workflow
```

以上逻辑依赖通过各子模块 `pom.xml` 中的 `<dependency>` 声明在编译时强制执行。未声明的模块的类不可访问。

说明：

- `model` 是基础配置模块，不依赖其他业务模块。
- `tool` 只管理工具定义、MCP 连接、HTTP 工具配置和工具调用入口描述，不依赖 `workflow`。
- Workflow-as-Tool 在 `tool` 模块中只表现为一种工具定义，包含 `workflowId` 等元数据。
- Workflow-as-Tool 的实际执行由调用方处理：
  - Agent ReAct 调用 Workflow-as-Tool 时，由 `engine` 调用 `workflow`。
  - 工作流 Tool 节点调用 Workflow-as-Tool 时，由 `workflow` 在本模块内启动目标工作流。
- `workflow` 可以调用 `tool` 执行普通 Tool 节点。
- `agent` 可以调用 `model`、`tool`、`knowledge`、`workflow` 校验绑定对象是否存在，但不执行这些模块的运行逻辑。
- `chat` 只负责会话与消息管理，Agent 执行统一交给 `engine`。
- `trigger` 只触发工作流，不直接触发 Agent 对话。

禁止循环依赖。如果新增功能导致 A 依赖 B、B 又依赖 A，必须先重新划分职责，不能通过把业务类移动到 `common` 来绕过循环。

---

## 八、common 模块

`zify-common` 是独立 Maven 子模块，不按四层结构拆分，只按基础设施能力分包：

```text
zify-common/
├── pom.xml                    (无业务依赖)
└── src/main/java/com/zify/common/
    ├── config/
    │   ├── DataSourceConfig.java
    │   ├── RedisConfig.java
    │   ├── WebConfig.java
    │   └── MyBatisPlusConfig.java
    ├── exception/
    │   ├── BusinessException.java
    │   ├── ErrorCode.java
    │   └── GlobalExceptionHandler.java
    ├── web/
    │   ├── R.java
    │   └── PageResult.java
    └── util/
        └── JsonUtils.java
```

规则：

- `common` 不允许依赖任何业务模块。
- `common` 中不允许出现 Agent、Workflow、Knowledge、Tool、Model、Chat、Trigger 等业务概念。
- `common` 只放三个及以上模块都会使用的基础设施类。
- 只被一个模块使用的工具类放在该模块 `infrastructure/` 下。
- 只被两个模块使用的业务对象不能直接放进 `common`，优先通过 Facade DTO 传递，或重新检查模块边界。

---

## 九、文件放置速查表

| 要写的类 | 放置位置 |
|---|---|
| Facade 接口 | `zify-{module}/src/main/java/com/zify/{module}/api/{Module}Facade.java` |
| 跨模块 DTO | `zify-{module}/src/main/java/com/zify/{module}/api/dto/XxxDTO.java` |
| 跨模块命令对象 | `zify-{module}/src/main/java/com/zify/{module}/api/dto/XxxCommand.java` |
| 跨模块查询对象 | `zify-{module}/src/main/java/com/zify/{module}/api/dto/XxxQuery.java` |
| 跨模块结果对象 | `zify-{module}/src/main/java/com/zify/{module}/api/dto/XxxResult.java` |
| Service | `zify-{module}/src/main/java/com/zify/{module}/domain/XxxService.java` |
| Executor | `zify-{module}/src/main/java/com/zify/{module}/domain/executor/XxxExecutor.java` |
| Handler | `zify-{module}/src/main/java/com/zify/{module}/domain/handler/XxxHandler.java` |
| Validator | `zify-{module}/src/main/java/com/zify/{module}/domain/validator/XxxValidator.java` |
| Entity | `zify-{module}/src/main/java/com/zify/{module}/infrastructure/entity/XxxEntity.java` |
| MyBatis-Plus Mapper | `zify-{module}/src/main/java/com/zify/{module}/infrastructure/mapper/XxxMapper.java` |
| 可选 Repository | `zify-{module}/src/main/java/com/zify/{module}/infrastructure/repository/XxxRepository.java` |
| Converter | `zify-{module}/src/main/java/com/zify/{module}/infrastructure/converter/XxxConverter.java` |
| Facade 实现 | `zify-{module}/src/main/java/com/zify/{module}/infrastructure/facade/{Module}FacadeImpl.java` |
| 外部 API Client | `zify-{module}/src/main/java/com/zify/{module}/infrastructure/client/XxxClient.java` |
| Controller | `zify-{module}/src/main/java/com/zify/{module}/adapter/web/{Module}Controller.java` |
| SSE Controller | `zify-{module}/src/main/java/com/zify/{module}/adapter/sse/XxxSseController.java` |
| HTTP Request | `zify-{module}/src/main/java/com/zify/{module}/adapter/web/request/XxxRequest.java` |
| HTTP Response | `zify-{module}/src/main/java/com/zify/{module}/adapter/web/response/XxxResponse.java` |
| XML Mapper | `zify-{module}/src/main/resources/mapper/XxxMapper.xml` |
| 全局配置 | `zify-common/src/main/java/com/zify/common/config/` |
| 全局异常 | `zify-common/src/main/java/com/zify/common/exception/` |
| 统一响应体 / 分页 | `zify-common/src/main/java/com/zify/common/web/` |
| 全局工具类 | `zify-common/src/main/java/com/zify/common/util/` |

---

## 十、AI 生成代码执行顺序

AI 新增一个功能时，按以下顺序执行：

1. 判断功能属于哪个业务模块。
2. 如果需要调用其他模块，先检查”模块间依赖关系”是否允许。
3. **验证当前子模块的 `pom.xml` 是否已声明对目标模块的 Maven 依赖**；未声明则先添加。
4. 若允许跨模块调用，只在当前模块 Service 中注入目标模块 Facade。
5. 先创建或复用当前模块 `api/dto` 中的 Command / Query / DTO / Result。
6. 创建 Entity 和 Mapper。
7. 创建 Converter。
8. 创建 Service，并在 Service 中写业务逻辑和事务。
9. 创建 FacadeImpl，把 Facade 调用转发到 Service。
10. 创建 Controller、HTTP Request、HTTP Response。
11. 确认没有跨模块引用 Service / Mapper / Repository / Entity / Controller。
12. 确认当前子模块 `pom.xml` 的 `<dependency>` 声明与依赖图完全匹配。

---

## 十一、前端代码组织规范

### 11.1 技术栈

| 层 | 技术 |
|----|------|
| 框架 | React 18 + TypeScript |
| 路由 | React Router |
| 工作流画布 | React Flow |
| 状态管理 | Zustand |
| HTTP 客户端 | Axios |
| UI 组件库 | Ant Design |
| 构建工具 | Vite |

硬性规则：

- 使用 Vite + React Router，不使用 Next.js App Router 目录约定。
- 路由统一写在 `src/app/router.tsx`，文件路径不自动生成路由。
- 路由动态参数使用 React Router 语法，例如 `/agents/:id/edit`，不使用 `[id]` 目录。
- 页面文件命名为 `XxxPage.tsx`，不使用 `page.tsx`。

### 11.2 项目结构

```text
src/
├── app/                          # 应用入口、路由、Provider、布局
│   ├── App.tsx
│   ├── router.tsx
│   ├── providers.tsx
│   └── layouts/
│       ├── MainLayout.tsx        # 左侧导航 + 内容区
│       └── MainLayout.module.css
│
├── pages/                        # 路由页面，只放页面入口和页面私有组件
│   ├── chat/
│   │   ├── ChatPage.tsx          # 路由：/
│   │   ├── components/
│   │   │   ├── ConversationSidebar.tsx
│   │   │   ├── ChatPanel.tsx
│   │   │   ├── MessageList.tsx
│   │   │   ├── MessageInput.tsx
│   │   │   └── ToolCallTrace.tsx
│   │   └── hooks/
│   │       └── useChatPage.ts
│   ├── agents/
│   │   ├── AgentListPage.tsx     # 路由：/agents
│   │   ├── AgentFormPage.tsx     # 路由：/agents/create、/agents/:id/edit
│   │   └── components/
│   │       └── AgentCard.tsx
│   ├── workflows/
│   │   ├── WorkflowListPage.tsx  # 路由：/workflows
│   │   ├── WorkflowEditorPage.tsx # 路由：/workflows/:id
│   │   └── components/
│   │       └── WorkflowCard.tsx
│   ├── knowledge/
│   │   ├── KnowledgeListPage.tsx # 路由：/knowledge
│   │   ├── KnowledgeDetailPage.tsx # 路由：/knowledge/:id
│   │   └── components/
│   │       ├── KnowledgeCard.tsx
│   │       └── CreateKnowledgeModal.tsx
│   ├── tools/
│   │   ├── ToolListPage.tsx      # 路由：/tools
│   │   ├── ToolFormPage.tsx      # 路由：/tools/create、/tools/:id/edit
│   │   └── components/
│   │       └── ToolCard.tsx
│   └── models/
│       └── ModelPage.tsx         # 路由：/models
│
├── features/                     # 可复用业务组件、业务 Hook、复杂业务 UI
│   ├── agent/
│   │   ├── components/
│   │   │   ├── AgentSelector.tsx
│   │   │   ├── AgentForm.tsx
│   │   │   ├── AgentTypeSelector.tsx
│   │   │   └── PromptEditor.tsx
│   │   └── hooks/
│   │       └── useAgentOptions.ts
│   ├── model/
│   │   ├── components/
│   │   │   ├── ModelSelector.tsx
│   │   │   ├── ProviderCard.tsx
│   │   │   ├── ProviderFormModal.tsx
│   │   │   ├── ModelList.tsx
│   │   │   └── ModelTestModal.tsx
│   │   └── hooks/
│   │       └── useModelOptions.ts
│   ├── tool/
│   │   ├── components/
│   │   │   ├── ToolSelector.tsx
│   │   │   ├── ToolBinder.tsx
│   │   │   ├── HttpToolForm.tsx
│   │   │   └── McpServerForm.tsx
│   │   └── hooks/
│   │       └── useToolOptions.ts
│   ├── knowledge/
│   │   ├── components/
│   │   │   ├── KnowledgeSelector.tsx
│   │   │   ├── KnowledgeBinder.tsx
│   │   │   ├── DocumentUploader.tsx
│   │   │   ├── DocumentList.tsx
│   │   │   ├── ChunkList.tsx
│   │   │   └── HitTestPanel.tsx
│   │   └── hooks/
│   │       └── useKnowledgeOptions.ts
│   ├── workflow/
│   │   ├── components/
│   │   │   ├── WorkflowSelector.tsx
│   │   │   ├── WorkflowCanvas.tsx
│   │   │   ├── NodePanel.tsx
│   │   │   ├── NodeConfigPanel.tsx
│   │   │   ├── RunLogPanel.tsx
│   │   │   ├── TriggerConfigModal.tsx
│   │   │   └── nodes/
│   │   │       ├── StartNode.tsx
│   │   │       ├── EndNode.tsx
│   │   │       ├── LlmNode.tsx
│   │   │       ├── IfElseNode.tsx
│   │   │       ├── HttpRequestNode.tsx
│   │   │       ├── CodeNode.tsx
│   │   │       ├── KnowledgeRetrievalNode.tsx
│   │   │       ├── ToolNode.tsx
│   │   │       └── AnswerNode.tsx
│   │   └── hooks/
│   │       └── useWorkflowCanvas.ts
│   └── chat/
│       └── hooks/
│           └── useChatStream.ts
│
├── api/                          # HTTP API 调用封装，一个文件对应一个后端 HTTP 模块
│   ├── request.ts
│   ├── agentApi.ts
│   ├── chatApi.ts
│   ├── engineApi.ts
│   ├── workflowApi.ts
│   ├── knowledgeApi.ts
│   ├── toolApi.ts
│   ├── triggerApi.ts
│   └── modelApi.ts
│
├── stores/                       # Zustand 全局状态，只放跨组件共享的客户端状态
│   ├── appStore.ts
│   ├── chatStore.ts
│   └── workflowStore.ts
│
├── types/                        # 前端使用的 HTTP 契约类型和视图类型
│   ├── api.ts
│   ├── agent.ts
│   ├── chat.ts
│   ├── engine.ts
│   ├── workflow.ts
│   ├── knowledge.ts
│   ├── tool.ts
│   ├── trigger.ts
│   └── model.ts
│
├── shared/                       # 无业务含义的共享能力
│   ├── ui/
│   │   ├── Loading.tsx
│   │   ├── EmptyState.tsx
│   │   └── ErrorBoundary.tsx
│   ├── hooks/
│   │   ├── useConfirm.ts
│   │   └── useCursorPagination.ts
│   └── utils/
│       ├── format.ts
│       ├── constants.ts
│       └── queryString.ts
│
├── styles/
│   └── globals.css
└── main.tsx
```

### 11.3 路由表

`src/app/router.tsx` 必须显式声明以下路由：

| 路由 | 页面组件 | 说明 |
|---|---|---|
| `/` | `pages/chat/ChatPage.tsx` | 默认落地页，对话页 |
| `/agents` | `pages/agents/AgentListPage.tsx` | Agent 列表 |
| `/agents/create` | `pages/agents/AgentFormPage.tsx` | 创建 Agent |
| `/agents/:id/edit` | `pages/agents/AgentFormPage.tsx` | 编辑 Agent |
| `/workflows` | `pages/workflows/WorkflowListPage.tsx` | 工作流列表 |
| `/workflows/:id` | `pages/workflows/WorkflowEditorPage.tsx` | 工作流画布编辑 |
| `/knowledge` | `pages/knowledge/KnowledgeListPage.tsx` | 知识库列表 |
| `/knowledge/:id` | `pages/knowledge/KnowledgeDetailPage.tsx` | 知识库详情 |
| `/tools` | `pages/tools/ToolListPage.tsx` | 工具列表 |
| `/tools/create` | `pages/tools/ToolFormPage.tsx` | 创建 HTTP 工具或 MCP 连接，通过 `?type=http` / `?type=mcp` 区分 |
| `/tools/:id/edit` | `pages/tools/ToolFormPage.tsx` | 编辑工具配置 |
| `/models` | `pages/models/ModelPage.tsx` | 模型管理 |

规则：

- 所有业务页面必须挂在 `MainLayout` 下。
- 工作流编辑页允许隐藏或收起左侧导航，但仍由 `MainLayout` 控制。
- 一期没有独立触发器导航；触发器配置放在工作流编辑页的 `TriggerConfigModal`。
- 未匹配路由统一跳转到 `/` 或显示 `shared/ui/EmptyState.tsx`。

### 11.4 各目录职责和依赖方向

| 目录 | 职责 | 允许依赖 | 禁止 |
|---|---|---|---|
| `app/` | 应用启动、路由、Provider、布局 | `pages`、`shared`、`stores` | 业务 API 调用、业务表单逻辑 |
| `pages/` | 路由页面和页面私有组件 | `features`、`api`、`types`、`stores`、`shared` | 被其他页面引用 |
| `features/` | 可复用业务组件、复杂业务 UI、业务 Hook | `api`、`types`、`stores`、`shared` | 引用 `pages` |
| `api/` | HTTP 请求函数 | `types`、`shared/utils`、`api/request.ts` | 引用 React、组件、Store |
| `stores/` | 跨组件共享的客户端状态 | `types` | 发 HTTP 请求、引用组件、互相引用 Store |
| `types/` | TypeScript 类型 | 仅 type-only import | 运行时代码、组件、API 调用 |
| `shared/ui` | 无业务 UI 组件 | Ant Design、`shared/utils` | 业务 API、Store、业务类型 |
| `shared/hooks` | 无业务 Hook | `shared/utils` | 业务 API、业务 Store |
| `shared/utils` | 纯函数、常量 | 无业务依赖 | React、API、Store |

页面私有组件、业务组件、共享组件按以下规则放置：

- 只被一个页面使用，且没有跨业务复用价值：放 `pages/{page}/components/`。
- 被多个页面使用，且带业务含义或会调用业务 API：放 `features/{domain}/components/`。
- 不带业务含义，只做展示或通用交互：放 `shared/ui/`。
- Ant Design 组件可以在 `pages` 和 `features` 中直接使用；`shared/ui` 只封装项目级通用组件，不封装每一个 Ant Design 基础组件。

### 11.5 页面与组件规则

页面组件规则：

- `XxxPage.tsx` 只做路由参数读取、页面级布局、页面级 Hook 调用。
- 页面组件可以调用页面私有 Hook，例如 `useChatPage()`。
- 页面组件不直接写 Axios 调用；数据加载写在页面 Hook 或 `features/*/hooks/` 中。
- 页面组件不直接操作 React Flow 节点细节；工作流画布逻辑放在 `features/workflow/`。

页面私有组件规则：

- 只能被同目录页面引用。
- 如果被第二个页面引用，必须移动到 `features/{domain}/components/` 或 `shared/ui/`。
- 页面私有组件可以接收业务类型，但不要定义可复用业务模型。

业务组件规则：

- `features/model/components/ModelSelector.tsx` 用于 Agent 表单和工作流 LLM 节点。
- `features/tool/components/ToolBinder.tsx` 用于 Agent 表单；`ToolSelector.tsx` 用于工作流 Tool 节点。
- `features/knowledge/components/KnowledgeBinder.tsx` 用于 Agent 表单；`KnowledgeSelector.tsx` 用于工作流 Knowledge Retrieval 节点。
- `features/workflow/components/nodes/` 必须包含 9 个一期节点组件：Start、End、LLM、If-Else、HTTP Request、Knowledge Retrieval、Code、Tool、Answer。

### 11.6 API 和类型规则

`api/` 调用的是后端 HTTP Controller 暴露的 `/api/**` 接口，不是后端模块内部的 Facade `api` 层。

类型对齐规则：

- `types/*` 中的 `XxxRequest`、`XxxResponse`、`XxxQuery` 对齐后端 `adapter/web/request` 和 `adapter/web/response`。
- 前端不要以 Facade DTO 作为 HTTP 契约来源。
- 页面展示需要的派生类型命名为 `XxxView`，只在前端使用。
- API 文件返回值必须显式标注 `Promise<...>`。
- `types/api.ts` 必须定义 `ApiResponse<T>`、`PageResponse<T>` 等通用 HTTP 类型。

`types/api.ts` 模板：

```typescript
export type ApiResponse<T> = {
  code: number;
  message: string;
  data: T;
};

export type PageResponse<T> = {
  records: T[];
  nextCursor?: string | null;
  hasMore: boolean;
};
```

`api/request.ts` 统一封装 Axios：

```typescript
import axios, { type AxiosRequestConfig } from 'axios';
import type { ApiResponse } from '@/types/api';

const http = axios.create({
  baseURL: '/api',
  timeout: 30000,
});

export async function apiGet<T>(url: string, config?: AxiosRequestConfig): Promise<T> {
  const response = await http.get<ApiResponse<T>>(url, config);
  return response.data.data;
}

export async function apiPost<T>(url: string, data?: unknown, config?: AxiosRequestConfig): Promise<T> {
  const response = await http.post<ApiResponse<T>>(url, data, config);
  return response.data.data;
}

export async function apiPut<T>(url: string, data?: unknown, config?: AxiosRequestConfig): Promise<T> {
  const response = await http.put<ApiResponse<T>>(url, data, config);
  return response.data.data;
}

export async function apiDelete<T>(url: string, config?: AxiosRequestConfig): Promise<T> {
  const response = await http.delete<ApiResponse<T>>(url, config);
  return response.data.data;
}
```

API 文件模板：

```typescript
// api/agentApi.ts
import { apiDelete, apiGet, apiPost, apiPut } from './request';
import type {
  AgentListQuery,
  AgentSummaryResponse,
  AgentDetailResponse,
  CreateAgentRequest,
  UpdateAgentRequest,
} from '@/types/agent';

export function listAgents(query: AgentListQuery): Promise<AgentSummaryResponse[]> {
  return apiGet<AgentSummaryResponse[]>('/agents', { params: query });
}

export function getAgent(id: string): Promise<AgentDetailResponse> {
  return apiGet<AgentDetailResponse>(`/agents/${id}`);
}

export function createAgent(data: CreateAgentRequest): Promise<AgentDetailResponse> {
  return apiPost<AgentDetailResponse>('/agents', data);
}

export function updateAgent(id: string, data: UpdateAgentRequest): Promise<AgentDetailResponse> {
  return apiPut<AgentDetailResponse>(`/agents/${id}`, data);
}

export function deleteAgent(id: string): Promise<void> {
  return apiDelete<void>(`/agents/${id}`);
}
```

禁止：

- 禁止在组件中直接 `axios.get()`。
- 禁止在 `api/*Api.ts` 中写 Toast、Modal、路由跳转、Store 写入。
- 禁止在 `types/*` 中 import React 组件或运行时代码。

### 11.7 SSE 流式调用

SSE 不经过 Axios，统一由 `api/chatApi.ts` 创建连接，由 `features/chat/hooks/useChatStream.ts` 处理 UI 状态和 Store 写入。

> **归属说明**：SSE 流式端点位于 `chat` 模块（依赖方向 `chat → engine`，会话与消息持久化归 `chat`），路径 `/api/chat/...`，前端用 `chatApi.ts`。`engine` 模块只提供 `EngineFacade` 供 `chat` 内部调用，一期不暴露 HTTP 端点。

发送用户消息和建立流式连接分两步（受 `EventSource` 只能 GET、不能带 body 的约束）：

1. `chatApi.sendMessage(conversationId, content)` 使用 POST 提交用户消息并落库，后端返回 `userMessageId`。
2. `chatApi.openChatStream(messageId, handlers)` 使用 `EventSource` 打开 `/api/chat/stream?messageId=...`，流式接收 ASSISTANT 回复。

SSE API 模板：

```typescript
// api/chatApi.ts（流式部分）
import { toQueryString } from '@/shared/utils/queryString';
import type { ChatStreamEvent } from '@/types/chat';

type ChatStreamHandlers = {
  onMessageDelta: (event: Extract<ChatStreamEvent, { type: 'message_delta' }>) => void;
  onToolCall: (event: Extract<ChatStreamEvent, { type: 'tool_call' }>) => void;
  onDone: (event: Extract<ChatStreamEvent, { type: 'done' }>) => void;
  onRunError: (event: Extract<ChatStreamEvent, { type: 'run_error' }>) => void;
};

export function openChatStream(messageId: string, handlers: ChatStreamHandlers): EventSource {
  const es = new EventSource(`/api/chat/stream?${toQueryString({ messageId })}`);

  es.addEventListener('message_delta', (event) => {
    handlers.onMessageDelta(JSON.parse(event.data));
  });

  es.addEventListener('tool_call', (event) => {
    handlers.onToolCall(JSON.parse(event.data));
  });

  es.addEventListener('done', (event) => {
    handlers.onDone(JSON.parse(event.data));
    es.close();
  });

  es.addEventListener('run_error', (event) => {
    handlers.onRunError(JSON.parse(event.data));
    es.close();
  });

  es.onerror = () => {
    handlers.onRunError({ type: 'run_error', message: 'SSE connection error' });
    es.close();
  };

  return es;
}
```

事件类型放在 `types/chat.ts`：

```typescript
export type ChatStreamEvent =
  | { type: 'message_delta'; conversationId: string; messageId: string; delta: string }
  | { type: 'tool_call'; toolCallId: string; toolName: string; status: 'STARTED' | 'SUCCEEDED' | 'FAILED'; input?: unknown; output?: unknown }
  | { type: 'done'; conversationId: string; messageId: string }
  | { type: 'run_error'; message: string };
```

规则：

- 输入正文不放在 SSE query string 中，query string 只传 `messageId` 这类短 ID。
- 中断按钮必须先关闭当前 `EventSource`，如后端提供取消接口，再调用取消接口。
- SSE 回调中不直接操作 DOM；状态更新放在 `useChatStream.ts` 或 `chatStore.ts` action 中。

### 11.8 Zustand 状态管理规则

只把跨组件共享的客户端状态放入 Zustand。

允许进入 Zustand 的状态：

- `appStore`：侧边栏收起状态、当前导航、当前用户占位信息。
- `chatStore`：当前会话 ID、当前消息流、流式生成状态、当前 EventSource 引用 ID。
- `workflowStore`：画布节点、连线、选中节点、画布缩放和平移、当前运行状态。

不允许进入 Zustand 的状态：

- 表单草稿，例如 Agent 创建表单、Provider 表单。
- 弹窗开关，除非跨多个远距离组件控制。
- 列表搜索条件、分页游标。
- 单个页面独占的 loading 状态。

硬性规则：

- Store 只存状态和 action，不发 HTTP 请求。
- Store 之间不互相 import；需要组合状态时在组件或 Hook 中组合。
- 组件读取 Store 必须使用 selector，例如 `useChatStore((state) => state.currentConversationId)`。
- 服务端列表数据默认放在页面 Hook 中；只有对话消息流和工作流画布这类需要多组件实时共享的数据才放 Store。

### 11.9 页面与后端模块对应关系

| 前端页面 | 后端模块 | API 文件 |
|---|---|---|
| 对话 `/` | `chat` + `engine` + `agent` | `chatApi.ts`、`agentApi.ts` |
| Agents `/agents` | `agent` + `model` + `tool` + `knowledge` + `workflow` | `agentApi.ts`、选项数据调用对应模块 API |
| 工作流 `/workflows` | `workflow` + `trigger` + `model` + `knowledge` + `tool` | `workflowApi.ts`、`triggerApi.ts`、选项数据调用对应模块 API |
| 知识库 `/knowledge` | `knowledge` | `knowledgeApi.ts` |
| 工具 `/tools` | `tool` | `toolApi.ts` |
| 模型管理 `/models` | `model` | `modelApi.ts` |

规则：

- 页面需要其他模块的下拉选项时，调用对应模块的 API 文件，不复制 API 函数。
- `triggerApi.ts` 只被工作流页面和 `features/workflow` 使用，一期不建立触发器页面。
- 一期 `engine` 模块无 HTTP 端点（`EngineFacade` 仅供 `chat` 内部调用）；流式接口与会话 CRUD 统一在 `chatApi.ts`。`engineApi.ts` 暂不使用，待后续阶段 `engine` 暴露 HTTP 运行接口时再启用。

### 11.10 命名规范

| 对象 | 规则 | 示例 |
|---|---|---|
| 页面组件 | PascalCase + `Page` 后缀 | `AgentListPage.tsx` |
| 页面私有组件 | PascalCase | `AgentCard.tsx` |
| 业务组件 | PascalCase | `ModelSelector.tsx` |
| Hook | camelCase，`use` 前缀 | `useWorkflowCanvas.ts` |
| Store | camelCase，`Store` 后缀 | `chatStore.ts` |
| API 文件 | camelCase，`Api` 后缀 | `agentApi.ts` |
| 类型文件 | camelCase，与业务域同名 | `agent.ts` |
| HTTP 请求类型 | `XxxRequest` | `CreateAgentRequest` |
| HTTP 响应类型 | `XxxResponse` | `AgentDetailResponse` |
| HTTP 查询类型 | `XxxQuery` | `AgentListQuery` |
| 前端视图类型 | `XxxView` | `MessageView` |
| CSS Module | 组件同名 + `.module.css` | `MainLayout.module.css` |
| 路由路径 | kebab-case | `/tools/create` |

### 11.11 前端文件放置速查表

| 我要写的文件 | 放哪里 |
|---|---|
| React 根组件 | `app/App.tsx` |
| 路由配置 | `app/router.tsx` |
| 主布局 | `app/layouts/MainLayout.tsx` |
| 页面入口 | `pages/{page}/XxxPage.tsx` |
| 页面私有组件 | `pages/{page}/components/Xxx.tsx` |
| 页面私有 Hook | `pages/{page}/hooks/useXxx.ts` |
| 跨页面业务组件 | `features/{domain}/components/Xxx.tsx` |
| 复杂业务 Hook | `features/{domain}/hooks/useXxx.ts` |
| 工作流节点组件 | `features/workflow/components/nodes/XxxNode.tsx` |
| 无业务共享 UI | `shared/ui/Xxx.tsx` |
| 无业务共享 Hook | `shared/hooks/useXxx.ts` |
| 后端 HTTP API 调用 | `api/{module}Api.ts` |
| HTTP 契约类型 | `types/{module}.ts` |
| Zustand Store | `stores/{domain}Store.ts` |
| 工具函数 | `shared/utils/xxx.ts` |
| 全局样式 | `styles/globals.css` |
| 静态资源 | `public/` |
