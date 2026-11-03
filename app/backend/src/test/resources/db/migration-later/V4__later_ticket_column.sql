-- Stands in for the migration a later ticket will add. It lives under a test-only location, not
-- beside the real migrations, so running it proves the mechanism without inventing a table that
-- ticket has not designed yet.
--
-- V4 because the shipped migrations now go up to V3 (T11), and Flyway refuses two migrations
-- claiming the same version. Renumber it again whenever a real one catches up.
ALTER TABLE audit_entry ADD COLUMN reviewed_by TEXT;
