# Zify 部署架构

> 目标：50 人内部使用，生产环境使用 Docker 镜像 + K8s 部署。
> 原则：当前阶段不做复杂高可用，但生产环境必须保证数据不丢、配置清晰、可备份恢复、可排障。

---

## 一、部署结论

一期生产部署采用最小 K8s 架构：

- `zify-server` 单副本 Spring Boot 应用（由 `zify-app` 启动模块聚合所有 Maven 子模块打包）。
- `zify-nginx` 单副本前端静态资源 + API 反向代理。
- MySQL 单节点 StatefulSet。
- PostgreSQL + pgvector 单节点 StatefulSet。
- Redis 单节点。
- 上传文件使用 PVC，不使用 Pod 本地磁盘。
- Ingress 负责公网/内网入口和 TLS 终止。
- Nginx Pod 不负责 TLS，只负责静态资源和反向代理。

不做：

- 不做 MySQL / PostgreSQL 主从复制。
- 不做 Redis Cluster。
- 不做 Zify Server 多副本。
- 不做 HPA。
- 不做消息队列。
- 不做 ELK。

这些能力在 50 人内部使用阶段不是第一优先级，但必须预留后续扩展条件。

---

## 二、整体拓扑

```text
用户浏览器
    │ HTTPS
    ▼
Ingress
    │ TLS 终止，转发 HTTP
    ▼
zify-nginx Service
    │
    ▼
zify-nginx Pod
    ├── /              -> React index.html
    ├── /assets/*      -> React 静态资源
    ├── /api/**        -> zify-server Service
    └── /api/engine/** -> zify-server Service（SSE 长连接）
                               │
                               ▼
                         zify-server Pod
                               │
         ┌─────────────────────┼─────────────────────┐
         ▼                     ▼                     ▼
      MySQL              PostgreSQL + pgvector      Redis
   业务数据               向量数据 / chunk           缓存 / 进度 / 限流
         │
         ▼
   上传文件 PVC

zify-server -> 外部 LLM API / MCP Server / HTTP 工具
```

---

## 三、组件清单

| 组件 | K8s 资源 | 副本 | 是否持久化 | 说明 |
|---|---|---:|---|---|
| Ingress | Ingress | 由集群提供 | 否 | 域名、TLS、入口路由 |
| zify-nginx | Deployment + Service | 1 | 否 | React 静态资源、API 反向代理 |
| zify-server | Deployment + Service | 1 | 是，上传文件 PVC | Spring Boot 模块化单体（Maven 多模块，由 zify-app 打包） |
| MySQL | StatefulSet + Service + PVC | 1 | 是 | 业务数据 |
| PostgreSQL + pgvector | StatefulSet + Service + PVC | 1 | 是 | 文档 chunk 和向量 |
| Redis | StatefulSet + Service + PVC | 1 | 可持久化 | 缓存、进度、限流 |
| Backup CronJob | CronJob | 1 | 备份输出到备份存储 | MySQL、PostgreSQL、上传文件备份 |

---

## 四、K8s 资源拓扑

```text
Namespace: zify

Ingress:
  zify-ingress

Deployments:
  zify-nginx      replicas=1
  zify-server     replicas=1

StatefulSets:
  zify-mysql      replicas=1
  zify-postgres   replicas=1
  zify-redis      replicas=1

Services:
  zify-nginx      ClusterIP
  zify-server     ClusterIP
  zify-mysql      ClusterIP
  zify-postgres   ClusterIP
  zify-redis      ClusterIP

PVC:
  zify-mysql-pvc       50Gi
  zify-postgres-pvc    100Gi
  zify-redis-pvc       5Gi
  zify-uploads-pvc     20Gi

ConfigMap:
  zify-server-config
  zify-nginx-config

Secret:
  zify-db-secret
  zify-redis-secret
  zify-app-secret

NetworkPolicy:
  zify-network-policy

CronJob:
  zify-mysql-backup
  zify-postgres-backup
  zify-uploads-backup
```

---

## 五、Ingress 与 Nginx

### 5.1 TLS 终止位置

生产环境 TLS 统一在 Ingress 层终止。

```text
Browser --HTTPS--> Ingress --HTTP--> zify-nginx --HTTP--> zify-server
```

规则：

- TLS 证书配置在 Ingress，不配置在 zify-nginx Pod 内。
- zify-nginx 只监听 80 端口。
- zify-server 只监听 8080 端口，不对集群外暴露。

