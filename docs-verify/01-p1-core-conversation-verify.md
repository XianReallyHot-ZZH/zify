# P1 核心对话闭环 — 端到端验证案例

> 用一个带记忆锚点的虚构场景，覆盖 P1 全部功能点，逐条压满 DoD。
> 验证环境：DeepSeek（`OPENAI_COMPATIBLE`，ACTIVE）+ `deepseek-v4-pro`（LLM，启用）。
> 前端 `http://localhost:5173`，后端 `http://localhost:8080`，日志 `zify-app/target/app.log`。

---

## 场景：「Zify 智能咖啡机 X1」客服助手

让 Agent 扮演一款虚构产品「Zify 智能咖啡机 X1」的客服，并把几条**固定事实**写进 System Prompt。
后续多轮对话里只要它「记得」这些事实，就证明上下文 / 历史生效了。

**System Prompt（创建 Agent 时粘贴）**：

```text
你是「Zify 智能咖啡机 X1」的官方客服助手，名叫小咖。
产品固定信息，回答时必须一致：
- 型号：Zify 智能咖啡机 X1
- 售价：1999 元
- 保修：整机 2 年，磨豆模块 3 年
- 水箱容量：1.8L
- 支持：美式、拿铁、卡布奇诺、手冲
回答风格：简洁、专业、口语化，每条不超过 3 句。
```

---

## 阶段 1 — Agent 全生命周期

| # | 操作 | 预期 | 验证 |
|---|---|---|---|
| 1.1 | `/agents` → 新建 Agent：名字 `咖啡机客服`、类型 REACT、System Prompt 见上、模型选 `deepseek-v4-pro` | 4 步表单走完，保存成功，跳回列表 | 列表出现该卡片 |
| 1.2 | 列表搜索 `咖啡`、状态筛 `启用` | 命中 | 卡片显示 |
| 1.3 | 编辑该 Agent，把名字改成 `咖啡机客服-小咖` | 保存成功 | 名称更新；类型不可改（灰） |
| 1.4 | 点「禁用」 | 状态标签变灰 | DB：`agent.status='INACTIVE'` |
| 1.5 | 再点「启用」恢复 | 状态恢复绿 | — |

---

## 阶段 2 — 对话核心闭环（DoD #1 / #3）

| # | 操作 | 预期 | 验证 |
|---|---|---|---|
| 2.1 | `/`（对话页）→ 新建对话 → 弹窗选 `咖啡机客服` | 进入空会话，右栏头部显示 Agent 名 | DB：`conversation` 新增 1 行，`message_count=0`，`title='咖啡机客服'` |
| 2.2 | 发「X1 多少钱？保修多久？」 | **流式逐字**出现；回答含 1999 元 / 整机 2 年（System Prompt 生效） | UI 看 token 逐块；DB：`message` 多 2 行（USER + ASSISTANT），`conversation.message_count=2`，`last_message_at` 更新 |
| 2.3 | 追问「那它的水箱多大？刚才说的保修还记得吗？」 | 回答 1.8L **且复述保修**（证明历史上文被带入） | 看回答是否引用了第 2.2 轮的事实 |
| 2.4 | 刷新浏览器（F5）→ 重新进该会话 | 历史消息完整保留，顺序正确 | DB 与 UI 一致 |
| 2.5 | 左栏会话列表 | 该会话排在最上（按 `last_message_at` 倒序） | 排序正确 |

---

## 阶段 3 — 中断与取消上游（DoD #2，关键架构点）

| # | 操作 | 预期 | 验证 |
|---|---|---|---|
| 3.1 | 发一个稍长问题（如「详细介绍 X1 支持的四种咖啡做法」） | 流式输出开始 | — |
| 3.2 | **输出到一半点「停止」** | 立刻停止追加；已产出的部分文本被**落库**为 ASSISTANT（`finishReason=CANCELLED`） | DB：该 ASSISTANT 行 `metadata.finishReason='CANCELLED'`，内容是截断后的部分；`app.log` 出现 `status=cancelled` |
| 3.3 | 再发「继续」 | 它从中断处继续（或重述） | 正常流式 |

> 这个案例专门压「**SSE 断开必须取消上游 LLM**」这条硬规则。停止后 DeepSeek 那边的请求应被 dispose，不再计费 / 占并发。

---

## 阶段 4 — 上下文管理（DoD #6）

默认配置下这两条很难自然触发（`default-window=128000`），建议**临时调小配置快速验证，验完改回**。

编辑 `zify-app/src/main/resources/application.yml`：

```yaml
zify:
  chat:
    context:
      default-window: 6000      # 原 128000
      budget-threshold: 0.5     # 原 0.75
      compaction-batch: 2       # 原 6
      summary-overhead-tokens: 200  # 原 2000
      max-input-tokens: 300     # 原 30000（方便单条拦截）
```

