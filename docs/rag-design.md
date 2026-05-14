# RAG Design

The RAG module supports sales SOP retrieval, package policy lookup, objection handling, and call quality rules.

## Pipeline

1. Import document.
2. Split into semantic chunks.
3. Generate deterministic mock embeddings for repeatable local tests.
4. Store chunks and mock vectors as text for H2 test compatibility. The Docker stack uses the pgvector image as an infrastructure placeholder; the current schema does not yet use a `vector` column.
5. Rewrite query for sales terminology.
6. Run keyword retrieval plus deterministic vector-like scoring.
7. Merge and rerank results with rule weights.
8. Return citations or refuse when evidence is weak.

## Required Behaviors

- Answers include citations.
- Low-confidence retrieval refuses to answer.
- Knowledge text cannot override system instructions.
- Retrieval logs are persisted for evaluation and debugging.
- The implementation is intentionally deterministic in mock mode, which makes tests, interview demos, and evaluation reports repeatable.
- Productionizing this module would replace mock vectors with a real embedding model and move the storage column to pgvector.

## First Knowledge Documents

- Renewal SOP.
- Price objection handling.
- Exposure and ROI explanation.
- Package policy.
- Call quality rules.
- Competitor comparison.
- Follow-up cadence.
- Local-services industry playbook.
