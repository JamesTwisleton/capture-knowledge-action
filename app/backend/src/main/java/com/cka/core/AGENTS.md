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
  `Mention.workItemId`, `Decider.name`, and the four fields of `AuditEntry` that only some steps
  have. `AuditEntry.confidence` is a boxed `Double` for that reason: a primitive would make
  "nobody scored this" indistinguishable from a real score of zero. No `Optional` record components: it is not serialisable as-is and Java's
  own guidance is to use it for return types only.
- **A provider's own vocabulary never lands here.** A GitHub issue, a Jira epic and a Drive file
  are mapped to these types in their provider. `WorkItem.providerTypeName` exists precisely so the
  provider's label survives that mapping without a vendor type leaking in (PRD 7.9).
- **Extend a type when its ticket needs it, not before.** `AuditEntry` grew from three fields to
  nine in T14, because PRD 9.3 asks all nine of an audit record, and `Summary` gained
  `promptVersion` in T11 — the ticket that gave a prompt a version to record. `ActionType` lists
  only the MVP's `COMMENT` and `CLOSE`; add a type alongside the work item provider operation
  that performs it. Two more are already specified and deliberately absent: the **threshold a
  proposal was compared against** (T08, so a decision made under an old threshold can still be
  read once T23 lets a human change it) and the **steps for linking, dismissing and changing a
  threshold** on `AuditStep` (T23). Writing them now would mean fields nothing sets and an enum
  branch nothing reaches — the rule above, applied to a case where the design is settled and the
  code is not.
- **A field every instance has is required, even when nothing produces one yet.**
  `Summary.promptVersion` is `@NonNull` because PRD 7.6 asks that the version used is recorded
  against every output and `PromptStore.current` always returns a prompt — so there is no honest
  summary without one. The cost is that T12 cannot forget it; the alternative is a nullable field
  that quietly stays null and a hole in the audit chain nothing can fill in afterwards.

## Gotchas

- **`WorkItemType` and `providerTypeName` are independent.** Never derive one from the other in
  the core. A mapper sets both; the round trip back to the provider uses the provider's label.
