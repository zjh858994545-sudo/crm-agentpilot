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
AGENTPILOT_CALLCENTER_PROVIDER=manual|aliyun|tencent|custom
AGENTPILOT_ASR_PROVIDER=manual|aliyun-nls|tencent-asr|custom
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
- 系统管理页“上线检查清单”没有 `FAIL` 阻塞项。
- `/api/auth/me` 能返回真实用户和权限。
- `/api/knowledge/status` 显示向量库模式符合预期。
- `/api/events/status` 没有异常堆积的 outbox 事件。
- `/api/model/status` 显示模型处于配置状态。

## 5. 运行监控

系统管理页负责展示运行中心能力：

- 上线检查：安全、RBAC、限流、模型、Embedding、pgvector、Outbox、数据保留是否达到上线门槛。
- 模型状态：LLM 是否配置、当前模型、模型模式。
- 知识库状态：文档数、分块数、向量化分块数、pgvector 是否可用。
- 安全状态：strict/permissive、RBAC token、种子账号开关。
- Token 审计：RBAC token 最近认证时间和来源 IP，默认按用户 + IP 节流写入，避免高频请求持续写库。
- 限流状态：Agent、模型诊断、普通 API 的限流策略。
- Outbox 状态：待分发、分发中、失败和已发布事件数量。
- 租户配置中心：按租户覆盖模型、通知、限流、保留周期和供应商配置，避免所有企业共用一组环境变量。
- 导出审批：敏感数据导出必须先提交申请，管理员审批后再进入真实导出流程。
- 电话 / ASR 供应商：显示电话系统和语音转文字供应商是否开启、endpoint 是否配置。
- 数据生命周期：Agent 审计、检索日志、已发布 Outbox 事件的保留周期、可清理行数和受保护行数。
- 审计状态：Agent run、tool call、confirmation 和 trace 信息。
- Actuator 指标：`/actuator/health` 用于探活，`/actuator/metrics` 和 `/actuator/prometheus` 需要认证后访问，可接 Prometheus/Grafana。
- Trace 排障：所有 API 响应头和统一 JSON 响应体都会返回 `X-Trace-Id` / `traceId`，前端截图可以直接对应后端日志。

业务级 Prometheus 指标：

- `agentpilot_outbox_pending_events`
- `agentpilot_outbox_dispatching_events`
- `agentpilot_outbox_dead_letter_events`
- `agentpilot_knowledge_chunks_total`
- `agentpilot_knowledge_vectorized_chunks`
- `agentpilot_retention_eligible_rows`

告警规则模板位于 `ops/prometheus/agentpilot-alert-rules.yml` 和 `ops/prometheus/agentpilot-alerts.yml`，覆盖 Outbox 死信、Outbox backlog、保留策略 backlog、知识向量覆盖率过低、接口限流突增、通知失败和 webhook 拒绝。

OpenTelemetry Collector 模板位于 `ops/otel/otel-collector.yml`。生产环境可以通过 Java Agent 把 trace/metric 送入 Collector，再由 Collector 暴露 Prometheus 指标或转发到企业已有 APM：

```text
JAVA_TOOL_OPTIONS=-javaagent:/otel/opentelemetry-javaagent.jar
OTEL_SERVICE_NAME=crm-agentpilot-backend
OTEL_EXPORTER_OTLP_ENDPOINT=http://otel-collector:4318
```

运维人员优先关注：

- 失败事件是否持续增长。
- Agent 调用延迟是否异常上升。
- 模型调用是否进入 mock fallback。
- 向量分块是否未完成 backfill。
- 同一用户或同一 IP 是否触发高频限流。
- 历史审计表是否超过保留周期，清理前是否已完成数据库备份。

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
- 导出报表需要按角色和数据范围授权；当前系统已经提供 `agentpilot_export_request` 审批表，记录导出类型、原因、申请人、审批人和审批意见。审批通过后系统会生成 CSV 导出文件，默认 3 天过期，下载动作会写入管理员审计日志。
- 联系记录、通话摘要和质检结果属于业务敏感数据，需要保留审计。
- 当前后端已在 CRM 客户/商机/任务、Agent run/confirmation、呼叫中心、知识库文档、检索日志等核心链路中使用 `tenantId` 做租户隔离。
- `sales` 角色只能访问自己的 `salesRepId` 数据；`sales_manager` 和 `system_admin` 可以访问同一租户内的团队数据，但不能跨租户。
- 前端不能传入或决定 `tenantId`；后端必须从认证后的 `AgentPilotPrincipal` 读取租户、角色和数据范围。
- 生产环境还需要补充更完整的数据分级、字段级权限、租户级备份恢复演练，以及大文件对象存储归档。

