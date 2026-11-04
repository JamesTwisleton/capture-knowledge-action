# `com.cka.provider.audit` — the audit store

`AuditStore`: record an `AuditEntry`, read back a whole action decision chain by its id, and read
the human accept/reject decisions for one action type over a window. `SqlAuditStore` is the
implementation, on Spring Data JDBC over SQLite — the shared foundation lives in
[`com.cka.persistence`](../../persistence/AGENTS.md). T10 built the layer; T14 widened the table
and the record to everything PRD 9.3 requires and added the decision history.

| | |
|---|---|
| Table | `audit_entry`, created by `V1__audit_entry.sql`, widened by `V2__audit_entry_decision_chain.sql` |
| Ordering column | `id`, `INTEGER PRIMARY KEY AUTOINCREMENT` |
| Selected by | `cka.provider.audit=sql` (`CKA_PROVIDER_AUDIT`) |
| Written by | `AuditTrail` only — see [`pipeline/AGENTS.md`](../../pipeline/AGENTS.md) |
| Health | `DatabaseHealth.check()`, shared with the prompt store |

- **`chain()` returns entries in the order they were recorded.** The activity view and the "trace
  any action back to its recording" criterion (T14) both read it that way, so a SQL implementation
  needs an explicit ordering column, not insertion order by accident. That column is `id`, and
  it is `AUTOINCREMENT` rather than a plain `INTEGER PRIMARY KEY` so SQLite never reuses the id
  of a deleted row and lets a later entry sort before an earlier one. Not a timestamp: two
  entries written in the same millisecond would tie — including now that every entry has one.
- **`decisions()` is the human half of the chain, and the only query that spans chains.** Accept
  and reject both, per action type, since a time: one query, two consumers — the trust ramp's
  per-action-type score (PRD 9.2) and the front end's accept/reject history (the J5 groundwork).
  The steps it reads are `AuditStep.humanDecisions()`, so an action CKA applied by itself is not
  counted as a person's opinion of it.
- **Columns exist for what is queried; everything else is `detail`.** A chain, a trust window and
  an action type are columns. How a step is phrased is not — `detail` is what triage and the
  activity view show a person, and the one place its wording is decided is `AuditTrail`.
- **Widen the table with a new migration, never by editing an applied one.** Flyway checksums
  what it has applied, so `V2__audit_entry_decision_chain.sql` is how the decision-chain columns
  arrived; the next columns arrive the same way, under the next free version.
- **`AuditEntryRow` flattens two core records into four columns** — `Provenance` to
  `provider`/`model`, `Decider` to `decider_kind`/`decider_name`. Not for the sake of normalising:
  it is what makes "everything this model decided" and "everything James rejected" ordinary
  queries rather than string matches.
