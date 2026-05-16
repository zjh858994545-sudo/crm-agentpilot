ALTER TABLE crm_contact_log
    ADD COLUMN IF NOT EXISTS idempotency_key VARCHAR(128);

CREATE UNIQUE INDEX IF NOT EXISTS uk_contact_log_idempotency_key
    ON crm_contact_log(idempotency_key);
