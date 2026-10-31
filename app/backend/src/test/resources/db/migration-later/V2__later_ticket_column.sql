-- Stands in for the migration T11 or T14 will add. It lives under a test-only location, not
-- beside the real migrations, so running it proves the mechanism without inventing a column
-- those tickets have not designed yet.
ALTER TABLE audit_entry ADD COLUMN decided_by TEXT;
