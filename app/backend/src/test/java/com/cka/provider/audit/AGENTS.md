# `com.cka.provider.audit` — audit store tests

Tests for [`main/.../provider/audit`](../../../../../../main/java/com/cka/provider/audit/AGENTS.md).

`SqlAuditStoreTest` runs against a **real SQLite database** in a temp directory, which the T10
ticket asks for by name. A mock would assert nothing here, and H2 would assert it against the
wrong engine — the dialect, the migration and the ordering column are the whole of this layer,
and they are exactly what differs between one SQL database and another.

- **The broker provider is switched off, not mocked**, with `cka.provider.eventbus=none`. Its
  `@ConditionalOnProperty` then does not match, so the context holds the persistence layer and
  nothing wanting a RabbitMQ connection. A persistence test should not fail, or log warnings,
  because a broker is absent.
- **The database path names a directory that does not exist yet.** That is deliberate: it is what
  proves `SqlitePersistenceConfiguration` creates it, since the driver will not.
- **Ordering is asserted with details that are not in alphabetical order**, so a store that
  sorted on any column other than the ordering one fails here rather than passing by luck. See
  the ordering note in [`main/.../provider/audit/AGENTS.md`](../../../../../../main/java/com/cka/provider/audit/AGENTS.md).
