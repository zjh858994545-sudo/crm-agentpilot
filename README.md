# CRM-AgentPilot

CRM-AgentPilot 是一个面向本地生活销售 CRM 场景的全栈 AI Agent 工作台。它不是普通聊天机器人，而是把客户、商机、销售知识库、AI 建议、CRM 写入确认、运行审计和质量评估串成一条销售作业流：AI 可以读数据、给建议、生成行动草案，但所有写 CRM 的动作都必须先进入确认流，由用户确认后才真正落库。

## 产品定位

本项目面向三类使用者：

- 销售：查看今日优先客户、客户 360、商机建议，并用 AI 助手生成跟进动作。
- 销售主管：查看高优商机、风险客户、团队跟进重点和可解释评分。
- 系统管理员：管理模型接入、知识检索、限流、RBAC、Outbox、运行审计和评测质量。

产品核心目标：

- 让销售知道“今天先跟进谁、怎么跟进、下一步写什么任务”。
- 让 AI 写操作可控：读工具可直接执行，写工具必须 confirmation。
- 让系统可运营：每次 Agent run、tool call、confirmation、事件分发和评测结果都能被追踪。

## 核心功能

- CRM Core：销售、客户、商机、联系记录、任务、产品套餐
- Lead Scoring：可解释商机排序，输出分数、优先级、推荐原因和下一步动作
- Knowledge RAG：文档导入、语义化分块、阿里云百炼真实 Embedding、pgvector 混合检索、引用、低置信拒答
- Agent Orchestrator：LLM Tool Calling 优先、规则路由兜底、工具注册、运行轨迹、最终回答
- Human Confirmation：写任务、写联系记录等 CRM 写操作必须先生成 confirmation
- Call Center：通话摘要、客户异议抽取、质检、联系记录确认写入
- Evaluation：RAG Recall、引用命中、拒答、工具调用、确认覆盖率、延迟指标
- Frontend Workbench：今日工作台、AI 助手、客户 360、商机优先级、知识库、呼叫中心、系统能力、运行审计、质量评估

## 技术栈

- Backend：Java 17、Spring Boot 3.2、Maven、MyBatis-Plus、Validation、JUnit 5
- Data：PostgreSQL 16、pgvector 镜像、Redis、Kafka、Flyway
- Agent/RAG：阿里云百炼 Qwen、OpenAI-compatible 协议适配、text-embedding-v4、pgvector
- Frontend：React 18、TypeScript、Vite、Ant Design、Axios、React Router
- DevOps：Docker Compose、GitHub Actions、PowerShell 启动与验收脚本

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

本地完整运行首选全栈脚本：

```powershell
.\scripts\start-docker-demo.ps1 -OpenBrowser
```

这个脚本会优先用 Docker Compose 启动 PostgreSQL/pgvector、Redis、Kafka、后端和前端；如果 Docker Hub 拉取基础镜像失败，会自动回退到“Docker 基础设施 + 本机 Maven/NPM 后端前端”的启动方式。脚本会读取系统环境变量里的模型配置，等待后端健康检查通过，并执行 smoke check。

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

## Model Configuration

默认支持 deterministic mock mode，便于本地测试和 CI 稳定复现；线上或完整本地环境可接入阿里云百炼。这里的 `OpenAI-compatible` 指协议兼容，不代表供应商一定是 OpenAI。阿里云百炼 DashScope 使用同一类 `/chat/completions`、`/embeddings` 接口，所以可以复用统一适配层：

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

系统支持两种认证路径：

- 数据库 RBAC token：推荐方式。`agentpilot_user` 表只保存 token 的 SHA-256 哈希，认证后加载 userId、salesRepId、角色和权限。
- 本地兼容 token：用于无数据库用户体系时的本地启动兜底。

要启用严格权限模式，把安全模式写入用户环境变量后重启 PowerShell：

```powershell
[Environment]::SetEnvironmentVariable("AGENTPILOT_SECURITY_MODE", "strict", "User")
```

严格模式下，后端业务 API 需要请求头：

```text
X-AgentPilot-Token: RBAC 用户 token
```

