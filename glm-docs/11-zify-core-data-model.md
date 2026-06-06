# Zify 核心数据模型

> 梳理一期全部功能的核心数据表和关系。只列表名和关系，不展开字段。
> 字段细节和建表规范见 `10-zify-database-spec.md`。

---

## 一、按模块分表

### model 模块

| 表 | 说明 |
|----|------|
| `model_provider` | 模型供应商连接配置（OpenAI / Anthropic / OpenAI 兼容服务等） |
| `model` | 可用模型配置（LLM / Embedding / Rerank 预留） |

### tool 模块

| 表 | 说明 |
|----|------|
| `mcp_server` | MCP Server 连接配置 |
| `tool` | 统一工具定义（MCP 工具、HTTP 工具、Workflow-as-Tool） |

### agent 模块

| 表 | 说明 |
|----|------|
| `agent` | Agent 主表（REACT / WORKFLOW） |
| `agent_tool` | Agent 与工具的 N:N 关联表 |
| `agent_knowledge` | Agent 与知识库的 N:N 关联表 |

### knowledge 模块

| 表 | 说明 |
|----|------|
| `knowledge` | 知识库 |
| `document` | 文档元数据 |
| `document_chunk` | 文档分块和 embedding，存 PostgreSQL + pgvector |
| `document_parse_log` | 文档解析日志（大表） |

### workflow 模块

| 表 | 说明 |
|----|------|
| `workflow` | 工作流定义 |
| `workflow_node` | 工作流节点（Start / End / LLM / If-Else / HTTP Request / Knowledge Retrieval / Code / Tool / Answer） |
| `workflow_edge` | 工作流节点连线 |
| `workflow_run` | 工作流运行记录（大表） |
| `workflow_node_run` | 工作流节点运行记录（大表） |

### chat 模块

| 表 | 说明 |
|----|------|
| `conversation` | 会话 |
| `message` | 消息（大表） |

### engine 模块

| 表 | 说明 |
|----|------|
| `tool_call_log` | 工具调用日志（大表） |

### trigger 模块

| 表 | 说明 |
|----|------|
| `trigger` | 触发器定义（Webhook / Cron） |
| `trigger_log` | 触发执行日志（大表） |

---

## 二、核心关系

### model 相关

```text
model_provider  1:N   model                   一个供应商下配置多个可用模型
model           1:N   agent                   Agent 选用一个 LLM 模型
model           1:N   knowledge               知识库选用一个 Embedding 模型
model           1:N   workflow_node           LLM 节点选用一个 LLM 模型（逻辑引用）
```

### tool 相关

```text
mcp_server      1:N   tool                    一个 MCP Server 自动发现多个 MCP 工具
workflow        0..1:1 tool                    已发布工作流可自动注册为 Workflow-as-Tool
tool            1:N   tool_call_log           一个工具可产生多条调用日志
```

### agent 相关

```text
agent           N:N   tool                    通过 agent_tool 关联
agent           N:N   knowledge               通过 agent_knowledge 关联
agent           N:1   workflow                 仅 WORKFLOW 类型 Agent 绑定一个工作流；同一工作流可被多个 Agent 复用
agent           1:N   conversation             一个 Agent 多个会话
```

### knowledge 相关

```text
knowledge       1:N   document                 一个知识库下多个文档
document        1:N   document_chunk           一个文档切分为多个 chunk
document        1:N   document_parse_log       一个文档可产生多条解析日志
knowledge       1:N   workflow_node            Knowledge Retrieval 节点引用知识库（逻辑引用）
```

### workflow 相关

```text
workflow        1:N   workflow_node            一个工作流包含多个节点
workflow        1:N   workflow_edge            一个工作流包含多条连线
workflow_node   1:N   workflow_edge            节点作为连线起点或终点
workflow        1:N   workflow_run             一个工作流可运行多次
workflow_run    1:N   workflow_node_run        单次运行包含多条节点运行记录
workflow_node   1:N   workflow_node_run        一个节点可产生多条节点运行记录
tool            1:N   workflow_node            Tool 节点引用统一工具（逻辑引用）
```

