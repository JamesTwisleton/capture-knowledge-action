#!/usr/bin/env bash
# T03 — a frontend edit reaches the browser without an image rebuild.
# New to bash? lib.sh's header comment explains the recurring idioms used here.
set -euo pipefail
cd "$(dirname "$0")/../.."   # app/
source scripts/tests/lib.sh
FAILURES=0

PAGE=frontend/app/page.tsx
# mktemp creates a new, guaranteed-unique empty file (typically under /tmp) and
# prints its path — used here purely as a safe place to stash a backup copy.
PAGE_BACKUP=$(mktemp)
cp "$PAGE" "$PAGE_BACKUP"
# Runs in parallel with the other checks against the same file, so an interrupted run
# must restore it itself rather than relying on a shared cleanup elsewhere.
#
# `trap COMMAND EXIT` registers COMMAND to run automatically when this script exits,
# for ANY reason — normal completion, an error under set -e, or being interrupted —
# so the real source file gets its original content back no matter how this ends.
#
# `cp` into the existing file, deliberately NOT `mv` over it. mv replaces the file
# itself, so the page inherits the backup's identity: mktemp creates mode 600 owned by
# whoever ran the script, which leaves page.tsx readable only by that user. The
# container's dev server runs as UID 1000, so on CI — where a native bind mount really
# does enforce the UID mismatch, unlike Docker Desktop's mount layer — Turbopack then
# cannot read the page it is serving, and the frontend starts answering 500 to
# everything. cp writes through to the original file instead, so its mode and owner
# survive. (This is the same class of bug as the chmod in verify-acceptance.sh, and it
# was invisible until a check ran *after* this one.)
trap 'cp -f "$PAGE_BACKUP" "$PAGE"; rm -f "$PAGE_BACKUP"' EXIT

# $$ is this running script's own process ID — a cheap, always-available way to make
# the marker text unique, so a stale probe from a previous run couldn't be mistaken
# for this run's own edit.
MARK="hot-reload-probe-$$"
# sed "s|OLD|NEW|" FILE substitutes the first occurrence of OLD with NEW on each line
# and prints the result (the file itself is untouched unless redirected, as it is
# here into $PAGE). The delimiter is normally "/", but "|" is used instead simply
# because it doesn't appear in either OLD or NEW, so nothing needs escaping.
sed "s|Frontend stub|$MARK|" "$PAGE_BACKUP" > "$PAGE"
visible() { curl -fsS -m 10 "http://localhost:${FRONTEND_PORT}" 2>/dev/null | grep -q "$MARK"; }
wait_for 120 visible \
  && pass "edit to page.tsx served without an image rebuild" \
  || fail "edit never appeared — WATCHPACK_POLLING is probably not set"

exit $(( FAILURES > 0 ))