导出闭环当前规则：

- `PENDING`：申请已提交，等待具备 `export:approve` 权限的用户审批。
- `APPROVED`：审批通过并生成 CSV 文件，申请人或审批管理员可下载。
- `REJECTED`：审批拒绝，不能下载。
- `EXPIRED`：审批文件超过过期时间，不能下载。
- 客户导出会对手机号脱敏，例如 `139****1001`。
- 导出内容保存在审批记录中，适合 Demo 和私有化小规模场景；到期后保留审批记录但清空 CSV 正文，避免敏感数据长期滞留。
- 生产大文件建议迁移到对象存储，并在数据库只保存文件 key、水印、过期时间和下载审计。

## 8.1 租户配置中心

`agentpilot_tenant_config` 是商业化 SaaS 的控制面，不替代代码配置，而是让同一套系统可以按企业覆盖部分运行策略。后端通过 `TenantConfigResolver` 读取配置，优先级为：租户覆盖值 > 全局环境变量 > 安全默认值。

建议配置命名：

```text
model.chat.model
model.embedding.model
notification.webhook.url
rateLimit.agentChat.capacity
retention.agentRun.days
callcenter.provider
callcenter.provider.enabled
callcenter.provider.endpoint
callcenter.asr.provider
callcenter.asr.enabled
callcenter.asr.model
```

读取顺序建议为：

1. 先读租户配置。
2. 没有租户覆盖值时读全局环境变量。
3. 仍没有配置时使用安全默认值或拒绝启动。

管理员每次新增、修改、删除租户配置都会写入管理员审计日志，方便追踪“谁改了哪个租户的运行策略”。系统管理页同时展示“覆盖配置”和“生效配置来源”：`TENANT` 表示租户覆盖，`GLOBAL` 表示来自环境变量，`DEFAULT` 表示使用安全默认值。

## 8.2 电话与语音供应商

当前系统把真实电话系统和语音转文字供应商先做成可配置接入点，系统管理页会显示：

- 电话供应商是否启用。
- 电话供应商 endpoint 是否配置。
- ASR 是否启用。
- ASR 供应商和模型名称。

真实上线建议：

- 电话系统通过 `call-ended-events` webhook 推送通话结束事件。
- webhook 必须启用 HMAC 签名、时间窗校验和 nonce 防重放。
- ASR 供应商把录音转文字后，再进入通话摘要、质检和联系记录确认流。
- 原始录音 URL、转写文本和质检结果都属于敏感数据，必须按租户隔离并设置保留周期。

## 9. 备份与恢复

生产环境做任何数据清理、版本升级或迁移前，必须先完成备份。

本地 Docker PostgreSQL 备份：

```powershell
cd "F:\后端开发新项目\crm-agentpilot"
.\scripts\backup-postgres.ps1
```

脚本会在 `backups/postgres/` 下生成 `*.dump` 文件。该目录已加入 `.gitignore`，不要把真实业务备份提交到仓库。

恢复演练：

```powershell
.\scripts\restore-postgres.ps1 -BackupFile "F:\后端开发新项目\crm-agentpilot\backups\postgres\agentpilot-postgres-YYYYMMDD-HHmmss.dump" -ConfirmRestore
```

恢复是破坏性操作，必须满足：

- 已经确认备份文件来源可靠。
- 已停止业务流量或确认没有长事务锁表。
- 恢复后运行 `.\scripts\smoke-demo.ps1 -RunDemo` 做最小链路验证。

上线建议：

- 每次发版、执行保留策略清理、重建 pgvector 索引前都先备份。
- 定期做恢复演练，不能只保留“看起来存在”的备份文件。
- 真实生产环境建议使用云数据库自动备份 + 异地备份 + 定期恢复演练，脚本作为私有化/本地部署兜底。

## 10. 诊断包

系统管理页提供“下载诊断包”能力，对应接口：

```text
GET /api/operations/diagnostics.zip
```

诊断包用于线上故障排查，包含上线就绪检查、数据保留状态、租户用量摘要、Outbox 状态、JVM 摘要、数据库摘要和关键表行数。

诊断包刻意不导出客户明细、联系记录、通话正文、API Token 或模型 Key，避免运维附件变成数据泄露源。

## 11. 数据生命周期

系统提供保留策略中心，避免审计、检索和事件日志无限增长。

可配置项：

