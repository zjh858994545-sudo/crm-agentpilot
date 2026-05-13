# Architecture

CRM-AgentPilot is built around one rule: the Agent can assist sales work, but business writes must be controlled, auditable, and replayable.

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
- Keep LLM provider logic behind an OpenAI-compatible adapter.
- Store every tool call with input, output, status, latency, and error.
- Split read tools and write tools at the registry level.
- Treat retrieved knowledge as business context, never as system instructions.
- Keep the first version deterministic with a mock model and mock embedding so tests and demos are repeatable.
- Use the pgvector Docker image in local infrastructure while storing mock embeddings in a database-portable format for H2 tests.
- Add `X-Trace-Id` on every HTTP response so API calls can be correlated with logs and tool-call records.
