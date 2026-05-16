# CRM-AgentPilot 产品运维手册

这份文档面向产品上线、私有化部署和日常运维，不用于面试讲解。目标是让团队知道系统如何部署、如何验收、如何看运行状态，以及哪些开关必须在生产环境收紧。

## 1. 产品边界

CRM-AgentPilot 是面向销售作业的 CRM AI Agent 工作台。系统允许 AI 读取客户、商机、知识库和通话上下文，并生成跟进建议；所有写入 CRM 的动作必须经过 confirmation 确认流。

生产环境必须坚持三个边界：

- AI 可以建议，但不能绕过确认流直接改 CRM。
- 销售只能访问自己数据，主管和管理员按权限访问团队或系统数据。
- 所有 Agent run、tool call、confirmation、outbox 事件和评测结果都必须可追踪。

## 2. 角色与权限

系统内置三类业务角色：

- 销售：查看自己的客户、商机、Agent 建议和待确认写操作。
- 销售主管：查看团队商机、风险客户、评分解释和业务趋势。
- 系统管理员：查看系统能力、限流、RBAC、Outbox、模型、向量检索、审计和评测状态。

生产环境建议：

- 开启 `AGENTPILOT_SECURITY_MODE=strict`。
- 设置 `AGENTPILOT_SEED_USERS_ENABLED=false`，禁用内置种子账号。
- 只使用数据库 RBAC token 或企业 SSO/JWT 映射出的真实用户。
- RBAC token 只保存 SHA-256 哈希，不在数据库中保存明文 token。

## 3. 环境开关

核心环境变量：

```text
SPRING_PROFILES_ACTIVE=local|prod
AGENTPILOT_APP_PHASE=production
AGENTPILOT_SECURITY_MODE=strict
AGENTPILOT_SEED_USERS_ENABLED=false
AGENTPILOT_RATE_LIMIT_ENABLED=true
AGENT_MODEL_PROVIDER=openai-compatible
AGENT_EMBEDDING_PROVIDER=openai-compatible
AGENT_EVENTS_KAFKA_ENABLED=true
```

本地开发可以使用 mock model、mock embedding 和 permissive security。生产环境必须显式配置模型、embedding、RBAC、安全模式和限流。

当 `AGENTPILOT_APP_PHASE` 设置为 `production`、`prod`、`commercial` 或 `launch` 时，后端会执行启动前检查。以下条件不满足会直接启动失败：

- 安全模式不是 strict。
- 内置种子账号仍然启用。
- Chat 模型仍然是 mock 或缺少 baseUrl / apiKey / model。
- Embedding 模型仍然是 mock 或缺少 baseUrl / apiKey / model / dimensions。

## 4. 部署验收

后端启动后，按顺序验收：

```powershell
.\scripts\smoke-demo.ps1
```

前端完整链路验收：

```powershell
cd frontend
$env:VITE_BACKEND_URL="http://localhost:18080"
$env:E2E_FULL_DEMO="1"
npm run test:e2e
```

后端质量门禁：

```powershell
cd backend
mvn "-Dmaven.repo.local=.\.m2repo" test
```

前端质量门禁：

```powershell
cd frontend
npm run build
```

上线前必须满足：

- 后端测试通过。
- 前端生产构建通过。
- Playwright 业务链路通过。
- `/api/auth/me` 能返回真实用户和权限。
- `/api/knowledge/status` 显示向量库模式符合预期。
- `/api/events/status` 没有异常堆积的 outbox 事件。
- `/api/model/status` 显示模型处于配置状态。

## 5. 运行监控

系统管理页负责展示运行中心能力：