```text
AGENTPILOT_RETENTION_ENABLED=false
AGENTPILOT_RETENTION_SCHEDULED_CLEANUP_ENABLED=false
AGENTPILOT_RETENTION_CLEANUP_CRON=0 30 3 * * *
AGENTPILOT_RETENTION_AGENT_AUDIT_DAYS=180
AGENTPILOT_RETENTION_RETRIEVAL_LOG_DAYS=90
AGENTPILOT_RETENTION_OUTBOX_PUBLISHED_DAYS=30
AGENTPILOT_RETENTION_EXPORT_ARTIFACT_DAYS=3
AGENTPILOT_RETENTION_MAX_DELETE_ROWS_PER_RUN=10000
```

清理规则：

- Agent 运行审计：只清理已完成或失败的历史 run、tool call、confirmation；存在 `PENDING` 或 `PROCESSING` 确认单的 run 会被保护。
- 知识检索日志：只清理 `crm_retrieval_log` 历史查询日志，不删除知识库文档和分块。
- Outbox：只清理已发布的 `PUBLISHED` 事件；`PENDING`、`FAILED`、`DISPATCHING`、`DEAD_LETTER` 会被保护。
- 导出文件正文：审批记录保留，`APPROVED` 且已过 `expires_at` 的 CSV 正文会被清空并标记为 `EXPIRED`。

上线建议：

- 默认保持 `AGENTPILOT_RETENTION_ENABLED=false`，先在系统管理页运行 dry-run 预演。
- 确认数据库备份和恢复流程可用后，再开启真实清理。
- 如果 dry-run 结果超过 `AGENTPILOT_RETENTION_MAX_DELETE_ROWS_PER_RUN`，先分批或提高上限，不要在高峰期一次性清理大批数据。
- 定时清理只在 `AGENTPILOT_RETENTION_ENABLED=true` 且 `AGENTPILOT_RETENTION_SCHEDULED_CLEANUP_ENABLED=true` 时执行。

## 12. 故障处理

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

数据清理异常：

- 先在系统管理页执行 dry-run，确认可清理行数和受保护行数。
- 如果后端返回超过单次清理上限，先备份数据库，再调整 `AGENTPILOT_RETENTION_MAX_DELETE_ROWS_PER_RUN`。
- 如果清理被禁用，确认 `AGENTPILOT_RETENTION_ENABLED=true`。

## 13. 后续商业化路线

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
- 云数据库自动备份、异地灾备和恢复演练自动化。
- 移动端离线草稿同步、批量确认、电话拨打权限与通话 SDK 集成。

## 14. Runtime Healthcheck Acceptance

`scripts/ops-healthcheck.ps1` is the runtime acceptance script for staging and production smoke verification. It is intentionally stricter than a browser demo:

- Every request carries a unique `X-Trace-Id`; the script fails if the response header does not echo the same trace id.
- Core checks cover health, authenticated profile, security posture, dashboard metrics, model status, knowledge vector status, outbox status, launch readiness, and retention status.
- Admin checks also validate the tenant registry through `/api/tenants`; use `-SkipAdminChecks` only when validating with a sales-only token.
- The script accepts internal RBAC tokens through `-Token` and enterprise SSO/JWT tokens through `-BearerToken`.
- `scripts/release-gate.ps1` calls this script in the runtime stage and supports `-SkipAdminHealthchecks` for non-admin staging checks.
- The script should be run after deployment, after configuration changes, and after incident recovery.

## 15. Sales Notification And Call-End Workflow

The product story is now centered on: **AI suggests, humans decide, every CRM write has a responsible owner**.

Operationally this means confirmation is not only an audit record. It is also a user-facing work item:

- When Agent or Call Center creates a CRM write confirmation, the backend creates an `agentpilot_notification` row for the responsible user.
- The frontend header shows unread confirmation reminders so sales reps do not need to actively search for pending writes.
- Confirming or rejecting the confirmation marks the related notification as read.
- Delivery mode is log-only by default. Set `AGENTPILOT_NOTIFICATION_WEBHOOK_ENABLED=true`, `AGENTPILOT_NOTIFICATION_DELIVERY_CHANNEL=generic|wecom|dingtalk`, `AGENTPILOT_APP_BASE_URL=https://...`, and `AGENTPILOT_NOTIFICATION_WEBHOOK_URL=...` to forward confirmation reminders to an enterprise chat bot or gateway.
- `POST /api/callcenter/call-ended-events` is the productized call-end entry point. A telephony provider can send `callId`, `recordingUrl`, `transcript`, `customerId`, and `leadId`; the system returns summary, quality-check result, and a contact-log confirmation draft.

Production integration notes:

