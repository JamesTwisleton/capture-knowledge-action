# `com.cka.persistence` — the SQL foundation

The shared database layer (T10) that the audit store with its action decision chain (T14) and the
prompt store (T11) are both built on. **Spring Data JDBC**, **Flyway**, **SQLite by
default**. No repository or table lives here — those belong to whichever capability owns the
data, under [`com.cka.provider`](../provider/AGENTS.md).

| | |
|---|---|
| Database | SQLite, file at `DB_PATH` (default `./data/db/cka.db`; Compose mounts `/data/db`) |
| Data access | Spring Data JDBC — repositories and derived queries, not hand-written SQL |
| Migrations | Flyway, `src/main/resources/db/migration`, applied at startup |
| Connection pool | Hikari, **one** connection (see below) |
| Reachability | `DatabaseHealth.check()`, shared by every store on this layer |

## Conventions specific to this package

- **A new table or column is a new migration file, never an edit to an existing one.** Flyway
  checksums what it has applied, so changing `V1__audit_entry.sql` breaks every database that
  already ran it. Widening `audit_entry` meant a `V2…` beside it, not an edit to it.
  `LaterMigrationTest` is what proves that works against a database already holding rows; it
  derives its stand-in migration's version from the database, so adding a real one never
  collides with it and never needs an edit there.
- **Adding a `NOT NULL` column needs a default, on every engine worth substituting.** SQLite,
  Postgres and MySQL all refuse one without, since the rows already there have to be given
  something. Pick the value that is true of those rows rather than a convenient one — `V2`
  backfills `decider_kind` as `SYSTEM` because every entry written before it was the pipeline's
  own work.
- **Prefer a derived query to SQL.** Spring Data JDBC renders them *through the dialect*, so
  `findByChainIdOrderByIdAsc` is correct SQL for whichever database is configured. A hand-written
  string is frozen SQL, and it is where a SQLite-ism would quietly leak in and break the
  substitutability the ticket asks for.
- **Keep the domain free of the database.** `core` records carry no `@Id`, no `@Table` and no
  surrogate key — a row type next to the repository does that, and converts at the boundary.
  `AuditEntryRow` is the pattern.
- **Column types are the ones every SQL database has.** A UUID is `TEXT`, an enum is its name
  (not its ordinal, which reordering the enum would silently reinterpret), and an instant is
  `INTEGER` epoch milliseconds. Both of the last two are portable, but only the integer compares
  correctly: as ISO-8601 text, `09:15:00Z` sorts *after* `09:15:00.500Z`, because the fraction's
  `.` is below `Z` — so two entries in the same second can fall either side of a window's bound.

## Gotchas

- **A comment-only edit to an applied migration still breaks it.** Flyway checksums the whole
  file, so correcting a stale sentence in `V2` fails validation on every database that already
  ran it — including the dev database `RabbitEventBusTest` and `HealthEndpointTest` connect to,
  where it surfaces as an unrelated broker test failing at class initialisation. Put the
  correction in the Javadoc or an `AGENTS.md`; a migration is a historical record, and prose that
  has aged inside one is the price of that.
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
