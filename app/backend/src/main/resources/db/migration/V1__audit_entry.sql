-- The action decision chain's steps (PRD 7.8, 9.3), in the shape AuditEntry has today.
-- T14 widens both this table and the record: provider, model, confidence, decider and
-- prompt version. It does so with its own V2 migration, never by editing this file —
-- Flyway checksums what it has already applied, so changing an applied migration breaks
-- every database that ran it.
CREATE TABLE audit_entry (
    -- An explicit ordering column, which provider/audit/AGENTS.md requires of any SQL
    -- implementation: chain() returns entries "in the order they were recorded", and the
    -- order rows happen to come back in is not that. AUTOINCREMENT rather than a plain
    -- INTEGER PRIMARY KEY so SQLite never reuses the id of a deleted row, which would let
    -- a later entry sort before an earlier one.
    id       INTEGER PRIMARY KEY AUTOINCREMENT,
    -- TEXT, not a UUID type: SQLite has none, and keeping the column to the types every SQL
    -- database has is what the ticket means by dialect-portable. The ids are UUIDv7, so this
    -- column also sorts into chain-creation order for free (see ChainId).
    chain_id TEXT NOT NULL,
    -- The AuditStep name. Stored as text rather than an ordinal so that reordering the enum
    -- cannot silently reinterpret rows already written.
    step     TEXT NOT NULL,
    detail   TEXT NOT NULL
);

-- chain() filters by chain_id and orders by id, so one index covers both halves of it.
CREATE INDEX idx_audit_entry_chain ON audit_entry (chain_id, id);
