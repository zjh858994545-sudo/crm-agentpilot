# Evaluation

Evaluation must be generated from actual script runs. No metric may be hard-coded for resume display.

## Data Files

- `eval/rag_questions.jsonl`
- `eval/tool_call_cases.jsonl`
- `eval/agent_tasks.jsonl`

## Metrics

- RAG Recall@5
- Citation hit rate
- Refusal accuracy
- Tool Calling success rate
- Write confirmation coverage
- Write misfire rate
- Average latency
- P95 latency

## Report

Reports are written to:

```text
eval/reports/report-YYYYMMDD.md
```

When the backend is started from `backend/`, the runner reads cases from `../eval`. If the process cannot write to `../eval/reports`, it falls back to `backend/target/eval-reports`.

## Demo Command

```powershell
.\scripts\run-eval.ps1
```

The frontend Evaluation page calls the same backend runner through `POST /api/evaluation/run`.
