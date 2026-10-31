# `com.cka.provider.audit` — the audit store

`AuditStore`: record an `AuditEntry`, read back a whole action decision chain by its id.
`SqlAuditStore` (T10) is the implementation, on Spring Data JDBC over SQLite — the shared
foundation lives in [`com.cka.persistence`](../../persistence/AGENTS.md). T14 widens
`AuditEntry` and this table to everything PRD 9.3 requires.

| | |
|---|---|
| Table | `audit_entry`, created by `V1__audit_entry.sql` |
| Ordering column | `id`, `INTEGER PRIMARY KEY AUTOINCREMENT` |
| Selected by | `cka.provider.audit=sql` (`CKA_PROVIDER_AUDIT`) |

- **`chain()` returns entries in the order they were recorded.** The activity view and the "trace
  any action back to its recording" criterion (T14) both read it that way, so a SQL implementation
  needs an explicit ordering column, not insertion order by accident. That column is `id`, and
  it is `AUTOINCREMENT` rather than a plain `INTEGER PRIMARY KEY` so SQLite never reuses the id
  of a deleted row and lets a later entry sort before an earlier one. Not a timestamp: two
  entries written in the same millisecond would tie.
- **Widen the table with a new migration, never by editing `V1`.** Flyway checksums what it has
  applied. T14 adds `V2…` beside it.
- **It is written from two places today:** `AuditTrail` for every bus event, and `ActionStage`
  directly for unmatched mentions, which have no event. T14 decides whether that split stays.
