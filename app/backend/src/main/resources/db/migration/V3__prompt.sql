-- Summary prompts as first-class data (T11), because PRD 7.6 is explicit that they are data and
-- not configuration: keyed by content type, versioned, every change appending a version rather
-- than replacing one. A version number recorded against a summary (see AuditEntry.promptVersion)
-- is only worth recording if the text it names can still be read back.
CREATE TABLE prompt (
    -- A surrogate key because Spring Data JDBC needs one, and nothing more: nothing reads it, and
    -- unlike audit_entry this table has an explicit version column, so no ordering depends on it.
    -- Plain INTEGER PRIMARY KEY for that reason — audit_entry needs AUTOINCREMENT so a deleted
    -- row's id is never reused and cannot sort a later entry first, and the cost of that here
    -- would be a sqlite_sequence table maintained on every insert for a column nobody reads.
    id           INTEGER PRIMARY KEY,
    -- Free text, not an enum of types: "teams add their own meeting and document types ...
    -- without writing code" (PRD 7.6). The MVP exercises the mechanism with one meeting type.
    content_type TEXT    NOT NULL,
    -- Counted within the content type, from 1. Across types it would depend on what other
    -- teams edited, and "version 4 of the refinement prompt" would stop meaning anything.
    version      INTEGER NOT NULL,
    instructions TEXT    NOT NULL,
    -- Two writers racing for the same next version collide here rather than one quietly losing
    -- its edit. It also indexes (content_type, version), which is the only access path this
    -- table has — newest version for a type, and the type's history — so no second index.
    UNIQUE (content_type, version)
);

-- "CKA ships with sensible default prompts" (PRD 7.6). Seeded by the migration, so a fresh
-- database has them before anything runs; editing one through the API appends version 2 and
-- leaves this text in place.
INSERT INTO prompt (content_type, version, instructions) VALUES
('Sprint Refinement', 1,
'You are summarising a sprint refinement meeting for a software team that was not there.

Write Markdown under these headings, and leave a heading out entirely if the meeting did not cover it:

- **Items discussed** — one bullet per work item, named by the identifier said aloud where there was one, and what was decided about it.
- **Estimates and acceptance criteria** — what changed, and from what to what.
- **Deferred** — what was explicitly put off, and why.
- **Open questions** — what was left unresolved, and who was to follow it up.

Stay with what was actually said. Do not infer a decision that was not stated: where the discussion trailed off without concluding, say so. Quote a participant only where the exact words matter.'),
('General', 1,
'Summarise this content for a software team that was not there.

Write Markdown. Lead with what was decided or changed, then what is still open, then anything that needs a follow-up and who it falls to.

Stay with what the content actually says. Do not infer a decision that was not stated, and name work items by the identifiers the text uses. Where there are no clear decisions, say what the content was about instead of manufacturing some.');