### 5.2 Ingress 配置要求

如果使用 NGINX Ingress Controller，必须为 SSE 设置较长超时：

```yaml
metadata:
  annotations:
    nginx.ingress.kubernetes.io/proxy-read-timeout: "180"
    nginx.ingress.kubernetes.io/proxy-send-timeout: "180"
    nginx.ingress.kubernetes.io/proxy-body-size: "100m"
```

规则：

- `proxy-read-timeout` 必须大于应用 SSE 最大响应时间。
- 文件上传大小必须通过 `proxy-body-size` 显式限制。
- Ingress 只转发到 `zify-nginx`，不直接转发到 `zify-server`。

### 5.3 zify-nginx 配置要求

```nginx
server {
    listen 80;
    server_name _;

    root /usr/share/nginx/html;
    index index.html;

    location /assets/ {
        expires 30d;
        add_header Cache-Control "public, max-age=2592000, immutable";
        try_files $uri =404;
    }

    location / {
        add_header Cache-Control "no-store";
        try_files $uri /index.html;
    }

    location /api/ {
        proxy_pass http://zify-server:8080;
        proxy_http_version 1.1;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
        proxy_read_timeout 180s;
        proxy_send_timeout 180s;
    }

    location /api/engine/ {
        proxy_pass http://zify-server:8080;
        proxy_http_version 1.1;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
        proxy_buffering off;
        proxy_cache off;
        gzip off;
        proxy_read_timeout 180s;
        proxy_send_timeout 180s;
        add_header X-Accel-Buffering no;
    }
}
```

---

## 六、zify-server 部署要求

### 6.1 Deployment

一期 `zify-server` 固定单副本：

```yaml
replicas: 1
strategy:
  type: Recreate
```

使用 `Recreate` 的原因：

- 当前阶段 Quartz 随应用启动（配置在 `zify-app` 启动模块），不能同时运行两个实例。
- 上传文件 PVC 默认按单写方式使用，避免滚动更新时两个 Pod 同时挂载。
- 50 人内部使用可以接受短暂发布时间窗口。

### 6.2 资源限制

```yaml
resources:
  requests:
    cpu: "1"
    memory: "2Gi"
  limits:
    cpu: "2"
    memory: "4Gi"
```

JVM 建议：

```text
JAVA_TOOL_OPTIONS=-XX:MaxRAMPercentage=70
```

### 6.3 健康检查

Spring Boot 开启 Actuator health：

```yaml
management:
  endpoints:
    web:
      exposure:
        include: health,info
  endpoint:
    health:
      probes:
        enabled: true
```

K8s probes：

```yaml
startupProbe:
  httpGet:
    path: /actuator/health/liveness
    port: 8080
  failureThreshold: 30
  periodSeconds: 5

readinessProbe:
  httpGet:
    path: /actuator/health/readiness
    port: 8080
  periodSeconds: 10
  failureThreshold: 3

livenessProbe:
  httpGet:
    path: /actuator/health/liveness
    port: 8080
  periodSeconds: 30
  failureThreshold: 3
```

### 6.4 优雅停机

```yaml
terminationGracePeriodSeconds: 180
```

Spring Boot 配置：

```yaml
server:
  shutdown: graceful

spring:
  lifecycle:
    timeout-per-shutdown-phase: 60s
```

规则：

- Pod 进入 terminating 后不再接收新请求。
- 正在进行的 SSE、文档解析、工作流执行尽量在 grace period 内结束。
- 未完成的长任务必须依赖数据库状态恢复，不能只存在内存里。

### 6.5 上传文件存储

上传文件不允许存 Pod 本地磁盘。

一期使用 PVC：

```text
PVC: zify-uploads-pvc
MountPath: /data/uploads
```

规则：

- MySQL 只存文件元数据：文件名、大小、类型、hash、存储路径。
- 原始文件写入 `/data/uploads`。
- 文档解析生成的 chunk 和向量分别进入 MySQL / PostgreSQL。
- 未来 zify-server 多副本前，必须把上传文件迁移到 MinIO / S3 / RWX 共享存储。

---

## 七、MySQL

### 7.1 存储内容

