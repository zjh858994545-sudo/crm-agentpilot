ALTER TABLE agentpilot_export_request
    ADD COLUMN IF NOT EXISTS file_name VARCHAR(255);

ALTER TABLE agentpilot_export_request
    ADD COLUMN IF NOT EXISTS file_content TEXT;

ALTER TABLE agentpilot_export_request
    ADD COLUMN IF NOT EXISTS file_size_bytes BIGINT;

ALTER TABLE agentpilot_export_request
    ADD COLUMN IF NOT EXISTS expires_at TIMESTAMP;

ALTER TABLE agentpilot_export_request
    ADD COLUMN IF NOT EXISTS downloaded_at TIMESTAMP;

ALTER TABLE agentpilot_export_request
    ADD COLUMN IF NOT EXISTS download_count INT NOT NULL DEFAULT 0;

CREATE INDEX IF NOT EXISTS idx_export_request_expires
    ON agentpilot_export_request(tenant_id, status, expires_at);
