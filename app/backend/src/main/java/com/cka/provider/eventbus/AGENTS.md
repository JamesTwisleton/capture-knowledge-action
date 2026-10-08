# `com.cka.provider.eventbus` — the event bus

`EventBus`: publish a `PipelineEvent`, subscribe by event type. `RabbitEventBus` (T07) is the
real implementation, and the one the stack runs on. An in-memory implementation exists for
tests only, in [`com.cka.testsupport`](../../../../../../test/java/com/cka/testsupport/AGENTS.md).

| | |
|---|---|
| Exchange | `cka.events`, topic, durable |
| Routing key | `cka.event.<EventSimpleName>`, e.g. `cka.event.ContentCaptured` |
| Parent subscription | binds `cka.event.*`, which matches all six and nothing else |
| Type on the wire | the event's simple name, in the `__TypeId__` header |
| Selected by | `cka.provider.eventbus=rabbitmq` (`CKA_PROVIDER_EVENTBUS`) |

## Conventions specific to this package

- **Subscribing to a type receives its subtypes.** `subscribe(PipelineEvent.class, …)` gets every
  event, which is how `AuditTrail` catches all six with one subscription. In memory that is
  `Class.isInstance`; here it is the wildcard binding. A direct or fanout exchange would break it
  — fanout by ignoring the key, direct by requiring one binding per event, so a seventh event
  would stop being audited with nothing to notice.
- **One queue per subscriber, never a shared one.** Two subscribers on one queue is a work queue:
  the broker hands each event to whichever is free, so the knowledge provider and the audit trail
  would each see about half the pipeline. The queues are anonymous because `subscribe` carries no
  subscriber identity to name one with.
- **Declare the queue and binding before starting the listener.** The binding is what makes the
  broker keep a message, so anything published immediately after `subscribe()` returns waits in
  the queue instead of racing the consumer. Reversing the order makes every test that subscribes
  then publishes intermittently flaky.
- **Keep the interface deliberately simple** (PRD 8.1). Delivery and ordering guarantees beyond
  the basics are not abstracted; don't add acknowledgements or partitions to it for one broker.
- **Type information stays here, not on the records.** `com.cka.core.event` must compile with
  nothing but the JDK on the classpath, so the mapping from event to wire identity lives in
  `RabbitEventBusConfiguration` and is derived from the sealed interface's permitted subclasses —
  a seventh event needs no edit.

## Gotchas

- **Hand-built Spring components need `afterPropertiesSet()`.** Both
  `SimpleMessageListenerContainer` and `DefaultClassMapper` implement `InitializingBean`, and
  constructing them with `new` inside a `@Bean` method or a service means nothing calls it. Each
  fails silently and at a distance: the container simply never consumes, and the class mapper
  never inverts its id map, so publishing falls back to the fully-qualified Java class name and
  the *consumer* rejects it as an untrusted package. Both call it explicitly; keep it that way.
- **The `RabbitTemplate` is declared here on purpose.** Boot will set a `MessageConverter` bean on
  the template it auto-configures, but only if that bean exists by the time the auto-configuration
  runs — and this one is behind a `@ConditionalOnProperty`. Losing that race produced exactly the
  class-name-on-the-wire failure above.
- **`/actuator/health` does not report the broker**, by way of `management.health.rabbit.enabled:
  false`. Actuator would otherwise fold broker reachability into the endpoint the Compose
  healthcheck and T04 read as "is the backend up", so a broker hiccup would have Docker restart a
  healthy backend. Bus reachability is `checkHealth()`, which is what T16's provider status view
  reads. The same rule applies to every provider — see [`../AGENTS.md`](../AGENTS.md).
- **Queues are auto-delete and non-durable, so an event published while the app is down is
  lost.** Acceptable for now, and worth restating rather than discovering: nothing publishes
  while the app is down either, since every producer is in this process. It stops being true the
  moment a producer lives outside it, and that is the point to revisit durability — along with
  whether `subscribe` should take a subscriber name so queues can be stable and durable.
