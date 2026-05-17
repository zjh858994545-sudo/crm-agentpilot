# CRM-AgentPilot Production Deployment Guide

This guide describes the production path for a commercial deployment. The local demo remains useful for development, but production must run with strict security, isolated tenant data, managed secrets, and repeatable operations.

## Deployment Modes

### Private single-tenant

Use this when one company deploys AgentPilot for its own sales team.

- One `tenant_id` such as `acme`.
- One PostgreSQL database.
- RBAC users belong to the same tenant.
- Network access is limited to the company intranet or VPN.

### SaaS multi-tenant

Use this when one AgentPilot cluster serves multiple companies.

- Every business table carries `tenant_id`.
- Current tenant is derived from the authenticated principal, not from frontend input.
- CRM queries must include `tenant_id` plus data-scope filters such as `sales_rep_id`.
- Backups, retention, audit exports, and incident response must support tenant-level filtering.

## Required Environment Variables

Use `.env.production.example` as the deployment template. Copy the values into the target host environment, Docker secrets, Kubernetes secrets, or CI protected variables; do not deploy with placeholder secrets.

```powershell
AGENTPILOT_APP_PHASE=production
AGENTPILOT_SECURITY_MODE=strict
AGENTPILOT_SEED_USERS_ENABLED=false
AGENTPILOT_API_TOKEN=
AGENTPILOT_RATE_LIMIT_ENABLED=true
AGENTPILOT_RATE_LIMIT_BACKEND=redis
AGENTPILOT_DEMO_TENANT_ID=demo
AGENTPILOT_JWT_ENABLED=false
AGENTPILOT_JWT_ISSUER_URI=
AGENTPILOT_JWT_AUDIENCE=crm-agentpilot
AGENTPILOT_JWT_USER_ID_CLAIM=user_id
AGENTPILOT_JWT_TENANT_CLAIM=tenant_id
AGENTPILOT_JWT_SALES_REP_CLAIM=sales_rep_id
AGENTPILOT_JWT_ROLES_CLAIM=roles
AGENTPILOT_JWT_PERMISSIONS_CLAIM=permissions
AGENTPILOT_JWT_ALLOWED_TENANTS=demo,tenant-acme

SPRING_DATASOURCE_URL=jdbc:postgresql://postgres:5432/agentpilot
SPRING_DATASOURCE_USERNAME=agentpilot
SPRING_DATASOURCE_PASSWORD=<managed-secret>

AGENT_MODEL_PROVIDER=openai-compatible
OPENAI_COMPATIBLE_BASE_URL=https://dashscope.aliyuncs.com/compatible-mode/v1
OPENAI_COMPATIBLE_API_KEY=<managed-secret>
OPENAI_COMPATIBLE_CHAT_MODEL=qwen3.6-flash

AGENT_EMBEDDING_PROVIDER=openai-compatible
OPENAI_COMPATIBLE_EMBEDDING_BASE_URL=https://dashscope.aliyuncs.com/compatible-mode/v1
OPENAI_COMPATIBLE_EMBEDDING_API_KEY=<managed-secret>
OPENAI_COMPATIBLE_EMBEDDING_MODEL=text-embedding-v4
OPENAI_COMPATIBLE_EMBEDDING_DIMENSIONS=1024
```

Do not put API keys in source code, screenshots, docs, shell history, or frontend build variables. Use the host secret manager, Kubernetes secrets, Docker secrets, or CI protected variables.

## Startup Checklist

1. PostgreSQL has `vector` extension enabled.
2. Flyway migrations finish successfully.
3. `GET /api/health` returns `UP`.
4. `GET /api/model/status` shows `llm-enabled` for production LLM mode.
5. `GET /api/knowledge/status` shows `vectorStoreMode=pgvector-hybrid`.
6. `GET /api/security/status` shows strict mode, real RBAC users, rate limit enabled, and expected JWT status.
7. `GET /api/operations/readiness` has no blocking failures.
8. Run `scripts/ops-healthcheck.ps1` from an operations workstation. The script fails not only on HTTP errors, but also when readiness is `BLOCKED`, strict security is unusable, or Outbox has dead-letter events.

Before starting production, run the static preflight:

```powershell
.\scripts\preflight-production.ps1
```

If you keep deployment variables in a local env file for staging validation, load it explicitly:

```powershell
.\scripts\preflight-production.ps1 -EnvFile .\.env.production
```

If the runtime host does not use Docker, run:

```powershell
.\scripts\preflight-production.ps1 -SkipDockerCheck
```

## Data Isolation Rules

Production code must follow these rules:

