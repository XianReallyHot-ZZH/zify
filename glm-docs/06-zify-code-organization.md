# Zify 代码组织规范

> 本文只定义后端代码组织规范。前端代码组织规范后续单独定义。
> 基于模块化单体架构，后端使用 Spring Boot + MyBatis-Plus，目标是让 AI 生成代码时可以直接按规则落文件、写依赖、做跨模块调用。

---

## 一、全局原则

- Zify 后端一期采用单 Spring Boot 应用、单 Maven 工程。
- 所有业务模块都放在 `src/main/java/com/zify/{module}/` 下。
- 模块名使用小写英文：`agent`、`chat`、`engine`、`workflow`、`knowledge`、`tool`、`trigger`、`model`。
- `common` 不是业务模块，只放全局基础设施和通用工具。
- 任何跨模块调用都必须走被调用模块的 `api` 层 Facade。
- 未在本文“模块间依赖关系”中列出的跨模块依赖，一律禁止。确实需要新增依赖时，先更新本文档，再写代码。

---

## 二、模块清单

```text
src/main/java/com/zify/
├── agent/          # Agent 管理：创建/编辑 Agent，配置 Prompt、模型、工具、知识库、工作流
├── chat/           # 对话管理：会话列表、消息流、新建/删除会话
├── engine/         # Agent 对话引擎：ReAct 循环、流式响应、工具调用编排
├── workflow/       # 工作流引擎：画布编排、节点执行、变量传递、运行日志
├── knowledge/      # 知识库 RAG：文档上传、解析、分块、向量化、检索、命中测试
├── tool/           # 统一工具系统：MCP / HTTP / Workflow-as-Tool 的注册与执行入口描述
├── trigger/        # 触发器：Webhook 接收、Cron 调度、触发记录
├── model/          # 模型管理：Provider 配置、模型连通性测试
└── common/         # 公共基础设施：配置、异常、统一响应、通用工具
```

---

## 三、业务模块目录结构

每个业务模块统一使用以下结构：

```text
src/main/java/com/zify/{module}/
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
- XML Mapper 放在 `src/main/resources/mapper/{module}/XxxMapper.xml`。
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

允许的依赖关系如下：

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

`common` 不按四层结构拆分，只按基础设施能力分包：

```text
src/main/java/com/zify/common/
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
| Facade 接口 | `{module}/api/{Module}Facade.java` |
| 跨模块 DTO | `{module}/api/dto/XxxDTO.java` |
| 跨模块命令对象 | `{module}/api/dto/XxxCommand.java` |
| 跨模块查询对象 | `{module}/api/dto/XxxQuery.java` |
| 跨模块结果对象 | `{module}/api/dto/XxxResult.java` |
| Service | `{module}/domain/XxxService.java` |
| Executor | `{module}/domain/executor/XxxExecutor.java` |
| Handler | `{module}/domain/handler/XxxHandler.java` |
| Validator | `{module}/domain/validator/XxxValidator.java` |
| Entity | `{module}/infrastructure/entity/XxxEntity.java` |
| MyBatis-Plus Mapper | `{module}/infrastructure/mapper/XxxMapper.java` |
| 可选 Repository | `{module}/infrastructure/repository/XxxRepository.java` |
| Converter | `{module}/infrastructure/converter/XxxConverter.java` |
| Facade 实现 | `{module}/infrastructure/facade/{Module}FacadeImpl.java` |
| 外部 API Client | `{module}/infrastructure/client/XxxClient.java` |
| Controller | `{module}/adapter/web/{Module}Controller.java` |
| SSE Controller | `{module}/adapter/sse/XxxSseController.java` |
| HTTP Request | `{module}/adapter/web/request/XxxRequest.java` |
| HTTP Response | `{module}/adapter/web/response/XxxResponse.java` |
| XML Mapper | `src/main/resources/mapper/{module}/XxxMapper.xml` |
| 全局配置 | `common/config/` |
| 全局异常 | `common/exception/` |
| 统一响应体 / 分页 | `common/web/` |
| 全局工具类 | `common/util/` |

---

## 十、AI 生成代码执行顺序

AI 新增一个功能时，按以下顺序执行：

1. 判断功能属于哪个业务模块。
2. 如果需要调用其他模块，先检查“模块间依赖关系”是否允许。
3. 若允许跨模块调用，只在当前模块 Service 中注入目标模块 Facade。
4. 先创建或复用当前模块 `api/dto` 中的 Command / Query / DTO / Result。
5. 创建 Entity 和 Mapper。
6. 创建 Converter。
7. 创建 Service，并在 Service 中写业务逻辑和事务。
8. 创建 FacadeImpl，把 Facade 调用转发到 Service。
9. 创建 Controller、HTTP Request、HTTP Response。
10. 确认没有跨模块引用 Service / Mapper / Repository / Entity / Controller。
