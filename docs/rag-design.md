# RAG Design

The RAG module supports sales SOP retrieval, package policy lookup, objection handling, and call quality rules.

## Pipeline

1. Import document.
2. Split into semantic chunks.
3. Generate embeddings through `EmbeddingService`: deterministic mock for tests, or OpenAI-compatible real embeddings for configured environments.
4. Store chunks and serialized vectors for H2 compatibility. In PostgreSQL, Flyway creates `embedding_vector vector(1024)` and an HNSW index for pgvector search. The default real provider is Alibaba Bailian `text-embedding-v4`.
5. Rewrite query for sales terminology.
6. Run keyword retrieval plus pgvector similarity search in PostgreSQL; H2 tests fall back to deterministic in-memory vector-like scoring.
7. Merge and rerank results with rule weights.
8. Return citations or refuse when evidence is weak.

## Required Behaviors

- Answers include citations.
- Low-confidence retrieval refuses to answer.
- Knowledge text cannot override system instructions.
- Retrieval logs are persisted for evaluation and debugging.
- The implementation remains deterministic in mock mode, which makes tests repeatable.
- In configured local/demo environments, `AGENT_EMBEDDING_PROVIDER=openai-compatible` calls the provider's `/embeddings` endpoint and stores 1024-dimensional vectors in pgvector.

## First Knowledge Documents

- Renewal SOP.
- Price objection handling.
- Exposure and ROI explanation.
- Package policy.
- Call quality rules.
- Competitor comparison.
- Follow-up cadence.
- Local-services industry playbook.
