# `com.cka.core` — the domain model

The nouns of the PRD, as Java records and enums: what was captured, what it was summarised to,
where it was stored, which work items it mentioned, and what was done about it. The event
payloads that carry these between stages are in [`event/`](event/AGENTS.md).

## What belongs here

- **Records and enums that name a PRD concept**, using the glossary's term for it
  ([`mvp-tickets.md`](../../../../../../../../docs/prd/mvp-tickets.md#glossary-additions)). If the
  PRD has a word for it, the type is called that.
- **Nothing that depends on Spring, Jackson or a vendor SDK.** This package must compile with
  only the JDK. Persistence, JSON and mapping annotations go in the provider that needs them, not
  on these types.
- **No behaviour beyond what a value can answer about itself** — `Mention.matched()` is fine;
  anything that needs a provider is a stage's job ([`pipeline`](../pipeline/AGENTS.md)).

## Conventions specific to this package

- **`null` means "absent" on exactly the fields whose javadoc says so** —
  `CapturedContent.platformSummary`, `WorkItem.description`, `WorkItem.parentId`,
  `Mention.workItemId`. No `Optional` record components: it is not serialisable as-is and Java's
  own guidance is to use it for return types only.
- **A provider's own vocabulary never lands here.** A GitHub issue, a Jira epic and a Drive file
  are mapped to these types in their provider. `WorkItem.providerTypeName` exists precisely so the
  provider's label survives that mapping without a vendor type leaking in (PRD 7.9).
- **Extend a type when its ticket needs it, not before.** `AuditEntry` is deliberately thin —
  T14 adds provider, model, confidence, decider and prompt version. `ActionType` lists only the
  MVP's `COMMENT` and `CLOSE`; add a type alongside the work item provider operation that performs
  it.

## Gotchas

- **`WorkItemType` and `providerTypeName` are independent.** Never derive one from the other in
  the core. A mapper sets both; the round trip back to the provider uses the provider's label.
