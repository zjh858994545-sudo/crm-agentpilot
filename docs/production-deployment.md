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

```powershell
AGENTPILOT_SECURITY_MODE=strict
AGENTPILOT_SEED_USERS_ENABLED=false
AGENTPILOT_API_TOKEN=
AGENTPILOT_DEMO_TENANT_ID=demo

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
6. `GET /api/security/status` shows strict mode and real RBAC users.
7. `GET /api/operations/readiness` has no blocking failures.
8. Run `scripts/ops-healthcheck.ps1` from an operations workstation.

## Data Isolation Rules

Production code must follow these rules:

- Frontend never decides `tenantId`; it only displays it.
- Backend derives `tenantId` from `AgentPilotPrincipal`.
- CRM customer, lead, task, contact log, agent session, and agent run queries include `tenant_id`.
- Confirmation ownership checks validate tenant, user, and sales scope before executing CRM writes.
- Event payloads include enough IDs for downstream idempotency, but downstream services must re-check tenant scope.

## Release Steps

1. Build backend jar and frontend static bundle in CI.
2. Run backend unit/integration tests.
3. Run frontend production build.
4. Run Playwright smoke flow against staging.
5. Apply database migrations in staging, then production.
6. Rebuild missing knowledge vectors after embedding model changes.
7. Run readiness and ops healthcheck.
8. Tag release and keep rollback artifacts.

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