- 模型状态：LLM 是否配置、当前模型、模型模式。
- 知识库状态：文档数、分块数、向量化分块数、pgvector 是否可用。
- 安全状态：strict/permissive、RBAC token、种子账号开关。
- Token 审计：RBAC token 最近认证时间和来源 IP，默认按用户 + IP 节流写入，避免高频请求持续写库。
- 限流状态：Agent、模型诊断、普通 API 的限流策略。
- Outbox 状态：待分发、分发中、失败和已发布事件数量。
- 审计状态：Agent run、tool call、confirmation 和 trace 信息。
- Actuator 指标：`/actuator/health` 用于探活，`/actuator/metrics` 和 `/actuator/prometheus` 需要认证后访问，可接 Prometheus/Grafana。

运维人员优先关注：

- 失败事件是否持续增长。
- Agent 调用延迟是否异常上升。
- 模型调用是否进入 mock fallback。
- 向量分块是否未完成 backfill。
- 同一用户或同一 IP 是否触发高频限流。

## 6. Outbox 事件说明

CRM task 创建事件在 confirmation 事务内写入 `agent_outbox_event`，事务提交后分发。

Agent run 和 tool call 是审计事件，使用同一张 outbox 表做 at-least-once 落库与重试，但不声明 exactly-once。消费方必须按 `eventId` 做幂等。

上线建议：

- Kafka 消费方按 `eventId` 去重。
- 对连续失败事件设置告警。
- 后续可演进为 outbox + CDC/Debezium。
- 对永久失败事件增加 DLQ 或人工重放入口。

## 7. RAG 与模型说明

知识库支持 mock embedding 和真实 embedding 两种模式。

- 本地测试：可使用 deterministic mock，保证测试稳定。
- 生产环境：使用阿里云百炼 `text-embedding-v4`，维度 1024，写入 PostgreSQL pgvector。

上线前应重新导入或 backfill 知识库，确保：

- `vectorizedChunkCount` 接近 `chunkCount`。
- `vectorStoreMode` 为 `pgvector-hybrid`。
- 知识问答返回可追踪 citation。
- 低置信问题能触发拒答。

## 8. 数据与隐私

当前客户手机号接口层已做展示脱敏，但生产环境仍需要继续完善：

- 日志禁止输出 API key、token、手机号、客户备注等敏感信息。
- 导出报表需要按角色和数据范围授权。
- 联系记录、通话摘要和质检结果属于业务敏感数据，需要保留审计。
- 多租户场景需要在 salesRepId 之外引入 tenantId。

## 9. 故障处理

模型调用失败：

- 检查 `/api/model/status`。
- 检查 API key、baseUrl、proxy 和模型名称。
- 业务主路径会回退规则路由，系统不会因为模型失败完全不可用。

知识库检索异常：

- 检查 `/api/knowledge/status`。
- 确认 pgvector extension 可用。
- 确认 embedding 维度与配置一致。
- 必要时重新导入知识文档或触发 backfill。

Outbox 堆积：

- 检查 Kafka 是否可用。
- 检查事件状态是否卡在 `PROCESSING` 或 `FAILED`。
- 按 `eventId` 排查下游幂等消费。

权限异常：

- 检查 `AGENTPILOT_SECURITY_MODE`。
- 检查 `AGENTPILOT_SEED_USERS_ENABLED`。
- 使用 `/api/auth/me` 验证 token 映射出的 userId、salesRepId、roles 和 permissions。

## 10. 后续商业化路线

已经具备的产品化能力：

- RBAC token 认证与角色菜单。
- salesRepId 数据隔离。
- LLM Tool Calling 主流程。
- 写操作 confirmation 安全边界。
- pgvector 真实向量检索。
- Outbox 事件持久化与 CAS 分发。
- Redis 优先的接口限流。
- 系统管理页运行中心。
- Playwright 完整业务链路测试。

继续增强方向：

- 企业 SSO/JWT、刷新 token 和登录审计。
- tenantId 多租户隔离。
- Outbox DLQ 与人工重放。
- Prometheus/OpenTelemetry 指标与告警。
- 更大规模真实评测集与失败样例库。
- 联系记录写入幂等和 confirmation 乐观锁。
- 生产级数据备份、恢复和保留策略。
