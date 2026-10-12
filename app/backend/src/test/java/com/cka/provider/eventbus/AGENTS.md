# `com.cka.provider.eventbus` — event bus tests

Tests for [`main/.../provider/eventbus`](../../../../../../main/java/com/cka/provider/eventbus/AGENTS.md).

`RabbitEventBusTest` is T07's acceptance criterion — "a test publishes a fake event and a
subscriber receives it via the real broker" — and it runs against a real RabbitMQ started by
Testcontainers, not against the in-memory bus. The three things worth proving are exactly the
three that can differ between them: the event survives being serialised and read back, routing
reaches the right subscribers and no others, and delivery is asynchronous rather than already
finished when `publish` returns.

- **It needs a Docker daemon.** Run it with `./mvnw test` on the host; Maven inside a container
  cannot start a sibling container unless it can reach the host's Docker socket.
- **One broker for the whole class.** Starting one costs seconds and no test here depends on a
  clean broker, since each subscribes to its own fresh queue.
- **Wait on a `BlockingQueue`, never a sleep.** `next()` polls with a timeout and fails with a
  readable message when nothing arrives. A `Thread.sleep` long enough to be reliable on CI is
  long enough to make the suite tedious, and still only hides the race.
- **The composed stack is checked elsewhere**, by
  [`t07_event_bus.test.sh`](../../../../../../../../scripts/tests/t07_event_bus.test.sh). It asserts
  something this file cannot: that `EVENT_BUS_HOST`, the credentials in `.env` and the exchange
  all line up in the real environment. Boot hands Testcontainers the address, so none of that is
  exercised here. Don't duplicate either check in the other.
