# Agent Design

The Agent is a sales operations assistant for local-services CRM workflows.

## State Machine

```text
RECEIVED
  -> INTENT_ROUTING
  -> PLANNING
  -> TOOL_CALLING
  -> OBSERVING
  -> NEED_CONFIRMATION
  -> GENERATING_FINAL_ANSWER
  -> COMPLETED
  -> FAILED
```

## Routing Strategy

The main `chat` path tries LLM Tool Calling first when a real provider is configured.
The model receives the OpenAI-compatible tool schema from `ToolRegistry` and chooses
one tool plus JSON arguments. If the provider is unavailable, times out, or returns an
unknown tool, the orchestrator falls back to deterministic rule routing so the demo and
evaluation remain repeatable.

## Tool Types

Read tools:

- `queryCustomerProfile`
- `queryContactHistory`
- `analyzeCustomer`
- `rankLeads`
- `searchKnowledge`
- `queryProductPackage`

Write tools:

- `createFollowupTask`
- `updateLeadStage`
- `writeContactLog`

All write tools must set `requiresConfirmation=true`.

The current demo routes all listed tools. Read tools return final answers directly.
Write tools return `confirmation_required` and are executed only through the
confirmation endpoint.

## Output Types

- `final_answer`: no pending write action.
- `confirmation_required`: a write action is proposed and must be reviewed by the user.

## Safety Rules

- The Agent must not invent customer facts.
- CRM facts come from CRM tools.
- Policy and SOP facts come from knowledge retrieval.
- Write tools only create confirmation records on the first pass.
- Confirmation endpoints validate permissions, payload, and idempotency before writing.
