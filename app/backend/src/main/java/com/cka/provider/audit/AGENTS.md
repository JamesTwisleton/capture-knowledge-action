# `com.cka.provider.audit` — the audit store

`AuditStore`: record an `AuditEntry`, read back a whole action decision chain by its id. T10 puts
it on SQL (Spring Data JDBC, Flyway, SQLite by default); T14 widens `AuditEntry` to everything
PRD 9.3 requires.

- **`chain()` returns entries in the order they were recorded.** The activity view and the "trace
  any action back to its recording" criterion (T14) both read it that way, so a SQL implementation
  needs an explicit ordering column, not insertion order by accident.
- **It is written from two places today:** `AuditTrail` for every bus event, and `ActionStage`
  directly for unmatched mentions, which have no event. T14 decides whether that split stays.
