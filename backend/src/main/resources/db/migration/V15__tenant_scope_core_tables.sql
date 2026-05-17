ALTER TABLE agentpilot_user
    ADD COLUMN IF NOT EXISTS tenant_id VARCHAR(64) NOT NULL DEFAULT 'demo';

ALTER TABLE crm_sales_rep
    ADD COLUMN IF NOT EXISTS tenant_id VARCHAR(64) NOT NULL DEFAULT 'demo';

ALTER TABLE crm_customer
    ADD COLUMN IF NOT EXISTS tenant_id VARCHAR(64) NOT NULL DEFAULT 'demo';

ALTER TABLE crm_lead
    ADD COLUMN IF NOT EXISTS tenant_id VARCHAR(64) NOT NULL DEFAULT 'demo';

ALTER TABLE crm_contact_log
    ADD COLUMN IF NOT EXISTS tenant_id VARCHAR(64) NOT NULL DEFAULT 'demo';

ALTER TABLE crm_task
    ADD COLUMN IF NOT EXISTS tenant_id VARCHAR(64) NOT NULL DEFAULT 'demo';

ALTER TABLE crm_agent_session
    ADD COLUMN IF NOT EXISTS tenant_id VARCHAR(64) NOT NULL DEFAULT 'demo';

ALTER TABLE crm_agent_run
    ADD COLUMN IF NOT EXISTS tenant_id VARCHAR(64) NOT NULL DEFAULT 'demo';

CREATE INDEX IF NOT EXISTS idx_agentpilot_user_tenant ON agentpilot_user(tenant_id);
CREATE INDEX IF NOT EXISTS idx_crm_customer_tenant_owner ON crm_customer(tenant_id, owner_sales_rep_id);
CREATE INDEX IF NOT EXISTS idx_crm_lead_tenant_sales_rep ON crm_lead(tenant_id, sales_rep_id);
CREATE INDEX IF NOT EXISTS idx_crm_contact_tenant_customer ON crm_contact_log(tenant_id, customer_id);
CREATE INDEX IF NOT EXISTS idx_crm_task_tenant_sales_rep ON crm_task(tenant_id, sales_rep_id);
CREATE INDEX IF NOT EXISTS idx_agent_run_tenant_user_sales ON crm_agent_run(tenant_id, user_id, sales_rep_id);
