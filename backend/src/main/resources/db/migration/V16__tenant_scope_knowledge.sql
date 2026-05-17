ALTER TABLE crm_knowledge_doc
    ADD COLUMN IF NOT EXISTS tenant_id VARCHAR(64) NOT NULL DEFAULT 'demo';

ALTER TABLE crm_retrieval_log
    ADD COLUMN IF NOT EXISTS tenant_id VARCHAR(64) NOT NULL DEFAULT 'demo';

CREATE INDEX IF NOT EXISTS idx_knowledge_doc_tenant_type
    ON crm_knowledge_doc(tenant_id, doc_type);

CREATE INDEX IF NOT EXISTS idx_retrieval_log_tenant_created
    ON crm_retrieval_log(tenant_id, created_at);
