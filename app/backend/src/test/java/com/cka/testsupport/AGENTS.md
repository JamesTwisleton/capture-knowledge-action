# `com.cka.testsupport` — shared test fixtures

Three classes, and only three. Everything else a test needs from a provider is a Mockito mock
declared in the test itself.

| Class | Why it is a class and not a mock |
|---|---|
| `InMemoryEventBus` | It has real behaviour to get right — deliver each event to the subscribers whose type matches, including subtypes. Stubbing that would mean writing the implementation in mocks. The PRD lists in-memory as an event bus implementation "for tests" (8.1), so this is a real implementation that happens to be test-scoped, not a stand-in. |
| `Ticket` | A stand-in for a tracker's native work item, deliberately not shaped like `WorkItem` so a mapper has real converting to do. |
| `TicketMapper` | A mocked mapper is vacuous: stubbing `toWorkItem` to return a `WorkItem` asserts nothing, because the mapping *is* the thing under test. One real mapper over `Ticket` is what makes `WorkItemMapper` testable before T13 ships a real one. |

## Conventions specific to this package

- **Mock a provider; don't write a class for it.** Every other provider interface is a single
  call a stage makes and hands on, with no behaviour to fake — only a value to return. Mockito
  and AssertJ already arrive with `spring-boot-starter-test`, so a hand-written stub is code to
  maintain in place of an installed dependency, which is exactly what the Ponytail rule in the
  root [`AGENTS.md`](../../../../../../../../AGENTS.md) asks you not to do. An earlier draft of T05
  had one stub class per interface: 292 lines of near-identical shells supporting a 112-line
  test, each with its own `checkHealth()` returning the same thing.
- **Something belongs here only when a mock genuinely cannot do it** — real behaviour under test,
  or a type a mock would make vacuous. Both exceptions above are one of those. Add a fourth class
  only with that argument in hand, and write it in the table.
- **These are test-scoped.** `main` must never depend on this package.

## Gotchas

- **`InMemoryEventBus` is synchronous; a real broker is not.** A whole pipeline run finishes
  inside one `publish`, which is what lets a test assert on an ordered list of events with no
  waiting. It also means subscription order decides event order, so don't let a test pass only
  because of that when the production code guarantees nothing of the kind. T07's RabbitMQ
  implementation is asynchronous.
