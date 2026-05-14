# CRM-AgentPilot

CRM-AgentPilot 是一个面向本地生活销售 CRM 场景的全栈 AI Agent 项目，目标不是做一个普通聊天机器人，而是做一个能进入 CRM 作业流的销售助手：它能读取客户与商机数据、调用可审计工具、检索销售 SOP 知识、生成跟进策略、对写 CRM 的动作做人工确认，并输出可重复运行的评测报告。

## 目标岗位匹配

岗位要求是 CRM AI Agent 的全栈架构设计与能力实现。本项目重点展示：

- Java/Spring Boot 后端工程能力
- 数据库建模、MyBatis-Plus、Flyway 迁移与种子数据
- RAG 检索、知识分块、查询改写、引用与拒答
- Agent 工具调用、读写工具隔离、人工确认与审计
- CRM 业务理解：客户 360、商机优先级、续费跟进、呼叫摘要、质检
- 前端工作台与可演示闭环
- JSONL 评测集与自动报告

## 核心功能

- CRM Core：销售、客户、商机、联系记录、任务、产品套餐
- Lead Scoring：可解释商机排序，输出分数、优先级、推荐原因和下一步动作
- Knowledge RAG：文档导入、语义化分块、Mock Embedding、混合检索、引用、低置信拒答
- Agent Orchestrator：意图识别、工具注册、工具调用、运行轨迹、最终回答
- Human Confirmation：写任务、写联系记录等 CRM 写操作必须先生成 confirmation
- Call Center：通话摘要、客户异议抽取、质检、联系记录确认写入
- Evaluation：RAG Recall、引用命中、拒答、工具调用、确认覆盖率、延迟指标
- Frontend Workbench：Dashboard、Agent、客户、商机、知识库、呼叫中心、运行审计、评测页面

## 技术栈

- Backend：Java 17、Spring Boot 3.2、Maven、MyBatis-Plus、Validation、JUnit 5
- Data：PostgreSQL 16、pgvector 镜像、Redis、Kafka、Flyway
- Agent/RAG：本地 Mock LLM、Mock Embedding、OpenAI-compatible 适配预留
- Frontend：React 18、TypeScript、Vite、Ant Design、Axios、React Router
- DevOps：Docker Compose、GitHub Actions、PowerShell 演示脚本

## 架构

```text
React Workbench
  -> Spring Boot REST API
      -> CRM Core Services
      -> Lead Scoring
      -> RAG Retrieval
      -> Agent Orchestrator
      -> Tool Registry + Guardrails
      -> Confirmation Service
      -> Call Center Service
      -> Evaluation Runner
  -> PostgreSQL / pgvector
  -> Redis
  -> Kafka
```

## 本地启动

一键演示启动：

```powershell
.\scripts\start-full-demo.ps1
```

该脚本会启动 Docker 依赖、后端和前端，并把日志写入 `.demo-logs/`。停止演示进程：

```powershell
.\scripts\stop-full-demo.ps1
```

如果 `8080` 或 `5173` 已被本机其他程序占用，脚本会自动选择空闲端口，并在控制台打印实际 Frontend、Backend、Swagger 地址；`smoke-demo.ps1` 会自动读取 `.demo-logs/backend.url`。

启动依赖：

```powershell
docker compose up -d
```

启动后端：

```powershell
cd backend
mvn spring-boot:run
```

启动前端：

```powershell
cd frontend
npm install
npm run dev
```

访问：

- Frontend: http://localhost:5173
- Backend health: http://localhost:8080/api/health
- Swagger UI: http://localhost:8080/swagger-ui.html
- Actuator health: http://localhost:8080/actuator/health
- Model status: http://localhost:8080/api/model/status
- Events status: http://localhost:8080/api/events/status

## Real Model Configuration

默认使用 deterministic mock mode，方便测试和面试演示稳定复现。如果要切到真实 OpenAI-compatible 模型，设置环境变量：

