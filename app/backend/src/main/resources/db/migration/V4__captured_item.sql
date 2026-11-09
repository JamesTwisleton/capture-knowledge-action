-- What the folder watcher has already handed on (T09), so a recording is captured once rather
-- than once per poll. Held here and not in memory because the process is not long-lived: Spring
-- DevTools restarts the backend on every class change, and each restart would otherwise re-read
-- the whole folder as though it were new — a second action decision chain per meeting, a second
-- summary to pay for, and a second comment on every work item.
CREATE TABLE captured_item (
    -- The meeting's title, which is what the watcher groups a meeting's files by, so it is the
    -- identity the rest of T09 already uses. The primary key rather than a surrogate: across a
    -- DevTools restart, the old context's poll and the new one's can both decide a title is new
    -- before either writes it, and the constraint is what makes
    -- that a failed insert rather than a duplicate chain.
    title       TEXT    NOT NULL PRIMARY KEY,
    -- When, so somebody looking at a folder that is not being re-read can see whether this is
    -- the reason. Milliseconds since the epoch, matching audit_entry's recorded_at.
    captured_at INTEGER NOT NULL,
    -- When the meeting's note was first seen in the knowledge store, or null until it has been.
    -- What lets "the note is gone" mean deleted rather than not written yet: a meeting is
    -- recorded here the moment it is handed on, and its note appears only after summarising,
    -- tens of seconds later — reading a missing note as a deletion in between recaptured the
    -- meeting, which wrote another note, and did it again on every poll.
    stored_at   INTEGER
);
