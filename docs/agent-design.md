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

## Tool Types

Read tools:

- `queryCustomerProfile`
- `queryContactHistory`
- `rankLeads`
- `searchKnowledge`
- `queryProductPackage`

Write tools:

- `createFollowupTask`
- `updateLeadStage`
- `writeContactLog`

All write tools must set `requiresConfirmation=true`.

The current demo routes `rankLeads`, `queryCustomerProfile`, `queryContactHistory`,
`searchKnowledge`, and `createFollowupTask`. Other registered write tools are kept
as extension points and are not advertised as completed demo paths.

## Output Types

- `final_answer`: no pending write action.
- `confirmation_required`: a write action is proposed and must be reviewed by the user.

## Safety Rules

- The Agent must not invent customer facts.
- CRM facts come from CRM tools.
- Policy and SOP facts come from knowledge retrieval.
- Write tools only create confirmation records on the first pass.
- Confirmation endpoints validate permissions, payload, and idempotency before writing.
