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
- Knowledge RAG：文档导入、语义化分块、阿里云百炼真实 Embedding、pgvector 混合检索、引用、低置信拒答
- Agent Orchestrator：LLM Tool Calling 优先、规则路由兜底、工具注册、运行轨迹、最终回答
- Human Confirmation：写任务、写联系记录等 CRM 写操作必须先生成 confirmation
- Call Center：通话摘要、客户异议抽取、质检、联系记录确认写入
- Evaluation：RAG Recall、引用命中、拒答、工具调用、确认覆盖率、延迟指标
- Frontend Workbench：Dashboard、Agent、客户、商机、知识库、呼叫中心、运行审计、评测页面

## 技术栈

- Backend：Java 17、Spring Boot 3.2、Maven、MyBatis-Plus、Validation、JUnit 5
- Data：PostgreSQL 16、pgvector 镜像、Redis、Kafka、Flyway
- Agent/RAG：阿里云百炼 Qwen、OpenAI-compatible 协议适配、text-embedding-v4、pgvector
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

## 一键启动

面试演示首选全容器版，只需要这一条：

```powershell
.\scripts\start-docker-demo.ps1 -OpenBrowser
```

这个脚本会用 Docker Compose 启动 PostgreSQL/pgvector、Redis、Kafka、后端和前端，自动读取系统环境变量里的模型配置，等待后端健康检查通过，并执行 smoke check。

默认地址：

- Frontend: http://localhost:15173
- Backend health: http://localhost:18080/api/health
- Swagger UI: http://localhost:18080/swagger-ui.html
- Model status: http://localhost:18080/api/model/status

停止：

```powershell
.\scripts\stop-docker-demo.ps1
```

重置数据库和容器卷：

```powershell
.\scripts\stop-docker-demo.ps1 -ResetData
```

如果端口冲突，可以指定端口：

```powershell
.\scripts\start-docker-demo.ps1 -BackendPort 18081 -FrontendPort 15174 -OpenBrowser
```

开发调试时也可以使用本机 Maven/NPM 版本：

```powershell
.\scripts\start-full-demo.ps1
```

启动基础设施：

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

默认支持 deterministic mock mode，方便测试和面试演示稳定复现；当前也已接入阿里云百炼。这里的 `OpenAI-compatible` 指协议兼容，不代表供应商一定是 OpenAI。阿里云百炼 DashScope 使用同一类 `/chat/completions`、`/embeddings` 接口，所以可以复用统一适配层：

```powershell
[Environment]::SetEnvironmentVariable("AGENT_MODEL_PROVIDER", "openai-compatible", "User")
[Environment]::SetEnvironmentVariable("OPENAI_COMPATIBLE_BASE_URL", "https://dashscope.aliyuncs.com/compatible-mode/v1", "User")
[Environment]::SetEnvironmentVariable("OPENAI_COMPATIBLE_API_KEY", "你的 API Key", "User")
[Environment]::SetEnvironmentVariable("OPENAI_COMPATIBLE_CHAT_MODEL", "qwen3.6-flash", "User")
[Environment]::SetEnvironmentVariable("OPENAI_COMPATIBLE_TEMPERATURE", "0.2", "User")
[Environment]::SetEnvironmentVariable("AGENT_EMBEDDING_PROVIDER", "openai-compatible", "User")
[Environment]::SetEnvironmentVariable("OPENAI_COMPATIBLE_EMBEDDING_BASE_URL", "https://dashscope.aliyuncs.com/compatible-mode/v1", "User")
[Environment]::SetEnvironmentVariable("OPENAI_COMPATIBLE_EMBEDDING_API_KEY", "你的 API Key", "User")
[Environment]::SetEnvironmentVariable("OPENAI_COMPATIBLE_EMBEDDING_MODEL", "text-embedding-v4", "User")
[Environment]::SetEnvironmentVariable("OPENAI_COMPATIBLE_EMBEDDING_DIMENSIONS", "1024", "User")
```

设置后重新打开 PowerShell，再运行 `.\scripts\start-docker-demo.ps1`。验证模型连接：

