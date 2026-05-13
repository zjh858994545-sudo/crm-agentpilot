# RAG Design

The RAG module supports sales SOP retrieval, package policy lookup, objection handling, and call quality rules.

## Pipeline

1. Import document.
2. Split into semantic chunks.
3. Generate embedding with mock or configured embedding provider.
4. Store chunks and embeddings. The current deterministic demo stores mock vectors as text for H2 test compatibility; the Docker database uses the pgvector image for production-style extension support.
5. Rewrite query for sales terminology.
6. Run keyword retrieval and vector retrieval.
7. Merge and rerank results.
8. Return citations or refuse when evidence is weak.

## Required Behaviors

- Answers include citations.
- Low-confidence retrieval refuses to answer.
- Knowledge text cannot override system instructions.
- Retrieval logs are persisted for evaluation and debugging.
- The implementation is intentionally deterministic in mock mode, which makes tests, interview demos, and evaluation reports repeatable.

## First Knowledge Documents

- Renewal SOP.
- Price objection handling.
- Exposure and ROI explanation.
- Package policy.
- Call quality rules.
- Competitor comparison.
- Follow-up cadence.
- Local-services industry playbook.
