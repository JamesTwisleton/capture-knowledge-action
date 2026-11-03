-- T14 widens audit_entry to the whole of the audit record PRD 7.8 and 9.3 describe: when a step
-- happened, which action it concerns, which provider and model produced it with what confidence,
-- which prompt version it came from, and who decided.
--
-- A new file rather than an edit to V1, because Flyway checksums what it has already applied —
-- changing V1 would break every database that ran it. LaterMigrationTest is the proof that this
-- path works against a database already holding rows.

-- Epoch milliseconds, not ISO-8601 text. Both are types every SQL database has, but only one
-- compares correctly: as text, "09:15:00Z" sorts after "09:15:00.500Z" (the fraction's "." is
-- below "Z"), so two entries in the same second can fall either side of a trust window's bound.
-- Milliseconds are also the unit a chain id's own timestamp is in (see ChainId).
--
-- NOT NULL with a default, because SQLite — like Postgres and MySQL — cannot add a NOT NULL
-- column to an existing table without one. The epoch is the only honest value for a row written
-- before the column existed, and nothing is ever inserted without a timestamp after this
-- migration: AuditEntryRow writes every column on every insert.
ALTER TABLE audit_entry ADD COLUMN recorded_at INTEGER NOT NULL DEFAULT 0;

-- The ActionType name, nullable: the steps before an action exists concern none. Text rather
-- than an ordinal, for the reason V1 gives for step.
ALTER TABLE audit_entry ADD COLUMN action_type TEXT;

-- Provenance, flattened to two columns rather than stored as one "jev/typesafe-1" string, so
-- "everything the LLM fallback decided" stays a query rather than a string match.
ALTER TABLE audit_entry ADD COLUMN provider TEXT;
ALTER TABLE audit_entry ADD COLUMN model TEXT;

-- REAL, and nullable: a step nobody scored has no confidence, which is not the same as 0.0.
ALTER TABLE audit_entry ADD COLUMN confidence REAL;

-- Who decided. SYSTEM carries no name; HUMAN always does (see Decider). SYSTEM is also the
-- truthful backfill for rows written before this migration — every one of them was the
-- pipeline's own work, since there is nowhere for a human to accept or reject yet.
ALTER TABLE audit_entry ADD COLUMN decider_kind TEXT NOT NULL DEFAULT 'SYSTEM';
ALTER TABLE audit_entry ADD COLUMN decider_name TEXT;

-- The prompt version behind the chain, from T11. Nothing versions prompts yet, so this stays
-- null until it lands.
ALTER TABLE audit_entry ADD COLUMN prompt_version INTEGER;

-- The trust window: accept/reject decisions for one action type since some time, newest first
-- (AuditStore.decisions). action_type leads because it is the equality and recorded_at follows
-- because it is the range; id is last so the index serves the sort as well as the search.
--
-- That last column only earns its place because the query orders by recorded_at DESC, id DESC.
-- Ordering by id alone — which is how a single chain is read — would cost a temp B-tree on every
-- call, since a range on recorded_at leaves the index out of id order. EXPLAIN QUERY PLAN says
-- so plainly: SEARCH ... USING INDEX, with no "USE TEMP B-TREE FOR ORDER BY" after it.
CREATE INDEX idx_audit_entry_decision ON audit_entry (action_type, recorded_at, id);
