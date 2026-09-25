#!/usr/bin/env bash
# T03 — a frontend edit reaches the browser without an image rebuild.
set -euo pipefail
cd "$(dirname "$0")/../.."   # app/
source scripts/tests/lib.sh
FAILURES=0

PAGE=frontend/app/page.tsx
PAGE_BACKUP=$(mktemp)
cp "$PAGE" "$PAGE_BACKUP"
# Runs in parallel with the other checks against the same file, so an interrupted run
# must restore it itself rather than relying on a shared cleanup elsewhere.
trap 'mv -f "$PAGE_BACKUP" "$PAGE"' EXIT

MARK="hot-reload-probe-$$"
sed "s|Frontend stub|$MARK|" "$PAGE_BACKUP" > "$PAGE"
visible() { curl -fsS -m 10 "http://localhost:${FRONTEND_PORT}" 2>/dev/null | grep -q "$MARK"; }
wait_for 120 visible \
  && pass "edit to page.tsx served without an image rebuild" \
  || fail "edit never appeared — WATCHPACK_POLLING is probably not set"

exit $(( FAILURES > 0 ))
