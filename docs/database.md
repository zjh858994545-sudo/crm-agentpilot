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
- `crm_knowledge_chunk`
- `crm_retrieval_log`

## Agent Tables

- `crm_agent_session`
- `crm_agent_run`
- `crm_agent_tool_call`
- `crm_agent_confirmation`
- `crm_agent_feedback`

## Key Guarantees

- Write tools use idempotency keys.
- Audit records keep enough information for replay and debugging.
- Retrieval logs store query rewrite, retriever type, top K, result JSON, and latency.
- CRM writes are never executed before confirmation.
- The first version stores mock embeddings as text so H2 integration tests can run without PostgreSQL-specific vector syntax.
