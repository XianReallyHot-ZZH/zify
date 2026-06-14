# Zify 工具调用技术方案（HTTP 工具 / MCP 工具）

> Zify 的 Agent（ReAct）和工作流（Tool 节点）需要调用外部 HTTP API 和 MCP Server。工具调用与 LLM 调用有一个根本差异——**幂等性**——决定了本方案的全部策略。
> 本方案从模块边界、线程、超时、重试、容错、异常、安全、MCP/HTTP 实现八个维度定义一期可落地方案。
> LLM 调用见 `07-zify-LLM-api-calling.md`，本文不覆盖 LLM。
> 制定依据：`docs-research/phase-P2/00-pending-tech-decisions.md`（B1 / C1 / C2 / C3 / C6 / C7）。

---

## 一、适用范围

本文覆盖以下外部调用：

- HTTP 工具调用（用户配置的 REST API，手动配置或 OpenAPI 解析）。
- MCP 工具调用（MCP Server 的连接、工具发现、工具执行）。

不覆盖：

- LLM 调用（见 `07`）。
- 工作流 HTTP Request 节点（工作流编排内的 HTTP，P4 定义，遵循类似原则）。

---

## 二、模块边界

工具调用统一归口 `tool` 模块 `ToolFacade`，不放在 `engine` / `workflow` 模块。

```text
zify-engine / zify-workflow（通过 Maven 依赖 zify-tool）
        ↓
zify-tool 子模块内 com.zify.tool.api.ToolFacade
        ↓
zify-tool 子模块内 com.zify.tool.domain.ToolService
        ↓
zify-tool 子模块内 com.zify.tool.infrastructure.executor.HttpToolExecutor / McpToolExecutor
```

硬性规则：

- engine / workflow 禁止直接发起 HTTP/MCP 工具调用，只能通过 `ToolFacade`。
- `ToolFacade` 对外**只暴露中立 DTO**（`ToolViewDTO` / `ToolExecutionCommand` / `ToolExecutionResultDTO`），不泄漏 Spring AI 类型（B2 中立边界）。
- 工具的鉴权凭据（Header/Body 里的 token/API Key）只在 `tool` 模块内解密使用，不返回、不记录（对齐 §6）。
- `tool_call_log` 由 `ToolFacade.executeTool` 内部写（执行点即记录点），engine / chat 不写（P2 A2）。

---

## 三、线程管理

### 3.1 选型

- Java 21 Virtual Threads。
- 工具执行用 Spring 管理的**独立**虚拟线程执行器 `ToolExecutor`，与 `llmTaskExecutor`（LLM 调用，见 `07` §3.2）**隔离**，避免工具 IO 与 LLM 调用相互影响。

```java
@Configuration
public class ToolExecutorConfig {
    @Bean(destroyMethod = "close")
    public ExecutorService toolExecutor() {
        return Executors.newVirtualThreadPerTaskExecutor();
    }
}
```

> CLAUDE.md §3「禁 `Executors.newXxx()`」针对 `newFixed/newCached` 的无界队列 OOM 风险；`newVirtualThreadPerTaskExecutor`（虚拟线程，轻量）是 `07` §3.2 已定例外。

### 3.2 并发保护

全局并发上限（防止工具调用风暴打爆外部服务）：

```yaml
zify:
  tool:
    executor:
      max-concurrent: 50
```

调用工具前必须先获取 `Semaphore` 许可（一期全局；per-tool 限流留二期）。

### 3.3 并行工具调用

模型一次决策请求多个工具时，**并行执行**（独立 IO 提效）：各 submit 到 `toolExecutor` + acquire `Semaphore` → `CompletableFuture.allOf` 等全部完成。各工具独立 try/catch（互不影响、各自 status）。

engine 循环在虚拟线程上**同步**调 `ToolFacade`（阻塞 IO 不占 OS 线程）；不用异步编排。

---

## 四、超时策略

### 4.1 超时类型

| 超时类型 | 默认值 | 说明 |
|---|---:|---|
| 连接超时 | 10 秒 | TCP/TLS 建连 |
| MCP 握手超时 | 15 秒 | MCP `initialize` 握手（含建连） |
| 单次请求超时 | 30 秒 | 单次 HTTP 完整请求+读取；**工具定义 `tool.timeout_seconds` 可覆盖** |
| 总调用超时 | ≤60 秒 | 含重试；且不超过 ReAct 循环剩余时间（见 `02` §5.5 / P2 C4） |