```powershell
curl http://localhost:18080/api/model/status
curl -X POST http://localhost:18080/api/model/chat `
  -H "Content-Type: application/json" `
  -d "{\"prompt\":\"用一句话解释 CRM AI Agent 的价值\"}"
curl -X POST http://localhost:18080/api/model/embedding `
  -H "Content-Type: application/json" `
  -d "{\"text\":\"客户嫌套餐贵，担心续费效果\"}"
```

## Security / Production Switches

本地演示默认是 `permissive`，浏览器和脚本不用额外传 token。要模拟生产权限，把 token 写进用户环境变量后重启 PowerShell：

```powershell
[Environment]::SetEnvironmentVariable("AGENTPILOT_SECURITY_MODE", "strict", "User")
[Environment]::SetEnvironmentVariable("AGENTPILOT_API_TOKEN", "换成一串强随机 token", "User")
```

严格模式下，后端业务 API 需要请求头：

```text
X-AgentPilot-Token: 你的 token
```

前端可以通过构建环境变量 `VITE_AGENTPILOT_API_TOKEN` 注入，也可以在浏览器控制台写入：

```javascript
localStorage.setItem("agentpilot.apiToken", "你的 token")
```

验证生产化能力的接口：

```text
GET  /api/events/status          # 查看 log-only/Kafka 模式与 outboxPending
GET  /api/agent/tools/openai     # 查看 OpenAI-compatible tools schema
POST /api/knowledge/search       # PostgreSQL 下结果 retriever 为 pgvector-hybrid
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
- 已加入 Spring Security API Token 权限层：默认 permissive 方便本地演示，设置 `AGENTPILOT_SECURITY_MODE=strict` 后所有业务 API 需要 `X-AgentPilot-Token`
- 已接入阿里百炼真实 embedding：默认 `text-embedding-v4`、1024 维，写入 PostgreSQL `crm_knowledge_chunk.embedding_vector vector(1024)`，并通过 HNSW 索引和 `pgvector-hybrid` 检索路径跑通
- 已加入 outbox 事件表 `agent_outbox_event`：确认后的 CRM task 事件与业务写入共享事务；Agent run / tool call 审计事件统一落库并按 at-least-once 语义重试分发；默认 log-only，设置 `AGENT_EVENTS_KAFKA_ENABLED=true` 后发布到 Kafka
- Tool Registry 已 schema 化，并通过 `GET /api/agent/tools/openai` 暴露 OpenAI-compatible tools JSON Schema；主 Agent 会优先让 LLM 选择工具，失败或超时再回退规则路由

## 已知限制

- 主 Agent 已接入 LLM Tool Calling 主流程；为了保证演示稳定，模型选工具失败或外部接口超时时会自动回退 deterministic 规则路由。
- RAG 已使用 PostgreSQL pgvector 存储和相似度检索；配置 `AGENT_EMBEDDING_PROVIDER=openai-compatible` 后走阿里百炼 `text-embedding-v4` 真实 embedding。本地/测试仍可回退 deterministic mock，保证用例稳定。
- 事件层已实现应用级 outbox。CRM 写操作事件在确认事务内落库，强调事务-事件一致；Agent run / tool call 是审计型事件，走 at-least-once 落库重试，不声明 exactly-once。生产大规模场景可以继续演进为 outbox + CDC/Debezium。
- 当前鉴权是适合演示和内网工具的 API Token + 方法级权限，不是完整企业级 SSO/JWT/RBAC/多租户权限体系。
- Tool Schema 已能暴露给 OpenAI-compatible function calling，当前已覆盖客户分析、商机推荐、知识检索、产品查询、创建任务、写联系记录、更新商机阶段等主工具；生产化仍需要补更严格的参数校验、权限边界和多轮消歧。
- JSONL 评测集是小规模面试样例，用于验证评测框架，生产化需要扩展真实业务样本。

## 面试讲法

一句话：这个项目解决的是“AI Agent 如何安全进入 CRM 业务系统”的问题。

重点讲三件事：

- Agent 不是直接改库，而是通过工具注册表区分读/写工具，写操作必须 confirmation
- RAG 不只是问答，而是销售 SOP、产品政策、质检规则的可引用业务上下文
- 评测不是手写指标，而是读取 JSONL 用例真实调用链路，输出报告，能量化 Recall、引用、拒答、工具调用和确认覆盖率