### chat 相关

```text
conversation    1:N   message                  一个会话多条消息
message         1:N   tool_call_log            对话消息触发的工具调用日志
```

### engine 运行日志相关

```text
workflow_run       1:N   tool_call_log         工作流运行触发的工具调用日志
workflow_node_run  1:N   tool_call_log         工作流 Tool 节点触发的工具调用日志
```

### trigger 相关

```text
workflow        1:N   trigger                  一个工作流可配置多个触发器
trigger         1:N   trigger_log              一个触发器可产生多条触发日志
trigger_log     0..1:1 workflow_run            成功触发工作流时关联对应运行记录
```

---

## 三、ER 关系图

```text
model_provider
  └─ 1:N model
        ├─ 1:N agent
        ├─ 1:N knowledge
        └─ 1:N workflow_node（LLM 节点逻辑引用）

agent
  ├─ N:N tool       via agent_tool
  ├─ N:N knowledge  via agent_knowledge
  ├─ N:1 workflow   （仅 WORKFLOW Agent）
  └─ 1:N conversation
        └─ 1:N message
              └─ 1:N tool_call_log

mcp_server
  └─ 1:N tool

workflow
  ├─ 0..1:1 tool    （Workflow-as-Tool）
  ├─ 1:N workflow_node
  │     ├─ 1:N workflow_edge（作为 source 或 target）
  │     └─ 1:N workflow_node_run
  ├─ 1:N workflow_edge
  ├─ 1:N workflow_run
  │     ├─ 1:N workflow_node_run
  │     └─ 1:N tool_call_log
  └─ 1:N trigger
        └─ 1:N trigger_log
              └─ 0..1:1 workflow_run

knowledge
  └─ 1:N document
        ├─ 1:N document_chunk      （PostgreSQL + pgvector）
        └─ 1:N document_parse_log

tool
  ├─ 1:N tool_call_log
  └─ 1:N workflow_node             （Tool 节点逻辑引用）
```

---

## 四、表清单汇总

共 **21 张表**：

| # | 表名 | 所属模块 | 存储 | 大表 |
|---|------|---------|------|------|
| 1 | `model_provider` | model | MySQL | |
| 2 | `model` | model | MySQL | |
| 3 | `mcp_server` | tool | MySQL | |
| 4 | `tool` | tool | MySQL | |
| 5 | `agent` | agent | MySQL | |
| 6 | `agent_tool` | agent | MySQL | 关联表 |
| 7 | `agent_knowledge` | agent | MySQL | 关联表 |
| 8 | `knowledge` | knowledge | MySQL | |
| 9 | `document` | knowledge | MySQL | |
| 10 | `document_chunk` | knowledge | PostgreSQL + pgvector | |
| 11 | `document_parse_log` | knowledge | MySQL | ✓ |
| 12 | `workflow` | workflow | MySQL | |
| 13 | `workflow_node` | workflow | MySQL | |
| 14 | `workflow_edge` | workflow | MySQL | |
| 15 | `workflow_run` | workflow | MySQL | ✓ |
| 16 | `workflow_node_run` | workflow | MySQL | ✓ |
| 17 | `conversation` | chat | MySQL | |
| 18 | `message` | chat | MySQL | ✓ |
| 19 | `tool_call_log` | engine | MySQL | ✓ |
| 20 | `trigger` | trigger | MySQL | |
| 21 | `trigger_log` | trigger | MySQL | ✓ |

MySQL 共 **20 张表**（其中 2 张关联表、6 张大表），PostgreSQL + pgvector 共 **1 张表**。

---

## 五、建模边界说明

- 一期不建用户、组织、权限、工作区相关表；需要预留用户标识时在具体表字段设计中处理。
- `workflow_node` 对模型、知识库、工具的引用属于节点配置语义；是否拆成独立关联表，由字段设计阶段按查询需求决定。
- `tool_call_log` 是统一工具系统的运行日志，可由对话引擎或工作流节点触发，不只属于对话。
- `document_chunk` 独立存 PostgreSQL + pgvector；业务元数据仍以 MySQL 表为准。
