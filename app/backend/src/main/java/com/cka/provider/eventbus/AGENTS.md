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

- **Subscribing to a type receives its subtypes**, which is how `AuditTrail` catches all six with
  one subscription. In memory that is `Class.isInstance`; here it is the wildcard binding, which
  is why the exchange has to stay a topic.
- **One queue per subscriber, never a shared one.** Sharing one makes it a work queue, and each
  subscriber then sees about half the pipeline.
- **Declare the queue and binding before starting the listener**, or `subscribe()` is racy and
  every test that subscribes then publishes goes intermittently flaky.
- **Type information stays here, not on the records.** `com.cka.core.event` must compile with
  nothing but the JDK on the classpath, so the mapping from event to wire identity lives in
  `RabbitEventBusConfiguration` and is derived from the sealed interface's permitted subclasses —
  a seventh event needs no edit.
- **`DefaultClassMapper` is a bean here on purpose**, not a local inside the converter. Spring
  runs `afterPropertiesSet()` only on something it registers, and that call is what inverts the id
  map into the class-to-name direction publishing needs. As a local the step is skipped in
  silence, fully-qualified Java class names go on the wire, and the consumer rejects them as an
  untrusted package — a failure that surfaces on the far side of the broker, nowhere near its
  cause. The javadoc carries the reason.
- **The `RabbitTemplate` is Boot's, not one declared here.** Auto-configuration applies the
  `MessageConverter` bean to it, and the `spring.rabbitmq.template.*` properties besides, which a
  hand-declared template would silently ignore. It resolves the converter with `getIfUnique()`,
  so keep this package to exactly one `MessageConverter` bean: a second one makes that return
  nothing, and publishing drops back to Java serialisation without a word.

## Gotchas

- **Queues are auto-delete and non-durable, so an event published while the app is down is
  lost.** Acceptable for now, and worth restating rather than discovering: nothing publishes
  while the app is down either, since every producer is in this process. It stops being true the
  moment a producer lives outside it, and that is the point to revisit durability — along with
  whether `subscribe` should take a subscriber name so queues can be stable and durable.
- **Boot's RabbitMQ health indicator is off**, by way of `management.health.rabbit.enabled:
  false`, so a broker hiccup cannot fail the healthcheck that Compose and T04 read as "is the
  backend up". Bus reachability is `checkHealth()` instead; the reasoning behind that split is in
  [`../AGENTS.md`](../AGENTS.md).
