# `com.cka.provider.prompt` — the prompt store

`PromptStore`: the current summary prompt for a content type, the next version of one, and a
content type's history. `SqlPromptStore` (T11) is the implementation, on the shared SQL
foundation in [`com.cka.persistence`](../../persistence/AGENTS.md).

| | |
|---|---|
| Table | `prompt`, created and seeded by `V3__prompt.sql` |
| Selected by | `cka.provider.prompt=sql` (`CKA_PROVIDER_PROMPT`) |
| Read by | the LLM provider at capture time (T12); listed and edited through T15's endpoints |

- **Append-only, and that is the whole design.** Editing a prompt writes the next version beside
  the old one; nothing updates or deletes a row. `AuditEntry.promptVersion` records which version
  produced a summary, and that record is worthless if the text it names can be overwritten.
  `create` and `edit` are therefore one method, `save` — the first write is version 1.
- **Versions count within a content type**, not across the table. Across it, the number in the
  audit chain would depend on what other teams edited and "version 4 of the refinement prompt"
  would stop meaning anything. `UNIQUE (content_type, version)` is what enforces it, and it also
  turns two writers racing for the same next version into a failed write rather than a lost edit.
- **`current()` falls back to the `General` prompt** (`Prompt.FALLBACK_CONTENT_TYPE`), because
  PRD 7.6 says an item with no more specific type is summarised with the general one. It returns a
  prompt rather than an `Optional`: the Summarise stage has nothing to do without one, so a
  database with neither the type's prompt nor the fallback is a broken installation, not a case
  to branch on — and it throws saying exactly that.
- **Content types are free text, not an enum.** "Teams add their own meeting and document types
  without writing code" (PRD 7.6). The MVP exercises the mechanism with one meeting type; adding
  a type is a row, never a code change.
- **One derived query serves all three operations.** A content type's history is a handful of
  rows, so the newest version is its head and the next version number is that plus one. A second
  query to fetch only the newest would be a second thing to keep correct for no measurable gain.
