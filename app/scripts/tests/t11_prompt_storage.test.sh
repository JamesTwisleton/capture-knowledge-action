#!/usr/bin/env bash
# T11 — the prompt table and its default prompts exist in this environment's database.
#
# SqlPromptStoreTest already proves the versioning: created, edited into a new version, fetched
# by content type, a previous version still retrievable. It does that against a temp file whose
# path it chooses, so what it cannot show is that the migration ran *here*, against the database
# on the mounted volume, and that the prompts CKA "ships with" (PRD 7.6) are actually in it.
# The endpoints that will expose them are T15's, so this is schema and seed, not behaviour.
#
# New to bash? lib.sh's header comment explains the recurring idioms used here.
set -euo pipefail
cd "$(dirname "$0")/../.."   # app/
source scripts/tests/lib.sh
FAILURES=0

DB_FILE="data/db/$(basename "${DB_PATH:-/data/db/cka.db}")"

curl -fsS -m 5 "http://localhost:${BACKEND_PORT:-8080}/actuator/flyway" | grep -q '"script":"V3__prompt.sql"' \
  && pass "Flyway applied V3__prompt.sql in this environment" \
  || fail "the backend reports no V3__prompt.sql migration"
# Deliberately not re-asserting that every migration is SUCCESS — t14_audit_chain does that once
# for the whole set, and two checks asserting one thing are one check and some noise.

# The seeded rows, in the file on the host rather than in Flyway's opinion of it. SQLite stores
# text inline, so grep answers it without a sqlite3 binary in the image — same reasoning as t10
# and t14. -a treats the file as text; without it grep refuses to search a binary.
# A phrase from the prompt body rather than the content type: "Sprint Refinement" also appears in
# a captured item's title and in the audit chain's detail text, so finding *that* would prove
# nothing about the prompt. These headings exist in one place only.
#
# Know what this does and does not say. SQLite does not zero the pages it frees, so the phrase
# would still be found in a database whose prompt rows had since been deleted — verified, not
# assumed. It asserts that the seed reached this file, which is the claim being made about a
# database the stack has just created; "the row is there now" needs the T15 endpoint to ask.
grep -aq 'Items discussed' "$DB_FILE" \
  && pass "the Sprint Refinement prompt and its instructions are seeded in $DB_FILE" \
  || fail "no Sprint Refinement prompt in $DB_FILE — did V3 run against this database?"

grep -aq 'Summarise this content for a software team' "$DB_FILE" \
  && pass "the General fallback prompt is seeded too" \
  || fail "no General fallback prompt — an untyped item would have nothing to summarise with"

exit $(( FAILURES > 0 ))
