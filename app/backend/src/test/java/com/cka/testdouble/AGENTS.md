# `com.cka.testdouble` — test doubles for every provider interface

One hand-written double per provider interface, so any stage, or the whole pipeline, can be
wired without a broker, a database, an LLM or a network (T05's acceptance criterion). Reuse these
rather than writing another per test.

| Double | Implements | Behaves |
|---|---|---|
| `InMemoryEventBus` | `EventBus` | delivers synchronously; `published()` lists everything sent |
| `ManualContentListener` | `ContentListener` | finds content only when the test calls `find()` |
| `CannedLlmProvider` | `LlmProvider` | summarises anything as "Summary of" its title |
| `InMemoryKnowledgeProvider` | `KnowledgeProvider` | keeps pages in a map |
| `ScriptedDecisionProvider` | `DecisionProvider` | returns the mentions it was built with |
| `InMemoryWorkItemProvider` | `WorkItemProvider` | a fixed pool of `Ticket`s; `calls()` records comments and closes |
| `TicketMapper` | `WorkItemMapper<Ticket>` | maps a stand-in native type, the way a real provider would |
| `InMemoryAuditStore` | `AuditStore` | keeps entries in a list |

## Conventions specific to this package

- **Doubles keep the interface's promises, not just its signatures.** `ScriptedDecisionProvider`
  throws if scripted to match an item outside the pool it is given, because a real decision
  provider must never do that. A double that is laxer than the contract hides bugs in the code
  under test.
- **Hand-written, not Mockito.** A pipeline test should read as a story — find this content, see
  that proposal — and these make it one. Use a mock where you need to verify an interaction a
  double can't show.
- **Not production code.** The PRD lists an in-memory event bus "for tests"; it lives here, and
  `main` must never depend on this package.

## Gotchas

- **`InMemoryEventBus` is synchronous; a real broker isn't.** A whole pipeline run completes
  inside one `publish`, which makes tests simple and makes subscription order visible in what
  they assert. Don't let a test pass only because of that ordering when the production code
  doesn't guarantee it.
