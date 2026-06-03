# Zify 性能瓶颈预判

> 基于当前部署架构：`zify-app` 启动模块聚合打包的 `zify-server` 单 Pod、MySQL 单节点、PostgreSQL + pgvector 单节点、Redis 单节点、上传文件 PVC、Ingress + Nginx、每日备份 CronJob。
> 目标：50 人内部使用。原则：一期不引入消息队列、多副本、分库分表、复杂监控平台，但必须在编码和部署时避免明显瓶颈。

严重程度 = 发生概率 × 影响范围。

---

## 一、当前部署前提

```text
zify-server（由 zify-app 启动模块聚合打包）:
  replicas = 1
  requests = 1C2G
  limits   = 2C4G
  strategy = Recreate

MySQL:
  单节点 StatefulSet + PVC

PostgreSQL + pgvector:
  单节点 StatefulSet + PVC

Redis:
  单节点 StatefulSet + PVC

Uploads:
  /data/uploads PVC

Ingress / Nginx:
  SSE timeout 已按 08 文档配置
```

这意味着：

- CPU、内存、DB 连接池、PVC IO 都集中在单实例上。
- K8s 可以自动重启 Pod，但不能解决数据库慢、磁盘慢、外部 LLM 慢。
- 一期可以接受短时间不可用，但不能接受数据丢失。

---

## 二、瓶颈清单

### 1. 外部 LLM API 响应慢、限流、失败

**严重程度**：最高

**现象**

- 用户发消息后首 token 很久不出现。
- SSE 中途断流。
- 返回 429 / 5xx。
- 工作流 LLM 节点长时间卡住。

**触发条件**

- 多个用户同时调用同一个 Provider。
- ReAct 循环连续调用 LLM 和工具，单个问题触发多次模型调用。
- Provider 账号额度、RPM、TPM 较低。
- Provider 自身降级或网络不稳定。

**根因**

Zify 的核心体验依赖外部模型服务，而 Provider 延迟、限流、故障不受 Zify 控制。

**一期处理**

- 使用 `07-zify-LLM-api-calling.md` 中的超时、显式重试、熔断和并发控制。
- 每个 Provider 设置最大并发，默认不允许无限并发打到下游。
- fallback 默认关闭，只在用户显式配置 fallback Provider 时启用。
- 流式调用只允许在首 chunk 前重试，已经输出内容后不自动重试。
- 不允许在数据库事务中调用 LLM。

**观测指标**

| 指标 | 说明 |
|---|---|
| `llm.firstTokenMs.p95/p99` | 首 token 延迟 |
| `llm.durationMs.p95/p99` | 模型调用总耗时 |
| `llm.http.429.count` | Provider 限流次数 |
| `llm.http.5xx.count` | Provider 服务端错误 |
| `llm.retry.count` | 重试次数 |
| `llm.provider.inflight` | Provider 当前并发 |
| `llm.provider.permit.waitMs` | 等待并发许可耗时 |

---

### 2. 数据库连接池被长调用拖垮

**严重程度**：高

**现象**

- 页面接口突然变慢。
- MySQL 连接池 active 接近上限。
- 请求等待数据库连接超时。
- LLM 响应慢时，普通 CRUD 也变慢。

**触发条件**

- 在 `@Transactional` 方法里调用 LLM API、Embedding API、HTTP 工具或 MCP。
- SSE 对话期间长时间持有数据库连接。
- 工作流执行期间把事务包住多个外部调用。
- 文档解析大批量写库时连接未及时释放。

**根因**

外部调用耗时 5-30 秒。如果事务覆盖外部调用，数据库连接会被长期占用。虚拟线程可以挂起很多请求，但数据库连接池数量有限。

**一期处理**

- 事务只包住数据库读写，不包住 LLM / HTTP / MCP 外部调用。
- 读取配置、写入消息、写入运行日志要拆成短事务。
- Hikari 连接池设置明确上限，避免无限等待。
- 对慢 SQL 和连接池等待时间做日志记录。

**建议配置**

```yaml
spring:
  datasource:
    hikari:
      maximum-pool-size: 20
      minimum-idle: 5
      connection-timeout: 2000
      validation-timeout: 1000
```