改完**重启后端**（`mvn clean install -DskipTests` 重新打 jar，或 IDE 重启）。

| # | 操作 | 预期 | 验证 |
|---|---|---|---|
| 4.1 | **单条拦截**：粘贴一段 ~400 字的文本发送 | 直接被拒，前端提示「消息内容过长」(`MESSAGE_TOO_LONG`) | DB：**没有**新 USER 消息（发送被拦截，未落库） |
| 4.2 | **摘要压缩**：在同一会话连续发 4–6 条中等消息（每条 100–200 字，正常提问） | 几轮后触发压缩 | DB：`conversation.summary_text` 被写入一段摘要，`summary_covered_message_id` 指向某条消息；`app.log` 该轮有**两次** `event=llm_call`（一次生成摘要、一次正式回复） |
| 4.3 | 压缩后继续提问，问「我前面问过 X1 的什么？」 | 仍能基于摘要回答大致内容（证明压缩保留了要点） | 回答不离谱 |

> 验完务必把配置改回原值：`default-window: 128000 / budget-threshold: 0.75 / compaction-batch: 6 / summary-overhead-tokens: 2000 / max-input-tokens: 30000`。

### 阶段 4 — 详细测试步骤（可复制）

> 触发阈值（带「咖啡机客服」system prompt ≈150 token）：
> `budget = 6000 − 150(system) − 4096(预留输出) − 200 = 1454`
> - 摘要压缩：活窗口历史 token > `1454 × 0.5 ≈ 727`（约 3 轮对话）
> - 单条拦截：单条消息估算 token > `300`（约 300 个中文字）

#### 4.1 单条拦截（`MESSAGE_TOO_LONG`）

在任意会话发送下面这段（约 400 字，>300 token）：

```
我想详细了解一下你们这款Zify智能咖啡机X1的全部细节，包括但不限于以下几个方面，请逐一回答：第一，这款机器的完整技术参数，比如功率、电压、尺寸、重量、水泵压力、磨豆机档位数量、蒸汽棒规格；第二，包装箱里附带哪些配件，是否有清洁工具、量勺、奶缸、滤芯、水管；第三，日常清洁维护的具体步骤和周期，除垢用什么牌子的试剂，磨豆模块多久清一次，蒸汽棒每次用完怎么处理；第四，常见故障的自查方法，比如不出水、磨豆卡顿、蒸汽不足、屏幕报错代码分别对应什么问题；第五，耗材和替换件的购买渠道与价格，滤芯多久换一次，磨豆刀盘寿命多长；第六，与手机App连接的具体功能和兼容性，支持哪些智能家居平台，固件怎么升级。麻烦尽量完整一点，我准备下单前研究清楚。
```

**预期**：前端提示「消息内容过长」，**不**出现流式回复。

**验证**：
- DB `message` 表无新增 USER（发送层拦截，未落库）：
  ```sql
  SELECT COUNT(*) FROM zify.message WHERE conversation_id='<会话id>' AND role='USER';
  ```
  数量不增加。
- `app.log` 无 `event=llm_call`（根本没调 LLM）。

> 控制变量：把上面文本截短到约 100 字再发 → 应正常流式（证明是 token 超限拦截，不是别的 bug）。

#### 4.2 摘要压缩触发

**前提**：用**新会话**（保证 `conversation.summary_text` 初始为 NULL）。连续发送下面 5 条（每条 ~150 字），**每条等流式回复完再发下一条**：

```
我打算把这台X1放在办公室茶水间给十几个同事用，每天大概要做三四十杯咖啡，主要都是美式和拿铁。请问这台机器的连续出杯能力怎么样？水箱1.8升够用吗，一天要加几次水？豆仓容量多大，多久加一次豆？另外这么多人的话，清洁维护频率是不是要更高一些？
```

```
另外我们办公室没有专门的维修人员，如果机器出故障我们自己能不能处理？常见的问题比如突然不出咖啡了、磨豆声音变大、屏幕显示错误代码，分别应该怎么排查？保修期内上门维修是怎么收费的，整机两年保修具体覆盖哪些部件，磨豆模块三年保修要不要额外付费？
```

```
我还想了解一下耗电情况，这台机器待机功耗多少，连续工作峰值功率多少？我们办公室电路是普通的，能带得动吗？另外它支持定时开关机吗，能不能设置工作日早上自动预热，下班自动关机省电？App远程控制的话，我能不能在公司外提前把机器打开预热好？
```

```
关于水质的问题，我们这边自来水水质偏硬，长期用会不会结水垢影响机器寿命？需要配净水器吗，还是机器自带滤芯？滤芯多久换一次，大概多少钱？如果用桶装纯净水可以直接倒进水箱吗，水箱有没有最大水位线和最小水位线的标识提醒？
```

