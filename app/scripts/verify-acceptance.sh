#!/usr/bin/env bash
# Proves the acceptance criteria for T01–T03 against a real running stack.
# CI runs this on every push; so can you:
#
#     ./app/scripts/verify-acceptance.sh        KEEP_UP=1 to leave the stack running
#
# Each criterion is its own file under scripts/tests/, named to match — find out why
# one failed by opening one short file, not searching one long one. Once the stack is
# healthy they don't depend on each other, so they run in two parallel phases (read-only
# checks, then the two that force a recompile — see run_phase below for why they're
# split): the checks were never what made this slow, the cold Docker build and Maven
# resolve were.
set -euo pipefail
cd "$(dirname "$0")/.."   # app/, where docker-compose.yml lives
source scripts/tests/lib.sh

export BACKEND_PORT="${BACKEND_PORT:-8080}"
export FRONTEND_PORT="${FRONTEND_PORT:-3000}"
export JVM_DEBUG_PORT="${JVM_DEBUG_PORT:-5005}"
SERVICES="event-bus backend frontend"

cleanup() {
  [ "${KEEP_UP:-0}" = "1" ] && return 0
  echo "Tearing down…"
  docker compose down -v >/dev/null 2>&1 || true
}
trap cleanup EXIT

all_healthy() { for s in $SERVICES; do healthy "$s" || return 1; done; }

# Compose needs a .env; it is gitignored, so CI starts from the committed template.
[ -f .env ] || cp .env.example .env

# Both containers run as UID 1000, matching a real developer's usual host UID — but CI
# checks the repo out as the runner's own account, which isn't UID 1000 and leaves the
# tree group/other read-only. Docker Desktop's mount layer doesn't enforce that, which
# is why this never surfaces locally; a native Linux bind mount does. Without this the
# backend can't create target/ and the frontend can't write next-env.d.ts.
chmod -R o+rwX backend frontend

echo "Building and starting the stack…"
for attempt in 1 2 3; do
  docker compose up -d --build >/dev/null && break
  [ "$attempt" = 3 ] && { echo "compose up failed three times — giving up"; exit 1; }
  # Docker Hub resets connections on CI runners often enough to be worth retrying.
  echo "  registry hiccup, retrying…"
  sleep $(( attempt * 15 ))
done

# A harness that fails without saying why is half a harness — dump the evidence.
diagnose() {
  for s in $SERVICES; do
    healthy "$s" && continue
    local id; id=$(docker compose ps -q "$s" 2>/dev/null)
    echo
    echo "--- $s did not become healthy ---"
    [ -n "$id" ] && docker inspect -f '  state={{.State.Status}} exit={{.State.ExitCode}} health={{if .State.Health}}{{.State.Health.Status}}{{else}}none{{end}}' "$id" 2>/dev/null
    [ -n "$id" ] && docker inspect -f '{{if .State.Health}}  last probe: {{range .State.Health.Log}}{{.Output}}{{end}}{{end}}' "$id" 2>/dev/null | head -5
    docker compose logs --tail=40 --no-color "$s" 2>&1 | sed 's/^/  /'
  done
  echo
}

# CI runners start cold: no image layer cache, no warm Maven repository. Be generous.
echo "Waiting for healthchecks…"
wait_for 600 all_healthy || { echo "  not everything went healthy"; diagnose; }

FAILURES=0

# Runs the given test files in parallel and folds their results into $FAILURES.
run_phase() {
  local names=() files=() pids=()
  local t name out
  for t in "$@"; do
    name=$(basename "$t" .test.sh)
    out=$(mktemp)
    "$t" >"$out" 2>&1 &
    names+=("$name"); files+=("$out"); pids+=("$!")
  done
  local i
  for i in "${!pids[@]}"; do
    wait "${pids[$i]}" || FAILURES=$((FAILURES + 1))
    echo
    echo "${names[$i]}"
    cat "${files[$i]}"
  done
  rm -f "${files[@]}"
}

echo
echo "Running acceptance checks…"

# Phase 1: everything that only reads — safe to run together against the stack.
run_phase scripts/tests/ac1-environment.test.sh scripts/tests/ac2-debugger.test.sh \
          scripts/tests/ac3-documentation.test.sh scripts/tests/hygiene.test.sh

# Phase 2: each of these forces a real recompile (Maven, Turbopack). Running them
# alongside phase 1's HTTP probes is what made ac1-environment see sustained 500s on
# CI's weaker CPU — genuine contention, not flakiness to hide behind a longer timeout.
# They don't touch each other's files or services, so they still run together.
run_phase scripts/tests/hot-reload-backend.test.sh scripts/tests/hot-reload-frontend.test.sh

echo
[ "$FAILURES" -eq 0 ] && { echo "All acceptance criteria verified."; exit 0; }
echo "$FAILURES check file(s) failed."
exit 1
