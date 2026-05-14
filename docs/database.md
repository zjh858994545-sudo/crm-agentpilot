# Database Design

The complete database design is implemented through Flyway migrations during Day 2 and later phases.

## CRM Tables

- `crm_sales_rep`
- `crm_customer`
- `crm_lead`
- `crm_contact_log`
- `crm_task`
- `crm_product_package`

## RAG Tables

- `crm_knowledge_doc`
- `crm_knowledge_chunk` (`embedding` text for H2-compatible tests; PostgreSQL adds `embedding_vector vector(1024)` and an HNSW index)
- `crm_retrieval_log`

## Agent Tables

- `crm_agent_session`
- `crm_agent_run`
- `crm_agent_tool_call`
- `crm_agent_confirmation`
- `crm_agent_feedback`

## Event Tables

- `agent_outbox_event`

## Key Guarantees

- Write tools use idempotency keys.
- Audit records keep enough information for replay and debugging.
- Retrieval logs store query rewrite, retriever type, top K, result JSON, and latency.
- CRM writes are never executed before confirmation.
- The first version stores serialized embeddings as text so H2 integration tests can run without PostgreSQL-specific vector syntax; PostgreSQL runtimes also maintain a 1024-dimensional pgvector column for real vector-distance retrieval.
- Agent run, tool-call, and confirmed CRM task events are inserted into `agent_outbox_event` before dispatch, so event delivery can be retried and inspected independently of the request thread.