### 4.2 配置

```yaml
zify:
  tool:
    timeout:
      connect: 10s
      mcp-handshake: 15s
      request-default: 30s   # 工具定义未设时的兜底
      total-cap: 60s
```

### 4.3 执行规则

- 每次调用创建 `deadline = now + totalTimeout`。
- 重试前检查剩余时间，不足则直接失败。
- 单次请求超时触发后，按重试策略决定是否重试（见 §五）。
- 总 deadline 与 ReAct 循环 deadline（P2 C4，默认 120s）取交集——循环每轮检查剩余时间。

---

## 五、重试策略

### 5.1 不使用 `@Retryable`

显式 retry wrapper。原因：需区分幂等性、加 jitter、遵守总 deadline、读取 `Retry-After`。

### 5.2 可重试条件（幂等性驱动）—— **核心差异**

工具调用与 LLM 的根本区别：**LLM 天然幂等可自由重试；工具多数非幂等（POST/写操作），重试会重复副作用**。重试安全与否取决于「请求是否已送达 + 工具是否幂等」：

| 失败时机 | 幂等工具（GET/HEAD 或声明 `idempotent`） | 非幂等工具（POST/PUT/DELETE/PATCH 默认） |
|---------|---|---|
| **建连失败**（连接超时/被拒） | ✅ 可重试 | ✅ **可重试**（请求未送达，无副作用） |
| 请求已发出后失败（读超时/5xx/429） | ✅ 可重试 | ❌ **不重试**（不确定是否已执行，重试风险重复副作用） |
| 4xx（参数/认证/404） | ❌ 不重试 | ❌ 不重试 |
| 熔断中 | ❌ 直接失败 | ❌ 直接失败 |

### 5.3 重试参数

| 参数 | 默认值 |
|---|---:|
| 最大重试次数 | 2 |
| 最大总尝试次数 | 3 |
| 初始退避 | 1 秒 |
| 退避倍率 | 2 |
| jitter | 20% |
| 单次最大等待 | 10 秒 |

### 5.4 幂等标记

`tool` 表 `idempotent` 字段：HTTP 工具按 method 推断默认值（GET/HEAD=true，POST/PUT/DELETE/PATCH=false），用户可显式覆盖；MCP 工具默认 false，用户显式声明。

---

## 六、容错策略

### 6.1 熔断（per `tool_id`）

进程内轻量熔断，键为 `tool_id`（一个挂掉的工具不能拖垮整轮 ReAct），不引入 Resilience4j。

```text
CLOSED  正常调用
OPEN    连续 5 次可重试失败后熔断，直接拒绝（60 秒）
HALF_OPEN 冷却后允许 1 个探测请求
```

```yaml
zify:
  tool:
    circuit-breaker:
      failure-threshold: 5
      open-duration: 60s
```

规则：

- 连续 5 次可重试失败 → `OPEN` 60 秒。
- 60 秒后 `HALF_OPEN`，允许 1 个探测请求；探测成功 → `CLOSED`，失败 → 重新 `OPEN`。
- 4xx（配置错误）不计入熔断失败次数。

### 6.2 失败回灌（不中断 ReAct 循环）

`ToolFacade.executeTool` 统一返回 `ToolExecutionResultDTO{status, output}`，**不向 engine 抛异常**（对齐 Spring AI `spring.ai.tools.throw-exception-on-error=false`）。失败分类回灌给模型，让模型自主决策（换工具/改参数/直接回答）：

| 失败类型 | output（回灌模型） |
|---------|--------------------------|
| 可重试故障重试耗尽 | `工具 <name> 暂时不可用，请稍后重试或换一种方式` |
| 参数/认证错误（4xx） | `调用工具 <name> 失败：<精简错误>` |
| 熔断中 | `工具 <name> 当前不可用` |
| 非幂等执行失败 | `工具 <name> 执行失败：<精简错误>` |

仅**致命错误**（如循环超轮次、用户中断）才中断整轮 → 发 SSE `run_error`（见 P2 C4）。

---

## 七、异常分类

异常统一定义在 `zify-tool` 子模块 `infrastructure/exception/` 下。

