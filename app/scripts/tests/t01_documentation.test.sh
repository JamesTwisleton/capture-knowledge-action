#!/usr/bin/env bash
# T01 AC3 — the documentation exists.
set -euo pipefail
cd "$(dirname "$0")/../.."   # app/
source scripts/tests/lib.sh
FAILURES=0

for f in ../docs/docker-environment.md ../docs/AGENTS.md backend/AGENTS.md frontend/AGENTS.md; do
  [ -s "$f" ] && pass "${f#../}" || fail "${f#../} is missing or empty"
done

exit $(( FAILURES > 0 ))