```powershell
$env:AGENT_MODEL_PROVIDER="openai-compatible"
$env:OPENAI_COMPATIBLE_BASE_URL="https://api.openai.com/v1"
$env:OPENAI_COMPATIBLE_API_KEY="你的 API Key"
$env:OPENAI_COMPATIBLE_CHAT_MODEL="gpt-4o-mini"
```

验证模型连接：

```powershell
curl http://localhost:8080/api/model/status
curl -X POST http://localhost:8080/api/model/chat `
  -H "Content-Type: application/json" `
  -d "{\"prompt\":\"用一句话解释 CRM AI Agent 的价值\"}"
```

## 演示脚本

后端启动后，先跑 smoke 验收：

```powershell
.\scripts\smoke-demo.ps1
```

后端启动后，可以直接运行完整 API 演示：

```powershell
.\scripts\demo-api.ps1
```

如果当前机器没有 Docker，也可以用 H2 内存库启动演示后端：

```powershell
.\scripts\start-demo-backend.ps1
```

另开一个 PowerShell：

```powershell
$env:AGENTPILOT_BASE_URL="http://localhost:18080"
.\scripts\demo-api.ps1
```

演示包含：

1. 健康检查
2. 商机优先级推荐
3. Agent 读取 CRM 与 RAG 生成客户跟进策略
4. Agent 生成 CRM 写操作 confirmation
5. 人工确认后写入 CRM 任务
6. RAG 问答与引用
7. 呼叫摘要
8. 通话质检
9. 评测报告生成

检查本机环境：

```powershell
.\scripts\check-local.ps1
```

## 测试与构建

后端测试：

```powershell
cd backend
mvn "-Dmaven.repo.local=.\.m2repo" test
```

前端构建：

```powershell
cd frontend
npm run build
```

评测：

```powershell
.\scripts\run-eval.ps1
```

## 当前完成状态

- CRM、商机推荐、RAG、Agent、确认流、呼叫中心、评测后端已实现
- 前端所有核心页面已接真实 API
- 后端测试通过
- 前端生产构建通过
- Docker Desktop 已恢复到 F 盘，Docker Compose 已验证：PostgreSQL/pgvector、Redis、Kafka 均可 healthy 启动
- 后端已在 Docker Postgres/Redis 环境下跑通完整 API 演示链路
- 已加入 OpenAI-compatible 模型适配、Swagger UI、Actuator 基础端点、`X-Trace-Id` 请求追踪
- 已加入可选 Kafka 事件发布层：`agent-run-events`、`agent-tool-call-events`、`crm-task-events`，默认 log-only，设置 `AGENT_EVENTS_KAFKA_ENABLED=true` 后可发布到 Kafka

## 已知限制

- 主 Agent 默认使用 deterministic mock 路由，保证面试演示和评测可复现；配置真实模型后，客户分析链路会使用 `ChatModelClient` 生成最终策略表达，其他意图仍以规则路由为主。
- RAG 当前使用关键词检索和 deterministic mock embedding，PostgreSQL 使用 pgvector 镜像作为后续扩展目标，当前 schema 仍为 H2 兼容设计。
- Kafka 事件层默认 log-only；开启 Kafka 后是演示级事件发布，生产环境应升级为 outbox/CDC 保证事务与事件一致性。
- 当前没有完整鉴权系统，confirmation 接口要求显式 `userId` 以保留审计字段，生产化应接 Spring Security/JWT 和客户字段级权限。
- JSONL 评测集是小规模面试样例，用于验证评测框架，生产化需要扩展真实业务样本。

## 面试讲法

一句话：这个项目解决的是“AI Agent 如何安全进入 CRM 业务系统”的问题。

重点讲三件事：

- Agent 不是直接改库，而是通过工具注册表区分读/写工具，写操作必须 confirmation
- RAG 不只是问答，而是销售 SOP、产品政策、质检规则的可引用业务上下文
- 评测不是手写指标，而是读取 JSONL 用例真实调用链路，输出报告，能量化 Recall、引用、拒答、工具调用和确认覆盖率
