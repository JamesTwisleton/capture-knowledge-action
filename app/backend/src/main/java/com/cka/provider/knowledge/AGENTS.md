# `com.cka.provider.knowledge` — knowledge providers

`KnowledgeProvider.store`: one page per captured item, holding its transcript, summary and a link
to the source, returning a `PageReference`. T06 adds the Markdown vault (Obsidian-compatible, not
Obsidian-dependent) here.

- **Return only once the write is durable.** `KnowledgeStage` publishes knowledge stored as soon as
  `store` returns, and the Action stage acts on it; a page that later fails to flush is an action
  pointing at nothing.
- **`PageReference.location` is what a person clicks** in the stored knowledge view (T16) — make it
  something that opens, not an internal key.
- **Nothing reads the page back.** Mention detection works from the `Knowledge` that
  `KnowledgeStage` puts on the event, so the page's format serves people and Obsidian, not the
  pipeline.