**观测指标**

| 指标 | 说明 |
|---|---|
| `hikari.connections.active` | 活跃连接数 |
| `hikari.connections.pending` | 等待连接线程数 |
| `hikari.connections.timeout.count` | 获取连接超时次数 |
| `db.query.durationMs.p95` | SQL 查询耗时 |
| `db.transaction.durationMs.p95` | 事务持续时间 |

---

### 3. 文档解析和向量化抢占资源

**严重程度**：高

**现象**

- 用户上传文档后，对话接口变慢。
- zify-server CPU / 内存升高。
- 上传 PVC IO 增高。
- Embedding 调用触发限流。
- PostgreSQL 写入向量变慢。

**触发条件**

- 多个用户同时上传 PDF / Word。
- 单个文件较大，例如 10MB+ PDF。
- 文档解析、分块、Embedding、向量写入同时发生。
- 备份 CronJob 和文档解析同时运行。

**根因**

文档处理同时消耗 CPU、内存、上传 PVC、Embedding Provider、PostgreSQL。当前部署中这些资源都在单节点或单 Pod 内。

**一期处理**

- 文件上传大小通过 Ingress 限制，默认不超过 100MB。
- 文档解析并发限制为 2，其余任务排队。
- Embedding 调用使用 Provider 并发控制。
- 文档处理任务状态落 MySQL，进度写 Redis。
- 解析失败可重试，不依赖内存状态。
- 上传文件只写 `/data/uploads` PVC，不写 Pod 本地磁盘。

**建议限制**

| 项 | 默认值 |
|---|---:|
| 同时解析文档数 | 2 |
| 单文件最大大小 | 100MB |
| 单知识库文档数 | 100 |
| 单批 Embedding chunk 数 | 16-64，按 Provider 限制调整 |

**观测指标**

| 指标 | 说明 |
|---|---|
| `doc.parse.queue.size` | 等待解析的文档数 |
| `doc.parse.durationMs.p95` | 文档解析耗时 |
| `doc.embedding.durationMs.p95` | 向量化耗时 |
| `doc.parse.failed.count` | 解析失败数 |
| `app.cpu.usage` | zify-server CPU |
| `app.memory.used` | zify-server 内存 |
| `pvc.uploads.used.percent` | 上传 PVC 使用率 |
| `postgres.insert.durationMs.p95` | 向量写入耗时 |

---

### 4. pgvector 检索和写入随数据量增长变慢

**严重程度**：中高

**现象**

- 知识库检索从毫秒级增长到秒级。
- Agent 对话首 token 前等待时间变长。
- 文档向量写入变慢。
- PostgreSQL CPU / IO 升高。

**触发条件**

- 单知识库 chunk 数超过 10 万。
- Agent 绑定多个知识库。
- 查询没有先按 `knowledge_id`、`document_id`、`enabled` 过滤。
- HNSW 索引过大，占用内存和磁盘。

**根因**

向量检索如果没有索引会退化为大范围扫描。即使有 HNSW，写入、索引维护、过滤条件、内存占用也会影响性能。

**一期处理**

- 建表时创建 HNSW 向量索引。
- 同时创建业务过滤索引，例如 `knowledge_id`、`document_id`、`enabled`。
- 查询必须带知识库范围，不允许全表向量检索。
- 限制 Top-K 默认值，例如 5 或 10。
- 限制单知识库文档数和 chunk 数。

示例：

```sql
CREATE INDEX idx_chunk_embedding
ON document_chunk
USING hnsw (embedding vector_cosine_ops)
WITH (m = 16, ef_construction = 64);

CREATE INDEX idx_chunk_knowledge_enabled
ON document_chunk (knowledge_id, enabled);
```

**注意**

- HNSW 提升查询速度，但会增加写入成本和索引体积。
- 大批量导入时可以先批量写入，再统一创建或重建索引。
- 不同 embedding 维度要使用匹配的 vector 列定义。

**观测指标**

