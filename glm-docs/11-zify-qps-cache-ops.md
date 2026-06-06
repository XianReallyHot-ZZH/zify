# Zify QPS 估算、缓存策略与运维事项

> 基于一期目标：Docker Compose 单副本部署，20-50 人同时在线，主要压力在对话接口（流式 SSE）。

---

## 一、QPS 估算

### 1.1 用户行为模型

| 指标 | 估算值 | 依据 |
|------|--------|------|
| 在线用户 | 20-50 人 | 一期目标 |
| 同时对话比例 | 25-30% | 非所有人同时发消息 |
| 活跃对话用户 | 5-15 人 | 50 × 30% |
| 每用户发消息间隔 | 30-60 秒 | 思考 + 阅读 LLM 回复 |
| 每条消息附带操作 | 1-3 次其他 API | 加载 Agent 列表、历史等 |

### 1.2 分层 QPS

| 接口类型 | 平均 QPS | 峰值 QPS | 瓶颈 |
|----------|---------|---------|------|
| 消息提交 POST | 0.3 - 0.5 | 2 - 3 | LLM 响应速度 |
| SSE 连接数 | 5 - 15 并发 | 20 并发 | 连接保持 / 内存 |
| 页面浏览 GET | 1 - 3 | 8 - 10 | 无 |
| LLM 调用 | 0.3 - 0.5 | 2 - 3 | Provider 并发限制 |
| 文件上传 | 偶发 | 偶发 | 磁盘 IO |

**结论：整体 API QPS 很低（1-5），瓶颈不在 QPS，而在 LLM 调用延迟和 SSE 连接保持。**

LLM 单次调用 2-10 秒，是整个系统的真正瓶颈。Provider 并发限制 20，远超需求。

---

## 二、缓存策略

### 2.1 Redis 缓存分层

```
L1 · 热数据（秒级过期）
  - SSE 进度状态、限流计数器
  - TTL: 30s - 300s

L2 · 温数据（分钟级过期）
  - Agent 详情、Workflow 定义、Tool 配置
  - 写入时主动失效（Cache Aside）
  - TTL: 5min - 15min

L3 · 冷数据（主动失效）
  - Model Provider 配置（含加密 Key）
  - 更新时删缓存，不设 TTL
```

### 2.2 具体缓存项

| 缓存项 | Key 模式 | 过期 | 策略 |
|--------|---------|------|------|
| Agent 详情 | `agent:{id}` | 10min | 读时回源，写时删除 |
| Agent 列表（分页） | `agents:list:{page}` | 5min | 写时删除全部列表 key |
| Workflow 定义 | `workflow:{id}` | 10min | 写时删除 |
| Provider 配置 | `provider:{id}` | 主动失效 | 不设 TTL，变更即删 |
| 对话上下文窗口 | `ctx:{conversationId}` | 30min | 对话结束删除 |
| SSE 心跳 / 进度 | `sse:{sessionId}` | 60s | 自动过期 |
| 接口限流 | `rate:{userId}:{api}` | 1s 滑动窗口 | 固定过期 |

### 2.3 不该缓存的数据

- **消息内容** — 直接写 MySQL，对话是追加写，缓存收益低。
- **Embedding 结果** — 写入 pgvector 后直接查，数据量大且更新少。
- **SSE Emitter 对象** — 禁止存 Redis（§8 明确要求）。

---

## 三、运维事项

### 3.1 部署前必须完成

| # | 事项 | 原因 |
|---|------|------|
| 1 | Provider API Key 加密存储 | MySQL 中密钥必须加密，密钥使用 K8s Secret |
| 2 | 日志脱敏 | API Key 最多显示前 4 位 + `***`，禁止明文输出 |
| 3 | TLS 配置 | Ingress 终止 TLS，zify-server 只接受内网流量 |
| 4 | 健康检查 | `/api/health` 已就位，需配置 K8s liveness/readiness probe |
| 5 | 数据库备份 | MySQL/PostgreSQL 每日备份，保留 14 天，备份不能只放同一 PVC |

### 3.2 日常运维

| # | 事项 | 建议方式 |
|---|------|---------|
| 6 | 历史数据清理 | message 保留 180 天，日志 30-90 天，分批删除（每批 1000 条） |
| 7 | Redis 内存监控 | 设置 maxmemory + allkeys-lru，避免 OOM |
| 8 | HikariCP 连接池监控 | 当前 20 连接，关注 active/idle/pending 比例 |
| 9 | LLM 调用监控 | 结构化日志：traceId、durationMs、token 数、status |
| 10 | 慢查询监控 | MySQL slow_query_log，阈值 200ms |

### 3.3 扩容前必须完成

从单副本扩到多副本前：

| # | 事项 | 原因 |
|---|------|------|
| 11 | Quartz 集群模式 | 多实例不能重复执行 Cron 任务 |
| 12 | 上传迁移到对象存储 | 多 Pod 不能共享本地磁盘 PVC |
| 13 | SSE 状态外置 | 单 Pod 内存中的 SseEmitter 不能跨实例共享 |
| 14 | 限流迁移到 Redis | 本地限流在多实例下不生效 |
| 15 | 会话亲和性或 SSE 网关 | SSE 连接必须路由到持有 Emitter 的实例 |

### 3.4 监控告警

```
必须监控：
├── 应用：JVM 堆内存、GC 频率、线程数
├── API：P50/P95/P99 延迟、错误率、QPS
├── LLM：调用延迟、成功率、Token 消耗
├── MySQL：连接数、慢查询、磁盘空间
├── Redis：内存使用、命中率、连接数
└── 系统：CPU、内存、磁盘 IO、网络

建议告警阈值：
├── API 错误率 > 5%          → 告警
├── LLM P95 > 30s            → 告警
├── MySQL 慢查询 > 1s/分钟    → 告警
├── Redis 内存 > 80%         → 告警
└── 磁盘使用 > 80%           → 告警
```

---

## 四、一期容量总结

```
目标用户：20-50 人
部署方式：Docker Compose 单副本
预期负载：平均 1-3 QPS，峰值 10 QPS，15-20 SSE 并发

最低资源配置：
├── zify-server:  2C4G（SSE 连接占内存）
├── MySQL:        1C2G + 20GB SSD
├── PostgreSQL:   1C2G + 10GB SSD（pgvector）
├── Redis:        512MB
└── 总计:         ~5C9G + 30GB 存储

一期完全够用，瓶颈在 LLM 调用延迟，不在基础设施。
```
