DROP INDEX IF EXISTS idx_knowledge_chunk_embedding_hnsw;

ALTER TABLE crm_knowledge_chunk
    DROP COLUMN IF EXISTS embedding_vector;

ALTER TABLE crm_knowledge_chunk
    ADD COLUMN embedding_vector vector(1024);

UPDATE crm_knowledge_chunk
SET embedding = ''
WHERE embedding IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_knowledge_chunk_embedding_hnsw
    ON crm_knowledge_chunk
    USING hnsw (embedding_vector vector_cosine_ops);
