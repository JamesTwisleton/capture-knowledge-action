# `com.cka.provider.eventbus` — the event bus

`EventBus`: publish a `PipelineEvent`, subscribe by event type. T07 adds RabbitMQ here. An
in-memory implementation exists for tests only, in
[`com.cka.testdouble`](../../../../../../test/java/com/cka/testdouble/AGENTS.md).

- **Subscribing to a type receives its subtypes.** `subscribe(PipelineEvent.class, …)` gets every
  event — `AuditTrail` relies on it. A broker implementation must keep that, for example with a
  topic exchange routed by event type.
- **Keep the interface deliberately simple** (PRD 8.1). Delivery and ordering guarantees beyond
  the basics are not abstracted; don't add acknowledgements or partitions to it for one broker.
- **Events cross a process boundary from T07.** Serialise the sealed `PipelineEvent` with its
  concrete type recorded, in this package — not with annotations on the `core.event` records.
