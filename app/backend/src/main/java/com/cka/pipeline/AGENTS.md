# `com.cka.pipeline` — the stages

The glue between providers and the bus. Each stage subscribes to one event, calls one provider,
and publishes the next event. No stage holds a reference to another, and no provider knows the
bus exists — which is what lets any stage be swapped, run alone, or driven by something else
(PRD 7.1).

| Class | On | Calls | Publishes |
|---|---|---|---|
| `CaptureStage` | whatever the content listener finds | `ContentListener` | `ContentCaptured`, minting the chain id |
| `SummariseStage` | `ContentCaptured` | `LlmProvider` | `ContentSummarised` |
| `KnowledgeStage` | `ContentSummarised` | `KnowledgeProvider` | `KnowledgeStored` |
| `ActionStage` | `KnowledgeStored` | `WorkItemProvider`, `DecisionProvider` | `ActionProposed`; unmatched mentions go to the audit store |
| `AuditTrail` | every event | `AuditStore` | nothing |

## Conventions specific to this package

- **Framework-free, like `core`.** Stages are plain classes with constructor injection
  (`@RequiredArgsConstructor`) and a `start()` that subscribes. Spring wiring lives in
  `com.cka.config` once there are real providers to wire — see
  [`provider/AGENTS.md`](../provider/AGENTS.md#selecting-an-implementation).
- **A stage is a thin adapter, not a home for logic.** If a stage grows a decision, ask whether it
  belongs in a provider (which the decision is *about*) or in a service of its own.
- **Prove decoupling by running a stage alone.** `PipelineWiringTest` drives `ActionStage` from a
  hand-published `KnowledgeStored` with nothing upstream. A new stage gets a test like that; if it
  can't be written, the stage is reaching for something it should be receiving on an event.

## Gotchas

- **`ActionStage` is the gate chain's skeleton, not the gate chain.** Every matched mention is
  proposed for a human to confirm. T08 adds the mention threshold above which a comment is posted
  automatically, and the intent, decision and trust checks for mutating actions — it extends this
  class rather than starting another.
- **Ordering is the bus's, not the stages'.** On the test suite's synchronous in-memory bus, a
  publish runs every downstream stage before it returns, so the order handlers subscribed in
  decides the order audit entries land in. A real broker (T07) is asynchronous. Don't write
  production code that relies on either.