| 存储内容 | 说明 |
|---|---|
| Agent 配置 | 名称、描述、类型、System Prompt、绑定模型/工具/知识库 |
| 对话记录 | 会话、消息、工具调用过程 |
| 工作流定义 | 节点和连线 JSON |
| 工作流运行日志 | 节点状态、输入输出、耗时 |
| 工具配置 | MCP Server、HTTP 工具定义 |
| 触发器配置 | Webhook、Cron、触发日志 |
| 模型 Provider | Provider 配置、加密后的 API Key |
| 文件元数据 | 上传文件路径、hash、解析状态 |

### 7.2 部署要求

```text
StatefulSet: zify-mysql
PVC: zify-mysql-pvc 50Gi
Service: zify-mysql ClusterIP
```

规则：

- 不对集群外暴露 MySQL。
- root 密码和业务账号密码放 `zify-db-secret`。
- 初始化数据库和账号通过 init SQL 或迁移工具完成。
- 应用表结构变更必须使用 Flyway 或 Liquibase 管理。

---

## 八、PostgreSQL + pgvector

### 8.1 存储内容

| 存储内容 | 说明 |
|---|---|
| 文档 chunk | 知识库文档分块后的文本 |
| Embedding 向量 | 每个 chunk 的向量表示 |
| 向量索引 | pgvector 索引 |

### 8.2 部署要求

```text
StatefulSet: zify-postgres
Image: pgvector/pgvector:pg16
PVC: zify-postgres-pvc 100Gi
Service: zify-postgres ClusterIP
```

初始化 SQL：

```sql
CREATE EXTENSION IF NOT EXISTS vector;
```

规则：

- 不对集群外暴露 PostgreSQL。
- 密码放 `zify-db-secret`。
- pgvector extension 初始化必须自动化，不能依赖人工进入容器执行。
- 向量表结构变更必须纳入迁移脚本。

---

## 九、Redis

### 9.1 用途

| 用途 | Key 示例 | TTL |
|---|---|---|
| 文档解析进度 | `doc:parse:{docId}` | 解析完成后删除 |
| 对话上下文缓存 | `chat:ctx:{conversationId}` | 30 分钟 |
| 接口限流 | `rate:{userId}:{api}` | 1 分钟 |
| SSE 连接状态 | `sse:{conversationId}` | 连接结束后删除 |

### 9.2 部署要求

```text
StatefulSet: zify-redis
PVC: zify-redis-pvc 5Gi
Service: zify-redis ClusterIP
```

规则：

- Redis 不作为核心数据源，MySQL / PostgreSQL 才是最终数据源。
- Redis 重启导致缓存丢失时，业务必须可以恢复。
- Redis 密码放 `zify-redis-secret`。
- 不允许把 Java `SseEmitter` 对象放进 Redis，只能存连接状态、游标、进度等可序列化数据。

---

## 十、配置与 Secret

### 10.1 ConfigMap

`zify-server-config` 只放非敏感配置：

```text
SPRING_PROFILES_ACTIVE=prod
MYSQL_HOST=zify-mysql
MYSQL_PORT=3306
MYSQL_DATABASE=zify
PG_HOST=zify-postgres
PG_PORT=5432
PG_DATABASE=zify_vector
REDIS_HOST=zify-redis
REDIS_PORT=6379
UPLOAD_DIR=/data/uploads
```

### 10.2 Secret

`zify-db-secret`：

```text
MYSQL_USERNAME
MYSQL_PASSWORD
PG_USERNAME
PG_PASSWORD
```

`zify-redis-secret`：

```text
REDIS_PASSWORD
```

`zify-app-secret`：

```text
ZIFY_ENCRYPTION_KEY
ZIFY_SESSION_SECRET
```

规则：

- Provider API Key 存 MySQL 时必须加密。
- `ZIFY_ENCRYPTION_KEY` 只能放 Secret，不允许放 ConfigMap。
- Secret 不提交到 Git。
- 生产环境镜像中不内置任何密码和 API Key。

---

## 十一、NetworkPolicy

如果集群 CNI 支持 NetworkPolicy，生产环境必须开启默认隔离。

允许的访问关系：

```text
Ingress Controller -> zify-nginx:80
zify-nginx         -> zify-server:8080
zify-server        -> zify-mysql:3306
zify-server        -> zify-postgres:5432
zify-server        -> zify-redis:6379
zify-server        -> 外部 LLM API / MCP Server / HTTP 工具
```

禁止：

- 其他命名空间 Pod 直接访问 MySQL / PostgreSQL / Redis。
- zify-nginx 直接访问数据库。
- MySQL / PostgreSQL / Redis 对集群外暴露端口。

---

## 十二、备份与恢复

### 12.1 备份对象

