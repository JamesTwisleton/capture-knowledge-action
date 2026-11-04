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
| `ActionStage` | `KnowledgeStored` | `WorkItemProvider`, `DecisionProvider` | `ActionProposed`; unmatched mentions go to the audit service |
| `AuditTrail` | every event, plus direct calls | `AuditStore` | nothing |

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

## `AuditTrail` is the audit service, and the only thing that writes a chain

T10 left the question open and T14 answered it: the three steps with no event — an unmatched
mention, and a human's accept or reject in triage — are methods on `AuditTrail`, not entries a
caller builds and hands to the store. `ActionStage` calls `mentionUnmatched`; T15's endpoints
call `accepted` and `rejected`.

- **Writing the store directly was fine while an entry was three fields.** At nine, two writers
  are two vocabularies, and the one that forgets the action type costs the trust ramp its data
  (PRD 9.2) without anything failing. One writer, one place that decides how a step is phrased.
- **It stays a bus sink for everything that *is* an event**, which is most of the chain. That is
  what keeps it invisible to the stages: none of them knows the chain is being written, and the
  one that calls it does so for a step the bus cannot carry.
- **It takes a `Clock`.** Every entry is stamped by the service, not by the database, so a test
  can pin a whole chain to one instant — and the trust window has a column it can trust.

## Gotchas

- **`ActionStage` is the gate chain's skeleton, not the gate chain.** Every matched mention is
  proposed for a human to confirm. T08 adds the mention threshold above which a comment is posted
  automatically, and the intent, decision and trust checks for mutating actions — it extends this
  class rather than starting another.
- **Ordering is the bus's, not the stages'.** On the test suite's synchronous in-memory bus, a
  publish runs every downstream stage before it returns, so the order handlers subscribed in
  decides the order audit entries land in. A real broker (T07) is asynchronous. Don't write
  production code that relies on either — and in a test, assert the chain's causal spine as a
  *subsequence*, not as an exact list: where an unmatched mention falls among the proposals from
  the same knowledge is the decision provider's iteration order, which nothing promises.
- **The stages are still not Spring beans, and `SummariseStage` is wired all the same.** T12
  builds and starts it in [`com.cka.config`](../config/AGENTS.md) on `ApplicationReadyEvent`;
  the class itself keeps no annotations, which is what lets `PipelineWiringTest` assemble the
  whole chain from mocks with no container. The other three wait on T06, T08 and T09.
- **Subscribing at startup against an unreachable broker fails, deliberately** — the question
  this file previously left open. `EventBus.subscribe` declares its queue before attaching a
  listener, and that queue is anonymous and auto-delete, so a caught failure leaves no queue and
  nothing to recreate it when the broker returns: a backend that is up, healthy, and silently
  dropping every recording. The general rule that a provider being down must not take the backend
  down still holds everywhere the degraded state can recover; this is the one place it cannot.
