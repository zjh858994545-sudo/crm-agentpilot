# Demo Script

## 准备

推荐方式：

```powershell
.\scripts\start-full-demo.ps1
.\scripts\smoke-demo.ps1 -RunDemo
```

停止演示进程：

```powershell
.\scripts\stop-full-demo.ps1
```

手动方式：

```powershell
docker compose up -d
cd backend
mvn spring-boot:run
cd ..\frontend
npm run dev
```

打开 http://localhost:5173。

已验证的 Docker 依赖：

- `agentpilot-postgres`：`pgvector/pgvector:pg16`
- `agentpilot-redis`：`redis:7.2-alpine`
- `agentpilot-kafka`：`apache/kafka:3.7.2`

如果只想演示 API 链路：

```powershell
.\scripts\smoke-demo.ps1
.\scripts\demo-api.ps1
```

无 Docker 演示方式：

```powershell
.\scripts\start-demo-backend.ps1
```

另开一个 PowerShell：

```powershell
$env:AGENTPILOT_BASE_URL="http://localhost:18080"
.\scripts\demo-api.ps1
```

## 场景 1：今日优先跟进客户

页面：商机推荐 或 Agent 工作台

Prompt：

```text
今天我应该优先跟进哪些客户？请给出推荐理由和下一步动作。
```

预期：

- Agent 调用 `rankLeads`
- 返回 Top 商机、分数、优先级、推荐原因、下一步动作
- 运行审计页能看到 `agent_run` 和 `agent_tool_call`

讲法：

> 这里不是让模型凭感觉排序，而是把 CRM 数据、套餐到期、最近联系时间、异议、客户等级等因素组合成可解释评分，再交给 Agent 生成销售可执行的话术。

## 场景 2：客户 360 跟进策略

页面：Agent 工作台

Prompt：

```text
帮我分析一下美家房产这个客户，明天应该怎么跟进？
```

预期工具链：

- `queryCustomerProfile`
- `queryContactHistory`
- `searchKnowledge`

预期回答：

- 客户当前状态
- 主要风险与机会
- 跟进话术
- 基于知识库的引用

讲法：

> CRM 事实来自 CRM 工具，销售 SOP 来自 RAG，Agent 只负责组织决策和表达，避免凭空编造客户信息。

## 场景 3：创建跟进任务

页面：Agent 工作台

Prompt：

```text
帮我创建明天上午10点跟进美家房产续费的任务。
```

预期：

- Agent 识别这是 CRM 写操作
- 返回 `confirmation_required`
- 前端展示确认卡片
- 点击确认后才写入 `crm_task`

讲法：

> 这是项目最重要的安全边界：Agent 可以提出写入建议，但不能绕过人工确认直接改 CRM 数据。确认记录也会用于审计和幂等。

## 场景 4：知识库检索与问答

页面：知识库

问题：

```text
客户嫌套餐贵并担心续费效果时，销售应该怎么回复？
```

预期：

- 查询改写
- 混合检索返回知识分块
- 答案带引用
- 无关问题触发拒答

讲法：

> 这里展示的是 RAG 的工程完整性：导入、分块、检索、引用、拒答都有落库和可观测记录。

## 场景 5：呼叫中心摘要与质检

页面：呼叫中心

通话文本：

```text
客户说套餐有点贵，担心续费后没有效果。销售表示可以帮客户争取优惠，并说明会在明天上午提供上月曝光数据和同行案例，但不会承诺一定成交。
```

预期：

- 生成结构化摘要
- 识别客户意向、异议、下一步动作
- 质检检索规则知识库
- 生成联系记录 confirmation，确认后写入 CRM

讲法：

> 呼叫中心场景展示了 Agent 不只是聊天，还能承接真实 CRM 售后动作：摘要、质检、记录写入，但仍然遵守确认机制。

## 场景 6：评测报告

页面：评测报告

点击“运行评测”。

预期指标：

- RAG Recall@5
- Citation Hit Rate
- Refusal Accuracy
- Tool Calling Success Rate
- Write Confirmation Coverage
- Average Latency
- P95 Latency

讲法：

> 评测指标来自 JSONL 用例真实执行，面试时可以打开 `eval` 目录和生成的报告，说明这个 Agent 是可测试、可回归的。

## 工程化追问：事件与可观测性

页面：Dashboard 或 Swagger UI

接口：

```text
GET /api/events/status
GET /actuator/health
GET /v3/api-docs
```

讲法：

> Agent 每次运行、每次工具调用、以及确认后的 CRM 任务创建都会经过事件发布层。为了本地演示稳定，默认是 log-only；如果设置 `AGENT_EVENTS_KAFKA_ENABLED=true`，同一套事件会发布到 Kafka topic，供后续审计、告警、离线评测或运营看板消费。
