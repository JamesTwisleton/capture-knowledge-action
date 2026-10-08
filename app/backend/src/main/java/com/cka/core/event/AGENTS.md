# `com.cka.core.event` — the event payloads

What travels on the event bus. Six events, exactly the PRD's six (Section 7.1), as records
implementing the sealed `PipelineEvent`:

| Event | Published by | Consumed by |
|---|---|---|
| `ContentCaptured` | `CaptureStage`, from the content listener | `SummariseStage` |
| `ContentSummarised` | `SummariseStage`, from the LLM provider | `KnowledgeStage` |
| `KnowledgeStored` | `KnowledgeStage`, once the write completes | `ActionStage` |
| `ActionProposed`, `ActionApplied`, `ActionFailed` | the Action stage | nothing in the pipeline — terminal |

"Consumed by" means the stage that picks the event up and carries the pipeline on. `AuditTrail`
subscribes to all six as well, but only to write a record: it publishes nothing and no stage waits
for it, so deleting it would leave the pipeline running end to end. That is why the action events
are still **terminal** above despite having a subscriber — nothing downstream does pipeline work
because of them. PRD 7.1 calls the audit service a cross-cutting *sink* for this reason.

> Mind the word *consumer* here. In this table it means that next stage. From T07 the bus is
> RabbitMQ, where *consumer* is the term for any subscriber at all — and by that meaning
> `AuditTrail` is certainly one.

## Conventions specific to this package

- **Every event carries the `chainId` of its action decision chain.** It is minted once, by
  `CaptureStage`, and copied forward by every stage. An event without it breaks traceability from
  an action back to its recording (PRD 7.8).
- **An event carries what the stage handling it needs, so that stage never reaches back.**
  `ContentSummarised` carries the content as well as the summary because the knowledge provider
  stores the transcript; `KnowledgeStored` carries both because mention detection reads them.
- **Adding an event is a PRD change, not a code change.** The sealed interface makes that
  visible: add a record to `permits` and every exhaustive `switch` (see `AuditTrail`) stops
  compiling until it handles it — which is the point.

## Gotchas

- **These will be serialised onto RabbitMQ from T07.** Keep them plain records of `core` types,
  so they map to JSON without annotations here. Polymorphic type information for the sealed
  interface belongs in the event bus implementation's serialiser, not on these types.
