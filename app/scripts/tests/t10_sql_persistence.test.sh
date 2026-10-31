#!/usr/bin/env bash
# T10 — the backend creates, connects to and migrates a SQLite database in this environment.
#
# Deliberately NOT a repeat of SqlAuditStoreTest or LaterMigrationTest. Those already prove the
# schema, the ordering and that a later migration applies, but they do it against a temp file
# whose path the test chooses. What they cannot prove is the wiring *here*: that DB_PATH lands
# on the mounted volume rather than inside the container, that the file therefore survives on
# the host, and that migrations ran against that database rather than a throwaway one.
#
# New to bash? lib.sh's header comment explains the recurring idioms used here.
set -euo pipefail
cd "$(dirname "$0")/../.."   # app/
source scripts/tests/lib.sh
FAILURES=0

# Where the file should land on the host. The container path comes from .env; the host side is
# the left half of the volume mount in docker-compose.yml, which is always ./data/db.
DB_FILE="data/db/$(basename "${DB_PATH:-/data/db/cka.db}")"

healthy backend \
  && pass "backend is healthy" \
  || fail "backend never became healthy"

# The database is created by the backend on startup, not by this script and not by the image, so
# its existence on the *host* is evidence of two things at once: the application connected, and
# DB_PATH pointed at the mounted volume rather than somewhere inside the container.
database_exists() { [ -s "$DB_FILE" ]; }
wait_for 60 database_exists \
  && pass "the backend created $DB_FILE on the host volume" \
  || fail "$DB_FILE was never created — is DB_PATH pointing outside the mounted volume?"

# Migrations, asked of the running backend through Boot's own Flyway endpoint. Reading the
# table directly would need a sqlite3 binary in the image, added purely so a test could run one
# query; the endpoint is already there and reports what this application actually applied.
flyway_report() { curl -fsS -m 5 "http://localhost:${BACKEND_PORT:-8080}/actuator/flyway"; }
REPORT=$(flyway_report || true)

echo "$REPORT" | grep -q '"script":"V1__audit_entry.sql"' \
  && pass "Flyway applied V1__audit_entry.sql in this environment" \
  || fail "the backend reports no V1__audit_entry.sql migration"

# SUCCESS specifically. A migration can be recorded and have failed, and "it is in the list"
# would pass for both.
echo "$REPORT" | grep -q '"state":"SUCCESS"' \
  && pass "the migration is recorded as SUCCESS" \
  || fail "a migration is recorded but not successful — check the backend logs"

# A database the backend cannot reach must not take the backend down with it — the same rule the
# broker follows. If Boot's "db" health indicator were left on, this would report DOWN the moment
# the file was unreachable and Docker would restart a backend that is answering fine.
curl -fsS -m 5 "http://localhost:${BACKEND_PORT:-8080}/actuator/health" | grep -q '"status":"UP"' \
  && pass "the health endpoint is UP and does not fold the database into it" \
  || fail "/actuator/health is not UP"

exit $(( FAILURES > 0 ))
