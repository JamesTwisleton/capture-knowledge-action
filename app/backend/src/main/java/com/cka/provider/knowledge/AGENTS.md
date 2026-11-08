# `com.cka.provider.knowledge` — knowledge providers

`KnowledgeProvider.store`: one page per captured item, holding its transcript, summary and a link
to the source, returning a `PageReference`. `MarkdownVaultKnowledgeProvider` (T06) is the
implementation — Obsidian-compatible, not Obsidian-dependent.

| | |
|---|---|
| Written to | `cka.vault.path` (`VAULT_PATH`), a folder of `.md` files, one per item |
| Selected by | `cka.provider.knowledge=markdown-vault`, and it ships selected |
| Read by | the `KnowledgeStage` subscription wired in [`com.cka.config`](../../config/AGENTS.md) |

- **Nothing here knows Obsidian exists.** The output is plain Markdown that happens to follow
  conventions Obsidian understands: YAML front matter at the very top, which it reads as a note's
  properties, and `[[Sprint Refinement]]` for the content type, which gives every type a page
  listing its notes without anything maintaining an index. The same file opens in any editor and
  drops straight into an LLM's context window, which is the PRD's reason for a folder of files
  over a database (7.3).
- **A title is not a path.** Titles arrive from a meeting platform, which got them from whatever
  someone typed into a calendar invite. Separators and the characters Windows rejects are
  replaced, leading dots and spaces trimmed, and the length capped — so `../` in an invite cannot
  reach a directory the vault does not own, and a note written on Linux still opens on Windows.
- **Two meetings called "Stand-up" are the normal case, not a conflict.** The second becomes
  `Stand-up (2026-11-08 143205).md` — named for when it was stored, which says *which* stand-up
  where a counter would only say it was not the first. Bracketed because titles often carry a
  date already, and two bare dates in one filename tell a reader nothing about which is which.
  A counter still backs it up for two notes stored in the same second. Overwriting would mean
  knowledge silently replacing knowledge, which is worse than none.
- **Written to a temp file and moved into place.** Obsidian watches the vault and opens what it
  finds, so writing in place means someone can open a half-written note; a move is atomic. The
  channel is forced before the move, because writing a file only hands the bytes to the OS, and
  `store` must not return until the note is durable — `KnowledgeStage` publishes knowledge stored
  the moment it does, and the Action stage acts on that.
- **The health check writes**, because `Files.isWritable` answers whether permission would allow
  a write rather than whether a note can be stored. It returns `true` for a `VAULT_PATH` pointing
  at a regular file and `true` for a full filesystem — the two ways this actually breaks in a
  deployment — while catching only the read-only mount. The probe is a dot-prefixed temp file,
  which Obsidian ignores, deleted in a `finally` because the vault is somebody's own folder and
  the front end polls this.

- **Return only once the write is durable.** `KnowledgeStage` publishes knowledge stored as soon as
  `store` returns, and the Action stage acts on it; a page that later fails to flush is an action
  pointing at nothing.
- **`PageReference.location` is what a person clicks** in the stored knowledge view (T16) — make it
  something that opens, not an internal key.
- **Nothing reads the page back.** Mention detection works from the `Knowledge` that
  `KnowledgeStage` puts on the event, so the page's format serves people and Obsidian, not the
  pipeline.
