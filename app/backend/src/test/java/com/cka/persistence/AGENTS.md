# `com.cka.persistence` — SQL foundation tests

Tests for [`main/.../persistence`](../../../../../../main/java/com/cka/persistence/AGENTS.md).

`SqliteDialectTest` runs the dialect's clauses **against a real SQLite database** rather than
comparing them to expected strings. That is the point of it: the risk in a hand-written dialect is
not rendering text somebody disagrees with, it is rendering text SQLite rejects, and asserting on
the string would check the claim against itself. Nothing in the application paginates yet, so a
wrong clause would otherwise stay dormant until T14 or T16 paged something and surface a long way
from `SqliteDialect`. One test deliberately asserts the *reversed* `OFFSET`-then-`LIMIT` ordering
is rejected, so "LIMIT comes first" is a checked fact and not a comment.

`LaterMigrationTest` covers the one T10 criterion nothing else can: *"a migration added by a
later ticket applies cleanly to an existing database."* The first criterion — the app creates,
connects and migrates with no configuration — is proven by the application starting at all, and
by `SqlAuditStoreTest` asserting Flyway's own history row.

- **It drives Flyway's API directly, not a Spring context**, because the point is to migrate one
  database twice from two different sets of migrations. A single application startup cannot
  express that.
- **The later migration lives in `src/test/resources/db/migration-later`**, not beside the real
  ones. It stands in for T11's and T14's tables without inventing columns those tickets have not
  designed.
- **The database has rows in it before the second migration runs.** An empty database proves
  little; what a later migration does to existing data is the actual risk.
