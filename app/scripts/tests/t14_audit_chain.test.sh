#!/usr/bin/env bash
# T14 — the widened action decision chain exists in this environment's database.
#
# The criterion itself ("after a full run, the chain for any applied or rejected action can be
# traced back to the original recording") is proven where the full run can actually happen:
# ActionDecisionChainTest drives every stage into the real SQLite audit store and traces both a
# rejected and an applied action back to its recording. Nothing can do that from out here yet —
# no capture, LLM, knowledge or decision provider exists (T06, T09, T12, T13), and the endpoints
# that will expose a chain are T15's. Repeating what the Java test proves would be a second
# check asserting the same thing.
#
# What only this can see is the schema reaching the database on the mounted volume, which is
# where every chain the pipeline later writes has to land.
#
# New to bash? lib.sh's header comment explains the recurring idioms used here.
set -euo pipefail
cd "$(dirname "$0")/../.."   # app/
source scripts/tests/lib.sh
FAILURES=0

DB_FILE="data/db/$(basename "${DB_PATH:-/data/db/cka.db}")"

# Asked of the running backend through Boot's own Flyway endpoint, the same way t10 asks: it
# reports what this application applied, rather than what the repository happens to contain.
REPORT=$(curl -fsS -m 5 "http://localhost:${BACKEND_PORT:-8080}/actuator/flyway" || true)

echo "$REPORT" | grep -q '"script":"V2__audit_entry_decision_chain.sql"' \
  && pass "Flyway applied V2__audit_entry_decision_chain.sql in this environment" \
  || fail "the backend reports no V2__audit_entry_decision_chain.sql migration"

# A migration can be recorded and have failed, and "it is in the list" would pass for both. Read
# as "no migration is in any state other than SUCCESS", which does not depend on the order Boot
# happens to serialise a migration's fields in — pinning the state to its script would.
echo "$REPORT" | grep -o '"state":"[A-Z_]*"' | grep -qv '"state":"SUCCESS"' \
  && fail "a migration is recorded in a state other than SUCCESS — check the backend logs" \
  || pass "every migration the backend applied is recorded as SUCCESS"

# And the columns themselves, in the file on the host volume rather than in Flyway's opinion of
# it. SQLite keeps each table's CREATE statement as text inside the database — ALTER TABLE ADD
# COLUMN rewrites it — so grep on the file answers "is the widened chain really in there" with
# no sqlite3 binary in the image, which is the dependency t10 declined to add for one query.
# -a treats the file as text; without it grep refuses to search a binary.
for column in recorded_at action_type provider model confidence decider_kind decider_name prompt_version; do
  grep -aq "$column" "$DB_FILE" \
    && pass "$DB_FILE carries the chain's $column column" \
    || fail "$DB_FILE has no $column column — did V2 run against this database?"
done

# The index the trust window reads (AuditStore.decisions): per action type, over a time bound.
grep -aq 'idx_audit_entry_decision' "$DB_FILE" \
  && pass "the decision history index is in place" \
  || fail "idx_audit_entry_decision is missing — accept/reject history would table-scan"

exit $(( FAILURES > 0 ))