```
最后问一下外观和尺寸，这台机器有多重，放在普通办公桌上稳不稳？有没有防滑垫？颜色只有这一种吗？工作时的噪音大不大，磨豆的时候会不会吵到旁边办公的同事？蒸汽棒打奶泡的时候声音呢？晚上加班用会不会显得很吵？
```

**预期**：通常发**第 3 或第 4 条**时触发压缩（活窗口历史 token 累积超过 577）。

**验证**（任一命中即说明触发了压缩）：
1. DB summary 落库（最直接）：
   ```sql
   SELECT LEFT(summary_text,50) AS summary_head, summary_covered_message_id
   FROM zify.conversation WHERE id='<会话id>';
   ```
   触发后 `summary_text` 非空、`summary_covered_message_id` 指向某条 `message.id`；触发前为 NULL。
2. 日志双次 `llm_call`：
   ```bash
   grep "event=llm_call" zify-app/target/app.log | tail
   ```
   触发那一轮有**两次**（一次生成摘要、一次正式回复）。
3. 观察延迟：触发那轮明显比别的轮慢（多了一次摘要 LLM 调用）。

> 发完 5 条还没触发（`summary_text` 仍 NULL），多半是 LLM 回答太短导致历史 token 没攒够——再发 2-3 条长提问即可。

#### 4.3 压缩后记忆验证（基于摘要）

在 4.2 触发压缩**之后**，接着发：

```
我前面问过的内容里，有提到办公室多少人、每天多少杯吗？还问到水质和噪音了吗？帮我把前面聊过的话题简单列一下。
```

**预期**：基于摘要复述前面聊过的要点（人数/杯量、水质、噪音等），即使这些原消息已被压缩进 summary、不再逐条出现在发给 LLM 的 history 里。

**验证**：回答**复述**了 4.2 第 1/4/5 条的要点。若回答完全不知道前面聊过什么（像全新对话）→ 查：
```sql
SELECT LEFT(summary_text,200) FROM zify.conversation WHERE id='<会话id>';
```
确认 summary 是否确实写了要点。

---

## 阶段 5 — 异常与边界

| # | 操作 | 预期 |
|---|---|---|
| 5.1 | 把 Agent 禁用 → 新建对话弹窗里**看不到**它（只列 ACTIVE+REACT） | 选择器为空或不含该 Agent |
| 5.2 | 在某会话中途禁用其 Agent → 再发消息 | 返回 `AGENT_INACTIVE`（业务错误，非 500） |
| 5.3 | 删除 Agent → 它名下的旧会话**仍能打开看历史**，但不能再发消息 | 历史只读（软删 Agent 不级联删会话） |
| 5.4 | 删除一个会话 | 会话 + 其下消息**同时软删** |

> 5.4 的 DB 验证：`conversation.is_deleted=1` 且该 `conversation_id` 下所有 `message.is_deleted=1`。

---

## 数据库对照（任选一条会话抽验）

```sql
-- 会话基本态
SELECT id, title, status, message_count, last_message_at,
       LEFT(summary_text, 40) AS summary_head, summary_covered_message_id
FROM zify.conversation WHERE is_deleted = 0;

-- 某会话的消息流（USER/ASSISTANT 交替，ASSISTANT 带 metadata）
SELECT role, LEFT(content, 30) AS head, metadata, created_at
FROM zify.message
WHERE conversation_id = '<某会话id>' AND is_deleted = 0
ORDER BY created_at;

-- 流式元数据落点（验证 cancel / done）
SELECT role,
       JSON_EXTRACT(metadata, '$.finishReason') AS finish,
       JSON_EXTRACT(metadata, '$.totalTokens')  AS tokens,
       JSON_EXTRACT(metadata, '$.durationMs')   AS ms
FROM zify.message
WHERE conversation_id = '<某会话id>' AND role = 'ASSISTANT';
```

---

## DoD 逐条对照

| DoD | 由哪个阶段验证 |
|---|---|
| ① 创建只绑模型的 REACT Agent | 阶段 1.1 |
| ② 流式回复 + 中断取消上游 | 阶段 2.2（流式）+ 阶段 3（取消） |
| ③ 历史保留、可继续 / 删除 | 阶段 2.3 / 2.4 + 阶段 5.4 |
| ④ 跨模块只走 Facade / 事务不包 LLM / Key 不泄露 | `app.log` 看不到 apiKey；流式调用在事务外（代码已确认） |
| ⑤ message / conversation 用 Keyset、列表无大字段 | 会话列表不返回 content（阶段 2.5 接口） |
| ⑥ 摘要压缩触发 + 单条超限拦截 | 阶段 4.2 + 4.1 |

---

## 建议验证顺序

1. 阶段 1 → 2 → 3（最关键，压取消上游）→ 5：用默认配置一气呵成。
2. 阶段 4 单独开一轮（要改配置 + 重启），验完恢复配置。
