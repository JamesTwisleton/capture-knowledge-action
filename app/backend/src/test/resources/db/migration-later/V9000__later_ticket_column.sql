-- Stands in for the migration a later ticket will add. It lives under a test-only location, not
-- beside the real migrations, so running it proves the mechanism without inventing a table that
-- ticket has not designed yet.
--
-- V9000 so that it is always the later one. Flyway refuses two migrations claiming the same
-- version, and it orders by version, so this file only has to sort after the shipped migrations
-- — a number no real ticket will reach does that permanently, where V4 would need renumbering
-- the next time a real migration lands.
ALTER TABLE audit_entry ADD COLUMN reviewed_by TEXT;
