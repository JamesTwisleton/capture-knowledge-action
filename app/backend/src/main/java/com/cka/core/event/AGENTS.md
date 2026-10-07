# `com.cka.core.event` — the event payloads

What travels on the event bus. Six events, exactly the PRD's six (Section 7.1), as records
implementing the sealed `PipelineEvent`:

| Event | Published by | Consumed by |
|---|---|---|
| `ContentCaptured` | `CaptureStage`, from the content listener | `SummariseStage` |
| `ContentSummarised` | `SummariseStage`, from the LLM provider | `KnowledgeStage` |
| `KnowledgeStored` | `KnowledgeStage`, once the write completes | `ActionStage` |
| `ActionProposed`, `ActionApplied`, `ActionFailed` | the Action stage | nothing in the pipeline — terminal |

`AuditTrail` subscribes to all six, as a sink rather than a consumer.

## Conventions specific to this package

- **Every event carries the `chainId` of its action decision chain.** It is minted once, by
  `CaptureStage`, and copied forward by every stage. An event without it breaks traceability from
  an action back to its recording (PRD 7.8).
- **An event carries what its consumer needs, so the consumer never reaches back.**
  `ContentSummarised` carries the content as well as the summary because the knowledge provider
  stores the transcript; `KnowledgeStored` carries both because mention detection reads them.
- **Adding an event is a PRD change, not a code change.** The sealed interface makes that
  visible: add a record to `permits` and every exhaustive `switch` (see `AuditTrail`) stops
  compiling until it handles it — which is the point.

## Gotchas

- **These will be serialised onto RabbitMQ from T07.** Keep them plain records of `core` types,
  so they map to JSON without annotations here. Polymorphic type information for the sealed
  interface belongs in the event bus implementation's serialiser, not on these types.
