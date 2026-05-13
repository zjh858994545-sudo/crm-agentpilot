# Interview Notes

## Three-Minute Story

CRM-AgentPilot is a CRM AI Agent platform for local-services sales operations. I did not build it as a generic chatbot. I designed it as a virtual sales assistant that can enter CRM workflows, call business tools, retrieve sales SOP knowledge, propose follow-up strategies, and safely create CRM tasks only after human confirmation.

The central design question is: how can an AI Agent help sales reps work faster without giving it unsafe direct write access to CRM data? My answer is a tool registry with read/write separation, confirmation records for writes, and auditable `agent_run` plus `agent_tool_call` traces.

## Key Technical Points

- Tool Calling is modeled as a registry with read and write tool types.
- Write tools produce confirmation records instead of writing directly.
- `agent_run` and `agent_tool_call` provide audit, replay, and debugging.
- RAG combines query rewrite, keyword retrieval, vector retrieval, rerank, citation, and refusal.
- Lead scoring is explainable, so sales reps can trust why a customer is prioritized.
- Evaluation uses JSONL cases and real scripts to avoid invented metrics.
- OpenAI-compatible model access is isolated behind `ChatModelClient`, so local demos stay deterministic while production can switch by environment variables.
- Swagger UI, Actuator, and `X-Trace-Id` show that the project is operated as an engineering service, not just a local script.

## Trade-offs

- The first version uses mock model and mock embedding for repeatable tests.
- Rule scoring ships before ML scoring because interviewers can inspect and reason about it.
- Kafka is used for event recording and future async processing, but core writes remain transactional.
- The local RAG implementation stores deterministic mock embeddings in text form for H2-compatible tests; the Docker stack uses PostgreSQL with the pgvector image so the vector column can be swapped in later without changing service boundaries.

## Interview Demo Order

1. Show Dashboard to establish this is a complete CRM Agent platform.
2. Show Leads to explain scoring factors and recommendations.
3. Ask the Agent to analyze 美家房产 and show tool traces.
4. Ask the Agent to create a follow-up task and show the confirmation gate.
5. Open Knowledge Base to show document chunks, retrieval, answer citations, and refusal.
6. Open Call Center to show summary, quality check, and contact-log confirmation.
7. Open Evaluation to run metrics from JSONL test cases.
8. Open Swagger UI to show the API surface and explain how frontend, evaluation scripts, and interview demos call the same endpoints.

## What Needs External Input

- Real LLM API key: optional. Without it the project runs deterministic mock mode; with it set `AGENT_MODEL_PROVIDER=openai-compatible`, `OPENAI_COMPATIBLE_BASE_URL`, `OPENAI_COMPATIBLE_API_KEY`, and `OPENAI_COMPATIBLE_CHAT_MODEL`.
- GitHub remote: optional. The local Git history can be created now, and a remote can be added later.
- Demo video: optional. Record the frontend flow after Docker, backend, and frontend are running.