| 对象 | 方式 | 频率 | 保留 |
|---|---|---:|---:|
| MySQL | `mysqldump` 或物理备份 | 每日 | 14 天 |
| PostgreSQL | `pg_dump` 或物理备份 | 每日 | 14 天 |
| 上传文件 PVC | 打包增量或存储快照 | 每日 | 14 天 |

### 12.2 备份存储

备份不能只存放在同一个 Pod 或同一个数据库 PVC 内。

可选目标：

- 公司内部 NAS。
- 集群外部备份盘。
- S3 兼容对象存储。
- 专用 backup PVC，且该 PVC 需要被集群备份系统保护。

### 12.3 恢复要求

必须保留恢复步骤文档：

```text
1. 停止 zify-server
2. 恢复 MySQL
3. 恢复 PostgreSQL
4. 恢复 /data/uploads
5. 启动 zify-server
6. 抽样验证 Agent、对话、知识库检索、工作流运行
```

每月至少做一次手工恢复演练。

---

## 十三、Docker Compose（开发 / 测试）

Docker Compose 只用于本地开发和测试，不作为生产部署方式。

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
    healthcheck:
      test: ["CMD", "mysqladmin", "ping", "-h", "localhost"]
      interval: 10s
      timeout: 5s
      retries: 10

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
    build:
      context: .
      dockerfile: Dockerfile    # 项目根目录 Dockerfile：mvn package -> 复制 zify-app/target/*.jar
    ports:
      - "8080:8080"
    environment:
      SPRING_PROFILES_ACTIVE: dev
      MYSQL_HOST: mysql
      PG_HOST: postgres
      REDIS_HOST: redis
      UPLOAD_DIR: /data/uploads
    volumes:
      - uploads_data:/data/uploads
    depends_on:
      - mysql
      - postgres
      - redis

  nginx:
    image: nginx:alpine
    ports:
      - "80:80"
    volumes:
      - ./zify-web/dist:/usr/share/nginx/html:ro    # zify-web 的 Vite 构建产物
      - ./nginx.conf:/etc/nginx/conf.d/default.conf:ro
    depends_on:
      - zify-server

volumes:
  mysql_data:
  pg_data:
  redis_data:
  uploads_data:
```

---

## 十四、资源估算

50 人内部使用的初始资源：

| 组件 | requests CPU | requests 内存 | limits CPU | limits 内存 | PVC |
|---|---:|---:|---:|---:|---:|
| zify-server | 1C | 2Gi | 2C | 4Gi | uploads 20Gi |
| zify-nginx | 100m | 128Mi | 500m | 512Mi | - |
| MySQL | 1C | 2Gi | 2C | 4Gi | 50Gi |
| PostgreSQL + pgvector | 1C | 2Gi | 2C | 4Gi | 100Gi |
| Redis | 100m | 256Mi | 500m | 1Gi | 5Gi |

建议节点规格：

```text
最低：8C16G
更稳妥：8C32G
```

说明：

- LLM 调用等待期间 CPU 消耗低，但文档解析、向量化、PDF 处理会消耗 CPU 和内存。
- PostgreSQL 磁盘大小取决于知识库文档量和 embedding 维度，100Gi 是初始值，不是上限。
- 需要配合监控实际调整 requests/limits。

---

## 十五、扩展前置条件

### 15.1 zify-server 扩多副本前必须完成

- Quartz 改为 JDBC 集群模式，或增加分布式锁，避免 Cron 重复触发。
- 上传文件迁移到 MinIO / S3 / RWX 共享存储。
- SSE 连接状态不依赖单 Pod 内存。
- 异步任务状态可从数据库恢复。
- 接口限流从单实例内存迁移到 Redis。
- Provider 并发控制从单 Pod 计数迁移到 Redis 或集中式限流。

### 15.2 数据库高可用前置条件

50 人阶段可以接受单节点数据库，但必须有备份。

当出现以下情况时再考虑主从或托管数据库：

- 数据恢复时间要求低于 1 小时。
- 数据丢失容忍度接近 0。
- 团队无法接受数据库维护窗口。
- 知识库数据量明显增长，单节点磁盘或查询性能吃紧。

### 15.3 对象存储前置条件

以下任一条件满足时，引入 MinIO / S3：

- zify-server 需要多副本。
- 上传文件总量超过单 PVC 容量规划。
- 需要文件生命周期管理。
- 需要跨节点稳定访问原始文档。
