ALTER TABLE agent_outbox_event
    ADD COLUMN IF NOT EXISTS locked_by VARCHAR(128);

ALTER TABLE agent_outbox_event
    ADD COLUMN IF NOT EXISTS locked_at TIMESTAMP;

CREATE INDEX IF NOT EXISTS idx_outbox_dispatch_lock
    ON agent_outbox_event(status, locked_at);

CREATE TABLE IF NOT EXISTS agentpilot_user (
    id BIGINT PRIMARY KEY,
    username VARCHAR(64) NOT NULL UNIQUE,
    display_name VARCHAR(128) NOT NULL,
    api_token_sha256 VARCHAR(64) NOT NULL UNIQUE,
    sales_rep_id BIGINT NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS agentpilot_role (
    id BIGINT PRIMARY KEY,
    code VARCHAR(64) NOT NULL UNIQUE,
    name VARCHAR(128) NOT NULL
);

CREATE TABLE IF NOT EXISTS agentpilot_permission (
    id BIGINT PRIMARY KEY,
    code VARCHAR(128) NOT NULL UNIQUE,
    name VARCHAR(128) NOT NULL
);

CREATE TABLE IF NOT EXISTS agentpilot_user_role (
    user_id BIGINT NOT NULL,
    role_id BIGINT NOT NULL,
    PRIMARY KEY (user_id, role_id)
);

CREATE TABLE IF NOT EXISTS agentpilot_role_permission (
    role_id BIGINT NOT NULL,
    permission_id BIGINT NOT NULL,
    PRIMARY KEY (role_id, permission_id)
);

INSERT INTO agentpilot_permission (id, code, name) VALUES
(1, 'agent:use', 'Use Agent workbench'),
(2, 'crm:read', 'Read CRM data'),
(3, 'crm:write', 'Write CRM data through confirmation'),
(4, 'knowledge:read', 'Read knowledge base'),
(5, 'knowledge:write', 'Write knowledge base'),
(6, 'product:read', 'Read product packages'),
(7, 'evaluation:run', 'Run evaluation'),
(8, 'events:read', 'Read event status'),
(9, 'events:write', 'Retry outbox dead letters');

INSERT INTO agentpilot_role (id, code, name) VALUES
(1, 'sales', 'Sales representative'),
(2, 'sales_manager', 'Sales manager'),
(3, 'system_admin', 'System administrator');

INSERT INTO agentpilot_role_permission (role_id, permission_id) VALUES
(1, 1), (1, 2), (1, 3), (1, 4), (1, 6),
(2, 1), (2, 2), (2, 3), (2, 4), (2, 6), (2, 7), (2, 8),
(3, 1), (3, 2), (3, 3), (3, 4), (3, 5), (3, 6), (3, 7), (3, 8), (3, 9);

INSERT INTO agentpilot_user (
    id, username, display_name, api_token_sha256, sales_rep_id, status
) VALUES
(1, 'linxiaofeng', '林晓峰', 'dc141bdf38023050f6064aa55819d30d15e170160c6a4adec36e9368eaacd711', 1, 'ACTIVE'),
(2, 'zhouyuqing', '周雨晴', 'a968b76d16d8160a5c5594fd3bae13e5c51e40d35cdf65762132477511aa2472', 2, 'ACTIVE'),
(900, 'admin', '系统管理员', 'b2cf1f378a6ea5d593eab546580ff0c26e77aa5ec01785ea3acbf21b89af964d', 1, 'ACTIVE');

INSERT INTO agentpilot_user_role (user_id, role_id) VALUES
(1, 1),
(2, 1),
(900, 3);