- Frontend never decides `tenantId`; it only displays it.
- Backend derives `tenantId` from `AgentPilotPrincipal`.
- Enterprise JWT deployments should configure `AGENTPILOT_JWT_ALLOWED_TENANTS`; unknown tenants are rejected before a principal is created.
- `agentpilot_tenant` is the tenant registry. Tenant onboarding, disablement, backup scope, and incident response should reference this table rather than ad-hoc tenant strings.
- CRM customer, lead, task, contact log, agent session, agent run, confirmation, knowledge document, retrieval log, and call-center operations include `tenant_id`.
- Sales users can only access their own `salesRepId`; `sales_manager` and `system_admin` can access other sales reps in the same tenant.
- Call-center write proposals must keep `customerId`, `leadId`, and `salesRepId` aligned before creating a confirmation.
- Knowledge search, answer generation, vector rebuild, and document listing are tenant-scoped so one tenant cannot retrieve another tenant's playbooks.
- Confirmation ownership checks validate tenant, user, and sales scope before executing CRM writes.
- Event payloads include enough IDs for downstream idempotency, but downstream services must re-check tenant scope.

## Privacy Controls

CRM data contains business contact information and conversation details. Production APIs must expose the minimum fields needed by the current page.

- Customer mobile numbers are masked in customer views.
- Contact log responses use `ContactLogView` instead of returning the raw entity.
- Contact log response text masks mobile numbers and email addresses.
- Internal fields such as `tenantId` and write idempotency keys are not returned to sales pages.
- Full raw records, if needed for compliance investigation, should be exported through a separate audited admin endpoint.

## Release Steps

1. Build backend jar and frontend static bundle in CI.
2. Run backend unit/integration tests.
3. Run frontend production build.
4. Run frontend bundle budget checks.
5. Run Playwright smoke flow against staging.
6. Apply database migrations in staging, then production.
7. Rebuild missing knowledge vectors after embedding model changes.
8. Run readiness and ops healthcheck.
9. Tag release and keep rollback artifacts.

For a single workstation or staging server, use the release gate script to make the same checks repeatable:

```powershell
.\scripts\release-gate.ps1 -SkipDockerCheck
```

The release gate runs backend tests, frontend production build, frontend bundle budget checks, static production preflight, and runtime operations healthcheck. In CI, keep all steps enabled. During local UI-only work, you can skip the slower or unavailable steps explicitly:

```powershell
.\scripts\release-gate.ps1 -SkipBackendTests -SkipPreflight -SkipRuntimeHealthcheck
```

For staging releases backed by an env file:

```powershell
.\scripts\release-gate.ps1 -EnvFile .\.env.production -SkipDockerCheck
```

Runtime healthcheck details:

- `scripts/ops-healthcheck.ps1` sends a unique `X-Trace-Id` for every check and fails if the backend does not echo the same trace id.
- With an admin token, it verifies `/api/auth/me`, `/api/dashboard/metrics`, `/api/tenants`, model status, knowledge status, outbox status, readiness, and retention status.
- If the token only represents a sales user, use `-SkipAdminHealthchecks` on `release-gate.ps1` or `-SkipAdminChecks` on `ops-healthcheck.ps1`.
- The release gate uses `F:\DockerData\AgentPilotCache\m2` as the default Maven cache on Windows to avoid C-drive pressure and non-ASCII path issues. Override with `MAVEN_REPO_LOCAL` when needed.

Frontend performance budget:

- `scripts/check-frontend-bundle.ps1` scans `frontend/dist/assets` after `npm run build`.
- Default thresholds are 1100 KB for a single JS/CSS asset, 850 KB total JS gzip, and 80 KB total CSS gzip.
- Use `-SkipFrontendBundleBudget` only while investigating a known bundle regression; do not skip it for release approval.

Do not treat a manual browser check as a release gate replacement. Browser checks prove the happy path; the release gate proves build correctness, security preconditions, and runtime health.

## Rollback

Application rollback is safe when database migrations are backward compatible. Destructive migrations, vector dimension changes, and tenant schema changes require a planned migration window.

Minimum rollback assets:

- Previous backend image.
- Previous frontend image.
- PostgreSQL backup before migration.
- Current `agent_outbox_event` dead-letter export.
- Environment variable snapshot without secrets.

## Monitoring

Prometheus alert rules live in `ops/prometheus/agentpilot-alert-rules.yml`.

Minimum alerts:

- Backend health down.
- Outbox pending/dead-letter growth.
- Knowledge vectorized chunks lower than expected.
- Retention eligible rows growing.
- HTTP 401/403/429 spikes.
- Model provider latency and error rate.
