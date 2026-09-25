#!/usr/bin/env bash
# Hygiene — secrets stay out of git.
set -euo pipefail
cd "$(dirname "$0")/../.."   # app/
source scripts/tests/lib.sh
FAILURES=0

git ls-files --error-unmatch .env >/dev/null 2>&1 \
  && fail ".env is tracked — from T20 it holds real credentials" \
  || pass ".env is not tracked"
[ -s .env.example ] && pass ".env.example is committed as the template" || fail ".env.example is missing"

exit $(( FAILURES > 0 ))
