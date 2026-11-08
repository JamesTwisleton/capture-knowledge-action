# `com.cka.provider.knowledge` — knowledge providers

`KnowledgeProvider.store`: one page per captured item, holding its transcript, summary and a link
to the source, returning a `PageReference`. `MarkdownVaultKnowledgeProvider` (T06) is the
implementation — Obsidian-compatible, not Obsidian-dependent.

| | |
|---|---|
| Written to | `cka.vault.path` (`VAULT_PATH`), a folder of `.md` files, one per item |
| Selected by | `cka.provider.knowledge=markdown-vault`, and it ships selected |
| Called by | the `KnowledgeStage` subscription wired in [`com.cka.config`](../../config/AGENTS.md) |
| Also needs | a `Clock`, so a test can pin the timestamp a clashing note is named after |

## Conventions for any knowledge provider

- **Return only once the write is durable.** `KnowledgeStage` publishes knowledge stored as soon
  as `store` returns and the Action stage acts on it, so a page that later fails to flush is an
  action pointing at nothing. Handing bytes to the operating system is not the same as having
  written them.
- **`PageReference.location` is what a person clicks** in the stored knowledge view (T16) — make
  it something that opens, not an internal key.
- **Nothing reads the page back.** Mention detection works from the `Knowledge` that
  `KnowledgeStage` puts on the event, so the page's format serves people and their tools, never
  the pipeline. That is what makes it safe to change.

## The Markdown vault

- **Nothing here knows Obsidian exists.** The output is plain Markdown that happens to follow
  conventions Obsidian understands: YAML front matter at the very top, which it reads as a note's
  properties, and `[[Sprint Refinement]]` for the content type, which gives every type a page
  listing its notes without anything maintaining an index. The same file opens in any editor and
  drops straight into an LLM's context window, which is the PRD's reason for a folder of files
  over a database (7.3).
- **A title is not a path.** Titles arrive from a meeting platform, which got them from whatever
  someone typed into a calendar invite. Separators and the characters Windows rejects are
  replaced, and leading dots and spaces trimmed, so `../` in an invite cannot reach a directory
  the vault does not own and a note written on Linux still opens on Windows.
- **The length cap is in bytes, not characters.** ext4, APFS and NTFS all limit one name to 255
  *bytes* while a UTF-8 character can be three of them, so a title in Japanese fails with
  `ENAMETOOLONG` at a third of the length a character count would allow. The cut steps back off
  continuation bytes, dropping a whole character rather than leaving half of one in a filename.
- **Two meetings called "Stand-up" are the normal case, not a conflict.** The title is never
  rewritten; a suffix is appended to the second one:

  | | |
  |---|---|
  | first | `Stand-up.md` |
  | second | `Stand-up` + `_2026-11-08_143205` + `.md` |
  | same second | `Stand-up` + `_2026-11-08_143205` + `_2` + `.md` |

  Naming it for when it was stored says *which* stand-up, where a counter would only say it was
  not the first. The name is claimed with `createFile`, not checked with `exists`:
  check-then-write leaves a window where two writers agree on a free name and the move that
  follows replaces rather than fails.
- **Nothing is added to a filename that Obsidian would have to escape in a link.** Underscores
  join the suffix, not brackets: parentheses are safe inside `[[wiki links]]` but end a
  `[text](path)` link early, and a vault with "Use [[Wikilinks]]" switched off writes the latter.
  Spaces come from the title and cannot be helped — Obsidian encodes those — so the rule governs
  what CKA *adds*. The underscores also mark where the title ends, which a space did not.
- **Written to a temp file and moved into place**, which is how the durability rule above is met
  here. Obsidian watches the vault and opens what it finds, so writing in place means someone can
  open a half-written note; a move is atomic, and the channel is forced before it. Nothing
  half-finished is left behind if any of it fails — not the temp file, and not the empty note
  whose name was claimed before the content existed.
- **The note is widened to be readable by others.** `createTempFile` creates owner-only on
  purpose and `move` carries that through, so notes shipped at mode `0600` until CI caught it:
  CKA writes from inside a container and a person opens the vault on the host, a different
  account on the same machine, which is the entire point of mounting it. Set on the temp file
  before the move, so a note is never visible with the wrong permissions. Skipped where the
  filesystem has none to set.
- **The health check writes**, because `Files.isWritable` answers whether permission would allow
  a write rather than whether a note can be stored. Measured: it returns `true` for a
  `VAULT_PATH` pointing at a regular file and `true` for a full filesystem — the two ways this
  breaks in a deployment — while catching only the read-only mount. The probe is a dot-prefixed
  temp file, which Obsidian ignores, deleted in a `finally` because the vault is somebody's own
  folder and the front end polls this.
