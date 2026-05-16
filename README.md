# CRM-AgentPilot

CRM-AgentPilot 是一个面向本地生活销售 CRM 场景的全栈 AI Agent 工作台。它不是普通聊天机器人，而是把客户、商机、销售知识库、AI 建议、CRM 写入确认、运行审计和质量评估串成一条销售作业流：AI 可以读数据、给建议、生成行动草案，但所有写 CRM 的动作都必须先进入确认流，由用户确认后才真正落库。

## 产品定位

本项目面向三类使用者：

- 销售：查看今日优先客户、客户 360、商机建议，并用 AI 助手生成跟进动作。
- 销售主管：查看高优商机、风险客户、团队跟进重点和可解释评分。
- 系统管理员：管理模型接入、知识检索、限流、RBAC、Outbox、数据保留、运行审计和评测质量。

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

## 文档入口

- [产品运维手册](docs/product-operations-guide.md)：部署、权限、限流、Outbox、RAG、数据保留、监控和上线前验收。
- [学习版讲解稿](docs/project-learning-guide.md)：帮助开发者理解业务主线、页面职责和后端模块。
- [架构说明](docs/architecture.md)：系统边界、Agent 安全边界和核心工程选择。
- [数据库设计](docs/database.md)：CRM、RAG、Agent、RBAC 和 Outbox 表设计。

## 本地/私有化启动

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

## 产品化完成度

已具备的上线基础能力：

- 核心销售作业流：客户、商机推荐、RAG、Agent、确认流、呼叫中心、运行审计和评测。
- 前端工作台：销售、主管、系统管理员三类角色菜单和业务页面。
- 安全边界：Spring Security API Token、数据库 RBAC、salesRepId 数据隔离、strict/permissive 模式切换。
- AI 能力：OpenAI-compatible 模型适配、LLM Tool Calling、规则路由 fallback、Tool Registry JSON Schema。
- RAG 能力：阿里百炼 `text-embedding-v4`、1024 维 pgvector、HNSW 索引、关键词 + 向量混合检索、引用和拒答。
- 可靠性能力：Outbox 事件表、CAS 分发锁、at-least-once 重试、Redis 优先限流、本地 fallback。
- 可观测与治理能力：`X-Trace-Id`、Agent run、tool call、confirmation、retrieval log、数据保留策略、系统管理运行中心。
- 质量门禁：JUnit 后端测试、前端生产构建、Playwright 完整业务链路测试。

上线前必须收紧的配置：

- 设置 `AGENTPILOT_SECURITY_MODE=strict`。
- 设置 `AGENTPILOT_SEED_USERS_ENABLED=false` 并创建真实 RBAC 用户。
- 配置真实模型和 embedding key，避免生产环境使用 mock provider。
- 配置 Kafka 或明确保留 log-only 模式的业务影响。
- 完成业务数据备份/恢复演练、日志脱敏、数据保留策略、告警和运维值班策略。

## 已知边界

- 主 Agent 已接入 LLM Tool Calling 主流程。为了保证写 CRM 的安全边界，创建任务、写联系记录、更新商机阶段等写操作仍必须进入 confirmation。
- Outbox 对 CRM 写事件强调事务-事件一致；Agent run / tool call 是审计事件，按 at-least-once 分发，消费方需要按 `eventId` 幂等。
- API Token + 数据库 RBAC 适合内网业务工具和服务端集成；更完整的商业化上线建议接入企业 SSO/JWT、刷新 token、登录审计和 tenantId 多租户隔离。
- JSONL 评测集当前用于回归验证；真实上线需要持续沉淀真实失败样例、扩充评测集和建立告警阈值。
