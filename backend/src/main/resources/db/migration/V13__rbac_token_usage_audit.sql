ALTER TABLE agentpilot_user
    ADD COLUMN IF NOT EXISTS last_authenticated_at TIMESTAMP;

ALTER TABLE agentpilot_user
    ADD COLUMN IF NOT EXISTS last_authenticated_ip VARCHAR(64);

CREATE INDEX IF NOT EXISTS idx_agentpilot_user_last_authenticated
    ON agentpilot_user(last_authenticated_at);
