# `com.cka.provider.prompt` — prompt store tests

Tests for [`main/.../provider/prompt`](../../../../../../main/java/com/cka/provider/prompt/AGENTS.md).

`SqlPromptStoreTest` runs against a **real SQLite database** in a temp directory, for the reason
T10 gives: the migration, the seed and the version ordering are the whole of this layer, and they
are what differs between one SQL database and another. It covers T11's acceptance criteria
directly — created, edited into a new version, fetched by content type, a previous version still
retrievable.

- **Every test works on its own content type.** One database file is shared by the class, as one
  Spring context is, and a version number counts *within* a content type — so a test reusing
  another's type would assert on versions another test wrote. Pick a new type, not a new number.
- **The seeded prompts are asserted on, not around.** That CKA "ships with" a Sprint Refinement
  prompt and a General fallback (PRD 7.6) is a promise of the migration, so two tests read them
  back rather than trusting the INSERT.
- **The broker is switched off, not mocked**, with `cka.provider.eventbus=none` — same as
  `SqlAuditStoreTest`, and for the same reason: a prompt test should not need a RabbitMQ
  connection, or log warnings because there isn't one.