- The telephony system should own call recording and speech-to-text; CRM-AgentPilot owns analysis, confirmation, and CRM write safety.
- The call-end endpoint must be protected by tenant identity, request signature validation, and replay protection before being exposed to third-party callbacks.
- WeCom and DingTalk bots are built-in delivery formats. `generic` sends a neutral JSON payload for SMS, custom gateways, or middleware; `wecom` and `dingtalk` send markdown payloads with an action link back to the confirmation detail.
- Notification and confirmation remain intentionally separate: notification is for reachability, confirmation is the write-safety boundary.

Recommended production notification setup:

| Channel | Environment | Payload shape | When to use |
|---|---|---|---|
| `generic` | `AGENTPILOT_NOTIFICATION_DELIVERY_CHANNEL=generic` | CRM-AgentPilot JSON | Internal notification gateway, SMS platform, custom middleware |
| `wecom` | `AGENTPILOT_NOTIFICATION_DELIVERY_CHANNEL=wecom` | Enterprise WeChat bot markdown | Sales teams already working in WeCom groups |
| `dingtalk` | `AGENTPILOT_NOTIFICATION_DELIVERY_CHANNEL=dingtalk` | DingTalk bot markdown | Sales teams already working in DingTalk |

Validation checklist:

- Trigger a CRM write confirmation from Agent or Call Center.
- Check `/api/system/status` or the System Admin page for notification mode and channel.
- Confirm `agentpilot_notification_webhook_delivery_total{channel="...",result="success"}` increases.
- Open the pushed link and verify it lands on the exact confirmation/customer context.

## 15. Inbound Webhook Security

External telephony providers must call `POST /api/callcenter/call-ended-events`. This endpoint is a production boundary and should not be exposed without signature validation.

Enable protection:

```text
AGENTPILOT_CALLCENTER_WEBHOOK_SIGNATURE_ENABLED=true
AGENTPILOT_CALLCENTER_WEBHOOK_SECRET=<managed-secret>
AGENTPILOT_CALLCENTER_WEBHOOK_MAX_SKEW_SECONDS=300
```

Required headers:

```text
X-AgentPilot-Webhook-Timestamp: <epoch-seconds>
X-AgentPilot-Webhook-Nonce: <unique-random-nonce>
X-AgentPilot-Webhook-Signature: sha256=<hmac-sha256>
```

Signature payload:

```text
timestamp + "." + nonce + "." + rawJsonBody
```

The backend rejects:

- missing or malformed timestamp, nonce, or signature;
- timestamp outside the configured skew window;
- invalid HMAC signature;
- repeated nonce for the same tenant and endpoint.

The browser product uses `POST /api/callcenter/call-ended-events/internal` for authenticated in-app simulations and operator workflows. Do not put the webhook secret in frontend code.

Prometheus metrics:

- `agentpilot_webhook_accepted_total{endpoint="call-ended-events"}`
- `agentpilot_webhook_rejected_total{endpoint="call-ended-events",reason="..."}`
- `agentpilot_notification_webhook_delivery_total{channel="generic|wecom|dingtalk",result="success|failed|log_only"}`

## 16. Lightweight Load Testing

Use `scripts/load-test.ps1` after deployment, configuration changes, or rate-limit tuning. The script uses built-in PowerShell jobs and writes a Markdown report to `ops/reports`.

```powershell
# Public health endpoint
.\scripts\load-test.ps1 -Scenario health -Requests 100 -Concurrency 10

# Business dashboard endpoint. In strict mode pass a real RBAC token.
.\scripts\load-test.ps1 -Scenario dashboard -Requests 100 -Concurrency 10 -Token "agentpilot-sales-1"

# Agent path. Keep request count small when a real model provider is enabled.
.\scripts\load-test.ps1 -Scenario agent -Requests 20 -Concurrency 3 -Token "agentpilot-sales-1"
```

Interpretation:

- `health` validates basic service availability and reverse proxy routing.
- `dashboard` validates tenant/sales-rep scoped aggregation and database query latency.
- `agent` validates rate limiting, tool routing, and model provider stability. Use mock mode for frequent load tests; use real model mode only for small confirmation tests to control cost.
- P95 latency and non-2xx status codes should be reviewed before each production release.

For staging or pre-production, use the k6 script:

```powershell
k6 run `
  -e BASE_URL=http://localhost:18080 `
  -e AGENTPILOT_API_TOKEN=$env:AGENTPILOT_API_TOKEN `
  .\ops\k6\agentpilot-smoke.js
```

The k6 script exercises Dashboard metrics and Agent Chat together, and sets P95 thresholds for both paths. Run it with an isolated tenant, controlled model budget, PostgreSQL slow-query logging, and Outbox monitoring enabled.
