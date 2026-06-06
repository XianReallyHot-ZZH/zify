# Zify 核心数据模型

> 梳理一期全部功能的核心数据表和关系。只列表名和关系，不展开字段。
> 字段细节和建表规范见 `10-zify-database-spec.md`。

---

## 一、按模块分表

### model 模块

| 表 | 说明 |
|----|------|
| `model_provider` | Provider 配置（OpenAI / Anthropic / Ollama 等），含 API Key、Endpoint |

### tool 模块

| 表 | 说明 |
|----|------|
| `tool` | 工具定义（HTTP 工具、Workflow-as-Tool） |

### agent 模块

| 表 | 说明 |
|----|------|
| `agent` | Agent 主表（名称、类型 REACT/WORKFLOW、状态、绑定的模型 ID） |
| `agent_tool` | agent N:N tool 关联表 |
| `agent_knowledge` | agent N:N knowledge 关联表 |

### knowledge 模块

| 表 | 说明 |
|----|------|
| `knowledge` | 知识库 |
| `document` | 文档元数据 |
| `document_chunk` | 文档分块，存 pgvector（VECTOR(1536)） |
| `document_parse_log` | 文档解析日志（大表） |

### workflow 模块

| 表 | 说明 |
|----|------|
| `workflow` | 工作流定义 |
| `workflow_node` | 工作流节点（LLM / If-Else / HTTP Request 等） |
| `workflow_edge` | 节点间连线 |
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
| `trigger` | 触发器定义（Webhook / Cron），绑定工作流 |
| `trigger_log` | 触发执行日志（大表） |

---

## 二、核心关系

### agent 相关

```
model_provider  1:N   agent                   Agent 选用某个 Provider
agent           N:N   tool                    通过 agent_tool 关联
agent           N:N   knowledge               通过 agent_knowledge 关联
agent           1:1   workflow                 WORKFLOW 类型 Agent 绑定一个工作流
```

### knowledge 相关

```
knowledge      1:N   document                 知识库下多个文档
document       1:N   document_chunk           文档分块（存 pgvector）
document       1:N   document_parse_log       解析日志
```

### workflow 相关

```
workflow       1:N   workflow_node            工作流包含多个节点
workflow       1:N   workflow_edge            节点间连线
workflow_run   1:N   workflow_node_run        单次运行中各节点的执行记录
```

### chat 相关

```
agent          1:N   conversation             一个 Agent 多个会话
conversation   1:N   message                  一个会话多条消息
conversation   1:N   tool_call_log            对话中的工具调用记录
```

### trigger 相关

```
workflow       1:N   trigger                  工作流上的触发器配置
trigger        1:N   trigger_log              触发执行日志
```

---

## 三、ER 关系图

```
                        ┌─────────────────┐
                        │  model_provider  │
                        └────────┬────────┘
                                 │ 1:N
                                 ▼
┌─────────────┐          ┌──────────────┐          ┌───────────┐
│  tool       │◄─── N:N ─►│    agent     │─── N:1 ──►│ workflow  │
└──────┬──────┘          └──┬───┬───────┘          └─────┬─────┘
       │                    │   │                        │ 1:N
       │                    │   ▼                        ▼
       │                    │  conversation        ┌──────────────┐
       │                    │   │                  │ workflow_node│
       │                    │   │ 1:N              │ workflow_edge│
       │                    │   ▼                  └──────┬───────┘
       │              ┌────┴─────────┐                    │
       │              │   message    │              ┌─────┴──────────┐
       │              └──────────────┘              │ workflow_run   │
       │                                            │ workflow_node_run│
       │ tool_call_log ◄── N:1 ──────────────────────└────────────────┘
       │
       │         ┌───────────┐          ┌─────────────────┐
       └─ N:N ──►│  knowledge│─── 1:N ──►│    document     │
                  └───────────┘          └────┬────────────┘
                                             │ 1:N
                                     ┌───────┴────────────┐
                                     │ document_chunk     │  ← pgvector
                                     │ document_parse_log │
                                     └────────────────────┘

                                             ┌──────────────┐
                                             │   trigger    │─── 1:N ──► trigger_log
                                             └──────────────┘
                                                  │ N:1
                                                  ▼
                                             workflow
```

---

## 四、表清单汇总

共 **20 张表**：

| # | 表名 | 所属模块 | 存储 | 大表 |
|---|------|---------|------|------|
| 1 | `model_provider` | model | MySQL | |
| 2 | `tool` | tool | MySQL | |
| 3 | `agent` | agent | MySQL | |
| 4 | `agent_tool` | agent | MySQL | 关联表 |
| 5 | `agent_knowledge` | agent | MySQL | 关联表 |
| 6 | `knowledge` | knowledge | MySQL | |
| 7 | `document` | knowledge | MySQL | |
| 8 | `document_chunk` | knowledge | pgvector | |
| 9 | `document_parse_log` | knowledge | MySQL | ✓ |
| 10 | `workflow` | workflow | MySQL | |
| 11 | `workflow_node` | workflow | MySQL | |
| 12 | `workflow_edge` | workflow | MySQL | |
| 13 | `workflow_run` | workflow | MySQL | ✓ |
| 14 | `workflow_node_run` | workflow | MySQL | ✓ |
| 15 | `conversation` | chat | MySQL | |
| 16 | `message` | chat | MySQL | ✓ |
| 17 | `tool_call_log` | engine | MySQL | ✓ |
| 18 | `trigger` | trigger | MySQL | |
| 19 | `trigger_log` | trigger | MySQL | ✓ |
| 20 | *(agent 关联表已计入 #4 #5)* | | | |

MySQL 16 张业务表 + 2 张关联表 + pgvector 1 张 + 大表日志 6 张。
