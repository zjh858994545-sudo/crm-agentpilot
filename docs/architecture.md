# Architecture

CRM-AgentPilot is built around one rule: the Agent can assist sales work, but business writes must be controlled, auditable, and debuggable.

## Runtime View

```text
Frontend Workbench
  -> REST API
      -> CRM Core
      -> Lead Scoring
      -> Knowledge Retrieval
      -> Agent Orchestrator
      -> Chat Model Adapter
      -> Tool Registry
      -> Confirmation Service
      -> Call Center Service
      -> Evaluation Runner
      -> Event Publisher
  -> PostgreSQL / pgvector
  -> Redis
  -> Kafka
```

## Main Boundaries

- CRM Core owns customer, lead, contact log, task, and product package data.
- RAG owns knowledge documents, chunks, retrieval logs, citations, and refusal policy.
- Agent owns sessions, runs, tool calls, planning state, and final responses.
- Confirmation owns write approvals and idempotency.
- Call Center owns call summary, quality checking, customer memory, and contact-log write proposals.
- Evaluation owns JSONL cases, metric calculation, and generated reports.

## Engineering Choices

- Keep local development runnable with a mock model provider.
- Keep LLM provider logic behind an OpenAI-compatible adapter; the current configured provider is Alibaba Bailian Qwen.
- Protect business APIs with a Spring Security API-token filter in strict mode, while keeping permissive mode for local interviews and smoke scripts.
- Store short-term session memory in Redis with an in-process fallback, and include it in configured customer-analysis model prompts.
- Store every tool call with input, output, status, latency, and error.
- Split read tools and write tools at the registry level, and expose registered tools as OpenAI-compatible function schemas through `/api/agent/tools/openai`.
- Treat retrieved knowledge as business context, never as system instructions.
- Keep tests deterministic with a mock model and mock embedding, while configured demos use Qwen Tool Calling, Bailian `text-embedding-v4`, PostgreSQL pgvector storage, and vector-distance retrieval.
- Keep H2 tests portable by storing the serialized mock vector text alongside the PostgreSQL-only `embedding_vector` column.
- Add `X-Trace-Id` on every HTTP response so API calls can be correlated with logs and tool-call records.
- Publish Agent run, tool-call, and confirmed CRM task events through an outbox-backed event layer. Events are written to `agent_outbox_event` inside the transaction and dispatched after commit. The default is log-only for stable local demos; setting `AGENT_EVENTS_KAFKA_ENABLED=true` sends the same events to Kafka topics.