| 指标 | 说明 |
|---|---|
| `rag.search.durationMs.p95` | 向量检索耗时 |
| `rag.search.topK` | 检索 Top-K |
| `rag.chunk.count` | chunk 总数 |
| `rag.chunk.count.byKnowledge` | 单知识库 chunk 数 |
| `postgres.cpu.usage` | PostgreSQL CPU |
| `postgres.index.size` | 向量索引大小 |

---

### 5. MySQL 消息和运行日志增长

**严重程度**：中

**现象**

- 对话页加载历史变慢。
- 工作流运行记录列表变慢。
- 工具调用日志查询变慢。
- MySQL 磁盘增长较快。

**触发条件**

- 50 人每天持续对话。
- Agent ReAct 记录每次工具调用过程。
- 工作流节点输入输出全部落库。
- 触发器每天自动运行。

**根因**

增长最快的不只是消息表，还包括：

- `message`
- `tool_call_log`
- `workflow_run`
- `workflow_node_run`
- `trigger_log`
- `document_parse_log`

**一期处理**

- 列表页必须分页。
- 对话消息按 `conversation_id + created_at` 建索引。
- 工作流运行日志按 `workflow_id + created_at` 建索引。
- 工具调用日志按 `tool_id + created_at` 建索引。
- 触发器日志按 `trigger_id + created_at` 建索引。
- 大字段输入输出不要在列表接口返回，只在详情接口按需读取。
- 一期不分库分表，但要预留日志清理任务。

**建议保留策略**

| 数据 | 默认保留 |
|---|---:|
| 对话消息 | 180 天或按用户删除 |
| 工具调用日志 | 90 天 |
| 工作流运行日志 | 90 天 |
| 触发器日志 | 90 天 |
| 文档解析日志 | 30 天 |

**观测指标**

| 指标 | 说明 |
|---|---|
| `mysql.table.rows.message` | 消息表行数 |
| `mysql.table.rows.workflow_run` | 工作流运行数 |
| `mysql.table.rows.tool_call_log` | 工具调用日志数 |
| `api.chat.history.durationMs.p95` | 对话历史接口耗时 |
| `api.workflow.run.list.durationMs.p95` | 工作流运行列表耗时 |
| `mysql.disk.used.percent` | MySQL PVC 使用率 |

---

### 6. SSE 长连接、断开取消和代理超时

**严重程度**：中

**现象**

- 前端流式响应中途断开。
- 用户关闭页面后，后端仍在调用 LLM。
- Ingress / Nginx 超时导致连接断开。
- 发布时正在对话的连接被中断。

**触发条件**

- 20+ 用户同时流式对话。
- 单次 Agent 对话超过 120 秒。
- Ingress / Nginx timeout 小于应用 SSE timeout。
- Pod 终止时没有优雅停机。

**根因**

SSE 本身不一定消耗大量 CPU，但会放大代理超时、连接生命周期、上游取消和发布策略的问题。

**一期处理**

- 按 `08-zify-deployment-architecture.md` 配置 Ingress/Nginx SSE timeout。
- 关闭 Nginx buffering。
- 用户断开、SSE timeout、发送失败时取消上游 LLM 调用。
- `zify-server` 使用 `Recreate` 发布策略和 180 秒 termination grace period。
- 不做复杂连接池，不做多副本连接迁移。

**观测指标**

| 指标 | 说明 |
|---|---|
| `sse.connections.active` | 当前 SSE 连接数 |
| `sse.durationMs.p95` | SSE 持续时间 |
| `sse.disconnect.count` | 非正常断开次数 |
| `llm.cancelled.count` | 用户断开导致的 LLM 取消次数 |
| `nginx.upstream.timeout.count` | Nginx 上游超时 |

---

### 7. 备份 CronJob 干扰数据库和 PVC IO

**严重程度**：中

**现象**

- 每天固定时间段系统变慢。
- MySQL / PostgreSQL CPU 或 IO 升高。
- 上传文件读取变慢。
- 备份期间文档解析或向量检索耗时增加。

**触发条件**

- `mysqldump`、`pg_dump`、uploads PVC 打包同时执行。
- 备份任务和文档解析、向量化、工作流高峰重叠。
- 备份输出目标网络慢。

**根因**

