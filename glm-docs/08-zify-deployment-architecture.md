# Zify 部署架构

> 目标：50 人内部使用，生产环境用 Docker + K8s 部署。
> 原则：当前阶段不搞复杂运维，组件够用即可，预留扩展空间。

---

## 组件清单

| 组件 | 作用 | 数量 |
|------|------|------|
| Nginx | 反向代理 + 前端静态资源托管 | 1 |
| Zify Server | Spring Boot 后端应用 | 1 |
| MySQL | 业务数据（Agent、对话、工作流、工具、触发器等） | 1 |
| PostgreSQL + PGVector | 向量数据（知识库 chunk、Embedding） | 1 |
| Redis | 缓存（流式响应中转、解析进度、对话上下文、限流） | 1 |

---

## 请求流转

```
用户浏览器
    │
    ▼
┌─────────────────────────────────────────────────┐
│  Nginx (:80/:443)                               │
│                                                 │
│  /              → 返回 React 静态文件（HTML/JS/CSS）│
│  /assets/*      → 返回 React 静态资源              │
│  /api/**        → 反向代理到 Zify Server           │
│  /api/engine/** → 反向代理到 Zify Server（SSE 长连接）│
└──────────────────┬──────────────────────────────┘
                   │
                   ▼
┌─────────────────────────────────────────────────┐
│  Zify Server (:8080)                            │
│  Spring Boot 单体应用                             │
│                                                 │
│  接收 HTTP 请求 → 业务处理 → 返回响应               │
│       │          │          │                    │
│       ▼          ▼          ▼                    │
│   MySQL      PGVector     Redis                 │
│  (业务数据)  (向量检索)   (缓存/限流)               │
│                                                 │
│  调用外部 LLM API ──→ OpenAI / Anthropic / ...    │
└─────────────────────────────────────────────────┘
```

---

## 各组件职责

### Nginx

| 职责 | 说明 |
|------|------|
| 前端托管 | 直接返回 React 构建产物（`index.html`、JS、CSS），不经后端 |
| API 代理 | `/api/**` 转发到 Zify Server，前端不直接暴露后端端口 |
| SSL 终止 | HTTPS 证书配置在 Nginx，后端走 HTTP |
| SSE 支持 | `/api/engine/**` 的 SSE 长连接，关闭 Nginx 的响应缓冲（`proxy_buffering off`） |
| 静态资源缓存 | `/assets/**` 设置长期缓存（文件名带 hash），`index.html` 不缓存 |

### Zify Server

| 职责 | 说明 |
|------|------|
| 业务 API | 所有业务接口（Agent、对话、工作流、知识库、工具、触发器、模型管理） |
| SSE 流式推送 | Agent 对话的流式响应，通过 SseEmitter 实现 |
| 定时任务 | Quartz 调度的 Cron 触发器，随应用启动 |
| 文档异步解析 | 文档上传后的解析、分块、向量化，虚拟线程异步执行 |
| 外部调用 | 调用 LLM API、MCP Server、HTTP 工具等外部服务 |

### MySQL

| 存储内容 | 说明 |
|----------|------|
| Agent 配置 | 名称、描述、类型、System Prompt、绑定的模型/工具/知识库 |
| 对话记录 | 会话和消息，高频写入 |
| 工作流定义 | 节点和连线的 JSON 定义 |
| 工作流运行日志 | 每次执行的节点状态、输入输出、耗时 |
| 工具配置 | MCP Server 连接、HTTP 工具定义 |
| 触发器配置 | Webhook URL、Cron 表达式、触发日志 |
| 模型 Provider | 供应商配置、API Key（加密存储） |

### PostgreSQL + PGVector

| 存储内容 | 说明 |
|----------|------|
| 文档 chunk | 知识库文档分块后的文本内容 |
| Embedding 向量 | 每个 chunk 的向量表示，用于相似度检索 |
| 相似度检索 | `SELECT * FROM chunk ORDER BY embedding <=> $query_vector LIMIT K` |

### Redis

| 用途 | Key 格式 | TTL |
|------|---------|-----|
| 文档解析进度 | `doc:parse:{docId}` → `parsing:60%` | 解析完成后删除 |
| 对话上下文缓存 | `chat:ctx:{conversationId}` → 最近 N 条消息 JSON | 30 分钟 |
| 接口限流 | `rate:{userId}:{api}` → 计数 | 1 分钟窗口 |
| SseEmitter 管理 | `sse:{conversationId}` → 连接元信息 | 随连接生命周期 |

