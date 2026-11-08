# `com.cka.provider.knowledge` — vault tests

Tests for [`main/.../provider/knowledge`](../../../../../../main/java/com/cka/provider/knowledge/AGENTS.md).

| Class | What it proves |
|---|---|
| `MarkdownVaultKnowledgeProviderTest` | What is written, where, and what a hostile title cannot do |
| `StoreAcrossTheBusTest` | T06's acceptance criterion: an event in, a readable note and an event out |

- **A real directory, via `@TempDir`.** Nothing is mocked because there is nothing to mock: the
  whole of this provider is what it writes and where, so a stubbed filesystem would assert the
  test's own idea of the answer. A temp directory also makes the failure modes reachable — a
  vault that is a file rather than a folder is one line.
- **No `LlmProvider` appears in `StoreAcrossTheBusTest`.** The criterion says the note is written
  "with no direct call from the publisher", so its absence is the assertion; it is imported only
  to be named in a sentence saying so. Same shape as `SummariseAcrossTheBusTest` under
  [`../llm`](../llm/AGENTS.md), and for the same reason.
- **The ordering test asserts inside the subscriber.** `KnowledgeStage` publishes the moment
  `store` returns and the Action stage acts on that, so the note has to exist *when the event is
  delivered*, not eventually. Checking afterwards would pass for a provider that wrote the file
  late, which is the bug worth catching.
- **Titles are the adversarial input here.** They reach the provider from a meeting platform,
  which got them from a calendar invite, so the tests push a path traversal and a title made of
  characters Windows rejects. Both must produce a note inside the vault rather than an error or
  an escape.

## Gotchas

- **A context test that loads the whole app now writes notes.** The Knowledge stage ships wired,
  so anything with a real `EventBus` and a published `ContentSummarised` will write — point
  `cka.vault.path` at a temp directory, as `LlmProviderWiringTest` does, or the note lands in the
  repository's own vault.