```text
ToolException
├── ToolRetryableException        # 429、5xx、连接失败、读超时（幂等时）
├── ToolNonRetryableException     # 4xx、参数错误、非幂等请求发出后失败
├── ToolTimeoutException          # 总 deadline 超时
├── ToolBusyException             # 全局并发已满（Semaphore 获取失败）
├── ToolCircuitOpenException      # 该 tool_id 熔断中
└── ToolCancelledException        # 用户中断/任务取消
```

规则：

- 异常中**不能包含鉴权凭据**。
- 异常在 `tool` 模块内部捕获并转化为 `ToolExecutionResultDTO.status=ERROR`；**对 engine 只暴露 DTO**，不暴露异常类型（B2 中立边界）。
- 结构化日志（`event=tool_call`）覆盖成功/失败/取消。

---

## 八、安全

### 8.1 SSRF 防护

HTTP 工具可配任意 URL，是 SSRF 入口。**黑名单模式**（默认开启）：

- 禁止解析到内网/保留地址——IPv4：`127.0.0.0/8`、`10.0.0.0/8`、`172.16.0.0/12`、`192.168.0.0/16`、`169.254.0.0/16`（含云元数据 `169.254.169.254`）、`0.0.0.0/8`、`100.64.0.0/10`(CGN)；IPv6：`::1`、`fc00::/7`、`fe80::/10`。
- DNS 检查：解析域名 → 校验所有 IP 不在黑名单 → 连接用已解析 IP（一期基础防护；完整 DNS-rebinding 防护自定义 DNS resolver 留二期）。
- 校验时机：工具/MCP server **保存时**（即时反馈）+ **运行时**（防配置后 IP 变更）。MCP Server URL 走同样校验。

### 8.2 大小限制 + 截断

- **响应**：默认上限 **32KB**（可配），超过截断 + 标记 `truncated`；回灌模型/存 `message`/`tool_call_log` 用截断后内容。
- **请求体**：默认上限 **1MB**（防超大请求）。

### 8.3 Header 防护

用户配 Header 允许任意键值（工具需要），但敏感 Header（`Authorization`/`Cookie`/`Set-Cookie`）**不明文记入** `tool_call_log`/output（脱敏）；Header 值大小限制。

```yaml
zify:
  tool:
    security:
      ssrf:
        enabled: true              # 黑名单开关
        allow-private: false       # 允许内网（内网部署时可开）
      response-max-bytes: 32768    # 响应截断阈值 32KB
      request-max-bytes: 1048576   # 请求体上限 1MB
```

---

## 九、MCP 工具

### 9.1 传输方式

一期只做 **Streamable-HTTP + SSE**（远程 MCP Server），**不做 stdio**。理由：Zify 是 Web 服务（Docker/K8s），stdio 需启动 Server 子进程 + 管理进程生命周期，一期复杂、部署不优雅。

### 9.2 客户端实现

采用 `spring-ai-starter-mcp-client`（HttpClient 版），但：

- `McpClient` 用 **SYNC** 类型（对齐 `07` §3.1 同步风格）。
- **关闭 ToolCallback auto-config**（`spring.ai.mcp.client.toolcallback.enabled=false`）——不用 starter 把 MCP 工具包成 `ToolCallback` 注册给 ChatClient（B2 决策 Zify 不用 ChatClient/ToolCallback）。
- tool 模块自己适配：`McpClient.listTools()` → `ToolViewDTO`（inputSchema 取 `McpSchema.Tool.inputSchema()`）；`McpClient.callTool(name, args)` → `ToolExecutionResultDTO`。
- `requestTimeout` 30s（对齐 §四）。

> B2 中立边界修正：tool 模块「中立」从「pom 零 spring-ai」精确为「**接口中立**」——接口不含 spring-ai 的 LLM 抽象（`ChatModel`/`ToolCallback`/`ChatResponse`），但 infrastructure 层可用 `spring-ai-mcp-client`（MCP 协议实现，与 HTTP 工具用 RestClient 同性质）。

### 9.3 连接生命周期