---

## K8s 部署拓扑

当前阶段用最小资源：

```yaml
# 命名空间
Namespace: zify

# 工作负载
Deployment: zify-server     (1 Pod, 2C4G, Spring Boot)
Deployment: zify-nginx      (1 Pod, 0.5C1G, Nginx)
StatefulSet: zify-mysql     (1 Pod, 2C4G, MySQL 8)
StatefulSet: zify-postgres  (1 Pod, 2C4G, PostgreSQL + PGVector)
StatefulSet: zify-redis     (1 Pod, 0.5C1G, Redis 7)

# 服务暴露
Service: zify-nginx         (ClusterIP, 暴露给 Ingress)
Service: zify-server        (ClusterIP, Nginx 代理到这里)
Service: zify-mysql         (ClusterIP, 只有 Server 能访问)
Service: zify-postgres      (ClusterIP, 只有 Server 能访问)
Service: zify-redis         (ClusterIP, 只有 Server 能访问)

# 入口
Ingress: zify-ingress       (域名 → zify-nginx:80)

# 配置
ConfigMap: zify-server-config  (应用配置)
Secret:   zify-db-secret       (数据库密码、API Key)
Secret:   zify-redis-secret    (Redis 密码)
```

网络访问策略：

```
外部用户 → Ingress → Nginx → Zify Server → MySQL / PostgreSQL / Redis / 外部LLM API
                                              ↑
                                    数据库只有 Server 能访问，不对外暴露
```

---

## Docker Compose（开发 / 测试环境）

生产用 K8s，本地开发和测试用 Docker Compose：

```yaml
services:
  mysql:
    image: mysql:8
    ports:
      - "3306:3306"
    environment:
      MYSQL_ROOT_PASSWORD: ${MYSQL_PASSWORD}
      MYSQL_DATABASE: zify
    volumes:
      - mysql_data:/var/lib/mysql

  postgres:
    image: pgvector/pgvector:pg16
    ports:
      - "5432:5432"
    environment:
      POSTGRES_PASSWORD: ${PG_PASSWORD}
      POSTGRES_DB: zify_vector
    volumes:
      - pg_data:/var/lib/postgresql/data

  redis:
    image: redis:7-alpine
    ports:
      - "6379:6379"
    command: redis-server --requirepass ${REDIS_PASSWORD}
    volumes:
      - redis_data:/data

  zify-server:
    build: ./zify-server
    ports:
      - "8080:8080"
    environment:
      SPRING_PROFILES_ACTIVE: prod
      MYSQL_HOST: mysql
      PG_HOST: postgres
      REDIS_HOST: redis
    depends_on:
      - mysql
      - postgres
      - redis

  nginx:
    image: nginx:alpine
    ports:
      - "80:80"
    volumes:
      - ./zify-web/dist:/usr/share/nginx/html:ro
      - ./nginx.conf:/etc/nginx/conf.d/default.conf:ro
    depends_on:
      - zify-server

volumes:
  mysql_data:
  pg_data:
  redis_data:
```

---

## 资源估算（50 人）

| 组件 | CPU | 内存 | 磁盘 | 说明 |
|------|-----|------|------|------|
| Zify Server | 2C | 4G | - | 主要消耗在 LLM 调用等待（虚拟线程不占资源） |
| MySQL | 2C | 4G | 20G | 50 人数据量很小 |
| PostgreSQL | 2C | 4G | 50G | 向量数据占用空间较大，视知识库文档量 |
| Redis | 0.5C | 1G | - | 缓存数据量小 |
| Nginx | 0.5C | 1G | - | 静态资源托管，几乎不消耗 |
| **总计** | **7C** | **14G** | **70G** | |

50 人使用量下，一台 8C16G 的机器就能全部装下。

---

## 不做的事情（当前阶段）

- 不做 MySQL / PostgreSQL 主从复制，单节点够用
- 不做 Redis 集群，单节点够用
- 不做 Zify Server 多副本（水平扩展），单 Pod 够用
- 不做对象存储（MinIO / S3），文件直接存本地磁盘或数据库
- 不做消息队列（Kafka / RabbitMQ），异步任务用虚拟线程 + 数据库状态轮询
- 不做 ELK 日志系统，直接看 Pod 日志 `kubectl logs`