当前部署是单节点数据库和单 PVC。备份会读取大量数据，占用 CPU、磁盘 IO、网络带宽。

**一期处理**

- 备份安排在低峰期，例如凌晨。
- MySQL、PostgreSQL、uploads 备份错峰执行。
- 备份 CronJob 设置资源 requests/limits。
- 备份失败必须告警。
- 备份期间不主动触发大批量文档解析。

**建议时间**

| 任务 | 时间 |
|---|---|
| MySQL backup | 02:00 |
| PostgreSQL backup | 03:00 |
| uploads backup | 04:00 |

**观测指标**

| 指标 | 说明 |
|---|---|
| `backup.durationMs` | 备份耗时 |
| `backup.failed.count` | 备份失败次数 |
| `mysql.cpu.usage` | MySQL CPU |
| `postgres.cpu.usage` | PostgreSQL CPU |
| `pvc.io.read.bytes` | PVC 读取量 |
| `pvc.io.write.bytes` | PVC 写入量 |

---

## 三、可用性风险

以下不是性能瓶颈，但会影响生产可用性。

### 单点故障

当前所有核心组件都是单实例：

- `zify-server` 单 Pod
- MySQL 单节点
- PostgreSQL 单节点
- Redis 单节点
- uploads 单 PVC

一期处理：

- K8s 自动重启 Pod。
- readiness / liveness / startup probes。
- MySQL、PostgreSQL、uploads 每日备份。
- 每月恢复演练。

不做：

- 不做应用多副本。
- 不做数据库主从。
- 不做 Redis Cluster。

扩展前置条件见 `08-zify-deployment-architecture.md`。

---

## 四、汇总表

| 排名 | 瓶颈 | 严重程度 | 一期处理 |
|---:|---|---|---|
| 1 | 外部 LLM API 慢、限流、失败 | 最高 | 超时、重试、熔断、Provider 并发控制、显式 fallback |
| 2 | DB 连接池被长调用拖垮 | 高 | 事务不包外部调用、Hikari 上限、连接池监控 |
| 3 | 文档解析和向量化抢占资源 | 高 | 异步、并发限制、上传大小限制、任务状态落库 |
| 4 | pgvector 检索和写入变慢 | 中高 | HNSW、业务过滤索引、Top-K 限制、chunk 上限 |
| 5 | MySQL 消息和运行日志增长 | 中 | 索引、分页、详情按需查询、保留策略 |
| 6 | SSE 长连接和代理超时 | 中 | Ingress/Nginx timeout、关闭 buffering、断开取消上游 |
| 7 | 备份 CronJob 干扰 IO | 中 | 低峰错峰、资源限制、失败告警 |

---

## 五、一期不引入的复杂方案

50 人内部使用阶段不引入：

- Kafka / RabbitMQ。
- 应用多副本。
- HPA。
- MySQL / PostgreSQL 主从。
- 分库分表。
- Redis Cluster。
- ELK。

但编码和部署时必须做到：

- 所有慢操作有并发限制。
- 所有外部调用有超时。
- 所有列表查询分页。
- 所有增长型表有索引和保留策略。
- 所有核心数据有备份。
- 所有关键瓶颈有可观察指标。

---

## 六、触发架构升级的信号

出现以下情况时，再考虑更重的架构：

| 信号 | 升级方向 |
|---|---|
| 文档解析队列长期大于 20 | 拆独立文档处理 Worker 或引入队列 |
| LLM Provider permit 等待 p95 大于 2 秒 | 增加 Provider 配额、模型降级或显式 fallback |
| Hikari pending 频繁大于 0 | 优化事务范围、调大连接池、排查慢 SQL |
| pgvector 检索 p95 大于 1 秒 | 优化索引、减少 chunk、引入 rerank/混合检索或专用向量库 |
| MySQL PVC 使用率超过 80% | 清理日志、扩容 PVC、考虑归档 |
| uploads PVC 使用率超过 80% | 扩容 PVC 或引入 MinIO / S3 |
| 备份耗时超过 1 小时 | 优化备份策略或使用存储快照 |
| zify-server CPU 长期超过 70% | 优化解析并发，后续考虑拆 Worker |