前端登录页会调用 `GET /api/auth/me` 校验 token，并根据数据库角色自动展示销售、主管或系统管理员菜单。

验证生产化能力的接口：

```text
GET  /api/auth/me                # 查看当前 token 绑定的用户、角色和权限
GET  /api/events/status          # 查看 log-only/Kafka 模式与 outbox 状态
GET  /api/agent/tools/openai     # 查看 OpenAI-compatible tools schema
POST /api/knowledge/search       # PostgreSQL 下结果 retriever 为 pgvector-hybrid
```

## 验收脚本

后端启动后，先跑 smoke 验收：

```powershell
.\scripts\smoke-demo.ps1
```

后端启动后，可以直接运行完整 API 验收：

```powershell
.\scripts\demo-api.ps1
```

如果当前机器没有 Docker，也可以用 H2 内存库启动后端进行功能验证：

```powershell
.\scripts\start-demo-backend.ps1
```

另开一个 PowerShell：

```powershell
$env:AGENTPILOT_BASE_URL="http://localhost:18080"
.\scripts\demo-api.ps1
```

验收包含：

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
- 后端已在 Docker Postgres/Redis 环境下跑通完整 API 验收链路
- 已加入 OpenAI-compatible 模型适配、Swagger UI、Actuator 基础端点、`X-Trace-Id` 请求追踪
- 已加入 Spring Security API Token 权限层：strict 模式下所有业务 API 需要 `X-AgentPilot-Token`，并通过数据库 RBAC 映射 userId、salesRepId、角色和权限
- 已接入阿里百炼真实 embedding：默认 `text-embedding-v4`、1024 维，写入 PostgreSQL `crm_knowledge_chunk.embedding_vector vector(1024)`，并通过 HNSW 索引和 `pgvector-hybrid` 检索路径跑通
- 已加入 outbox 事件表 `agent_outbox_event`：确认后的 CRM task 事件与业务写入共享事务；Agent run / tool call 审计事件统一落库并按 at-least-once 语义重试分发；默认 log-only，设置 `AGENT_EVENTS_KAFKA_ENABLED=true` 后发布到 Kafka
- Tool Registry 已 schema 化，并通过 `GET /api/agent/tools/openai` 暴露 OpenAI-compatible tools JSON Schema；主 Agent 会优先让 LLM 选择工具，失败或超时再回退规则路由
- 已加入接口限流：优先使用 Redis 做分布式固定窗口限流，Redis 不可用时自动回退本机 token bucket，保护 Agent Chat、Model Chat 和普通 API
- 前端已改为 token 登录，登录后通过 `/api/auth/me` 读取当前用户画像，不再在页面暴露内置身份 token

## 已知限制

- 主 Agent 已接入 LLM Tool Calling 主流程。为了保证写 CRM 的安全边界，创建任务、写联系记录、更新商机阶段等写操作会优先进入确定性工具确认流，避免模型用自然语言“口头确认”绕过真实 confirmation。
- RAG 已使用 PostgreSQL pgvector 存储和相似度检索；配置 `AGENT_EMBEDDING_PROVIDER=openai-compatible` 后走阿里百炼 `text-embedding-v4` 真实 embedding。本地/测试仍可回退 deterministic mock，保证用例稳定。
- 事件层已实现应用级 outbox。CRM 写操作事件在确认事务内落库，强调事务-事件一致；Agent run / tool call 是审计型事件，走 at-least-once 落库重试，不声明 exactly-once。生产大规模场景可以继续演进为 outbox + CDC/Debezium。
- 当前鉴权是 API Token + 数据库 RBAC，适合内网业务工具和服务端集成；完整商业化上线仍建议接入企业 SSO/JWT、刷新 token、登录审计和租户级隔离。
- Tool Schema 已能暴露给 OpenAI-compatible function calling，当前已覆盖客户分析、商机推荐、知识检索、产品查询、创建任务、写联系记录、更新商机阶段等主工具；生产化仍需要补更严格的参数校验、权限边界和多轮消歧。
- JSONL 评测集当前用于验证评测框架和回归链路，商业化上线需要接入真实业务样本、失败样例沉淀和持续评测看板。
