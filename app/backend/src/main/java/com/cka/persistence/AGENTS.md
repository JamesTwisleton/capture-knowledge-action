# `com.cka.persistence` — the SQL foundation

The shared database layer (T10) that the audit store uses today and that prompt storage (T11)
and the widened audit chain (T14) build on. **Spring Data JDBC**, **Flyway**, **SQLite by
default**. No repository or table lives here — those belong to whichever capability owns the
data, under [`com.cka.provider`](../provider/AGENTS.md).

| | |
|---|---|
| Database | SQLite, file at `DB_PATH` (default `./data/db/cka.db`; Compose mounts `/data/db`) |
| Data access | Spring Data JDBC — repositories and derived queries, not hand-written SQL |
| Migrations | Flyway, `src/main/resources/db/migration`, applied at startup |
| Connection pool | Hikari, **one** connection (see below) |

## Conventions specific to this package

- **A new table is a new migration file, never an edit to an existing one.** Flyway checksums
  what it has applied, so changing `V1__audit_entry.sql` breaks every database that already ran
  it. T11 and T14 add `V2…`, `V3…` beside it. `LaterMigrationTest` is what proves that works
  against a database already holding rows.
- **Prefer a derived query to SQL.** Spring Data JDBC renders them *through the dialect*, so
  `findByChainIdOrderByIdAsc` is correct SQL for whichever database is configured. A hand-written
  string is frozen SQL, and it is where a SQLite-ism would quietly leak in and break the
  substitutability the ticket asks for.
- **Keep the domain free of the database.** `core` records carry no `@Id`, no `@Table` and no
  surrogate key — a row type next to the repository does that, and converts at the boundary.
  `AuditEntryRow` is the pattern.
- **Column types are the ones every SQL database has.** A UUID is `TEXT`, an enum is its name
  (not its ordinal, which reordering the enum would silently reinterpret).

## Gotchas

- **Spring Data JDBC ships no SQLite dialect, so `SqliteDialect` is ours.** There is no fallback:
  without it `DialectResolver` throws `NoDialectException`. It is supplied as a plain `@Bean`,
  which backs off Boot's own by type — the `spring.factories` provider SPI Spring Data also
  offers does the same job in two more files. Only SQLite needs this; Postgres and the rest are
  already supported, so substituting one is a URL and a migration path, not another class.
- **The pool holds one connection on purpose.** SQLite takes a single write lock on the whole
  file, so a larger pool cannot add write concurrency — it turns waiting into `SQLITE_BUSY` on
  whichever connection loses. One connection makes the driver queue instead.
- **The driver creates the database file but not its directory.** A missing parent surfaces as
  `unable to open database file`, which names the wrong problem;
  `SqlitePersistenceConfiguration` creates it first. Under Compose the path is a mounted volume
  and always exists, so this only bites a checkout-and-run.
- **The `db` health indicator is off**, the same way the broker's is, so an unreachable database
  cannot fail the healthcheck Compose and T04 read as "is the backend up". Reachability is
  `AuditStore.checkHealth()` — see [`../provider/AGENTS.md`](../provider/AGENTS.md).