- **常驻保活**：starter 应用启动时连接已配置的 `mcp_server`，复用连接；新增 server 即时建连；删除/禁用 server 关闭连接。不按需建连（ReAct 多次调同一 server，反复建连开销大）。
- **工具发现**：连接建立后 `listTools()` → 写 `tool` 表（`source_type=MCP`、`mcp_server_id`、`input_schema`）；`toolsChangeConsumer`（starter 提供）监听 server 端工具增删 → 增量更新 `tool` 表。
- **断连重连 + 状态标记**：starter 内置重连；`mcp_server` 表存连接状态（`ONLINE`/`OFFLINE`/`ERROR`）。重连失败 → 标记 `ERROR` → `listBoundTools` 时该 server 下工具不可用（降级）；连接恢复 → 重新 `listTools` 刷新 + 状态回 `ONLINE`。
- **并发**：每个 `mcp_server` 一条 `McpClient` 连接，并发调用复用（MCP JSON-RPC 单连接多请求）；不需 per-server 连接池。

---

## 十、HTTP 工具

### 10.1 两种定义方式（底层同构）

手动配置（用户填 endpoint/method/header/body 模板 + inputSchema）与 OpenAPI 解析（导入 spec 自动提取）产出**同构的 tool 配置**——底层 `tool` 表存统一的「HTTP 工具配置」。

### 10.2 OpenAPI 解析

- 版本：OpenAPI **3.0 / 3.1**。
- 映射粒度：**一个 operation → 一个 tool**（path+method 唯一标识）；一个 spec 多 operation → 导入多个 tool。
- 解析库：**Swagger Parser**（`io.swagger.parser.v3:swagger-parser-v3`，OpenAPI 解析事实标准）。
- 解析产出：每个 operation → `endpoint`(baseUrl+path) / `method` / 参数(name/in/type/required) → 生成 `input_schema` + 参数映射。

### 10.3 鉴权凭据加密

Header/Body 里的 token/API Key 敏感信息**加密存储**，复用 `common.SecretEncryptor`（P1 Provider API Key 已用）；明文仅执行时解密、**不记录、不返回**（对齐 §6）。`tool` 表存 `auth_config`（加密 JSON）。

### 10.4 参数映射（OpenAPI `in` 字段）

LLM 填的 args 按 `in` 映射：`path`→填充 URL 模板（`/users/{id}`）、`query`→拼 query string、`header`→设请求头、`body`→request body（POST/PUT）。手动配置工具：用户定义参数→path/query/header/body 映射或固定模板。

---

## 十一、工具生命周期与运行时校验

- 工具禁用（`tool.enabled=0`）或软删 → `listBoundTools` **不返回**（本轮 LLM 看不到）。
- MCP Server 断连（`status != ONLINE`）→ 其下工具 `listBoundTools` 过滤掉。
- 绑定校验：保存时校验工具存在+enabled（即时反馈）+运行时 `listBoundTools` 再校验（对齐 P1 A-07）。
- `listBoundTools` 过滤逻辑：`tool.enabled=1 AND tool.is_deleted=0 AND (source_type=HTTP OR mcp_server.status=ONLINE)`。
- 命名冲突去重：HTTP 工具用户起名校验 `tool.name` 未删除唯一；MCP 工具冲突加前缀 `mcpServerName__toolName`（Zify 自实现）。

---

## 十二、总结

| 维度 | 一期方案 | 关键规则 |
|---|---|---|
| 模块边界 | 归口 `tool.ToolFacade`，中立 DTO | engine/workflow 不直接调；API Key 不外泄；tool_call_log 执行点即记录 |
| 线程 | 独立 `ToolExecutor`（虚拟线程 + Semaphore 50） | 与 `llmTaskExecutor` 隔离；并行多工具用 `CompletableFuture.allOf` |
| 超时 | 连接 10s / MCP 握手 15s / 单次 30s（可配）/ 总 ≤60s | 总 deadline 受 ReAct 循环 deadline 约束 |
| 重试 | 显式 wrapper，**幂等性驱动** | 建连失败可重试；请求发出后按幂等性；4xx 不重试 |
| 容错 | 熔断 per `tool_id` + 失败回灌 | 连续 5 次失败 OPEN 60s；失败回灌不中断循环 |
| 异常 | `ToolException` 体系 | 对 engine 只暴露 DTO |
| 安全 | SSRF 黑名单 + 大小限制 + Header 脱敏 | 默认禁内网；响应 32KB 截断 |
| MCP | spring-ai-mcp-client（SYNC）+ 关闭 ToolCallback | Streamable-HTTP/SSE；常驻保活 + toolsChange 增量 |
| HTTP | 手动配置 / OpenAPI 3.0-3.1（Swagger Parser） | 一 operation→一 tool；鉴权加密；参数按 `in` 映射 |
