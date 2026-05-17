CREATE TABLE IF NOT EXISTS agentpilot_tenant (
    id VARCHAR(64) PRIMARY KEY,
    name VARCHAR(128) NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    plan_code VARCHAR(64) NOT NULL DEFAULT 'demo',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

INSERT INTO agentpilot_tenant (id, name, status, plan_code)
VALUES ('demo', 'Demo Tenant', 'ACTIVE', 'demo');

CREATE INDEX IF NOT EXISTS idx_agentpilot_tenant_status
    ON agentpilot_tenant(status);
