-- Stands in for the migration a later ticket will add — T11's prompt tables next. It lives
-- under a test-only location, not beside the real migrations, so running it proves the
-- mechanism without inventing a table that ticket has not designed yet.
--
-- V3 because the shipped migrations now go up to V2 (T14), and Flyway refuses two migrations
-- claiming the same version. Renumber it again whenever a real one catches up.
ALTER TABLE audit_entry ADD COLUMN reviewed_by TEXT;
