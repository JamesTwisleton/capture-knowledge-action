#!/usr/bin/env bash
# Proves the acceptance criteria for T01–T03 against a real running stack.
# CI runs this on every push; so can you:
#
#     ./app/scripts/verify-acceptance.sh        KEEP_UP=1 to leave the stack running
#
# Every check asserts behaviour, not configuration. An open port is not a debugger,
# and a running container is not a service that answers.
set -euo pipefail
cd "$(dirname "$0")/.."   # app/, where docker-compose.yml lives

BACKEND_PORT="${BACKEND_PORT:-8080}"
FRONTEND_PORT="${FRONTEND_PORT:-3000}"
JVM_DEBUG_PORT="${JVM_DEBUG_PORT:-5005}"
SERVICES="event-bus backend frontend"
PAGE=frontend/app/page.tsx
PAGE_BACKUP=""
FAILURES=0

pass() { printf '  \033[32mPASS\033[0m  %s\n' "$1"; }
fail() { printf '  \033[31mFAIL\033[0m  %s\n' "$1"; FAILURES=$((FAILURES + 1)); }

# Restoring the probed page belongs here, not at the end of the check: an interrupted
# run must not leave an edited source file behind.
cleanup() {
  [ -n "$PAGE_BACKUP" ] && mv -f "$PAGE_BACKUP" "$PAGE"
  [ "${KEEP_UP:-0}" = "1" ] && return 0
  echo "Tearing down…"
  docker compose down -v >/dev/null 2>&1 || true
}
trap cleanup EXIT

# Poll a command until it succeeds or the budget runs out.
wait_for() {
  local deadline=$(( SECONDS + $1 )); shift
  while [ "$SECONDS" -lt "$deadline" ]; do "$@" && return 0; sleep 5; done
  return 1
}

# Compose's own healthchecks already encode "ready", so trust those rather than
# sleeping and hoping. Read them with docker inspect: `compose ps --format` only
# learned Go templates in a release newer than some contributors will have.
healthy() {
  local id
  id=$(docker compose ps -q "$1" 2>/dev/null) || return 1
  [ -n "$id" ] || return 1
  [ "$(docker inspect -f '{{if .State.Health}}{{.State.Health.Status}}{{end}}' "$id" 2>/dev/null)" = "healthy" ]
}
all_healthy() { for s in $SERVICES; do healthy "$s" || return 1; done; }

# Compose needs a .env; it is gitignored, so CI starts from the committed template.
[ -f .env ] || cp .env.example .env

echo "Building and starting the stack…"
for attempt in 1 2 3; do
  docker compose up -d --build >/dev/null && break
  [ "$attempt" = 3 ] && { echo "compose up failed three times — giving up"; exit 1; }
  # Docker Hub resets connections on CI runners often enough to be worth retrying.
  echo "  registry hiccup, retrying…"
  sleep $(( attempt * 15 ))
done

echo "Waiting for healthchecks…"
wait_for 300 all_healthy || echo "  not everything went healthy; checks below will say which"

echo
echo "AC1 — the environment comes up and answers"
for svc in $SERVICES; do
  healthy "$svc" \
    && pass "$svc is healthy" \
    || fail "$svc never became healthy — does its healthcheck command exist in the image?"
done
curl -fsS -m 15 "http://localhost:${BACKEND_PORT}/actuator/health" | grep -q '"status":"UP"' \
  && pass "backend serves /actuator/health (Actuator, as T02 requires)" \
  || fail "backend /actuator/health did not report UP"
curl -fsS -m 30 -o /dev/null "http://localhost:${FRONTEND_PORT}" \
  && pass "frontend serves a page" \
  || fail "frontend did not respond"

echo
echo "AC2 — a debugger can attach to the backend"
python3 - "$JVM_DEBUG_PORT" <<'PY' && pass "JDWP handshake accepted" || fail "no JDWP handshake — the port may be open but dead"
import socket, sys
with socket.create_connection(("localhost", int(sys.argv[1])), timeout=15) as s:
    s.sendall(b"JDWP-Handshake")
    sys.exit(0 if s.recv(14) == b"JDWP-Handshake" else 1)
PY

echo
echo "T01/T02 — a backend edit restarts the app without an image rebuild"
restarts() { docker compose logs backend 2>/dev/null | grep -c "Started CaptureKnowledgeActionApplication" || true; }
before=$(restarts)
restarted() { [ "$(restarts)" -gt "$before" ]; }
docker compose exec -T backend touch src/main/java/com/cka/CaptureKnowledgeActionApplication.java
docker compose exec -T backend mvn -o -q compile >/dev/null 2>&1 || true
wait_for 90 restarted \
  && pass "DevTools restarted the app after an in-container compile" \
  || fail "no restart detected — DevTools may not be watching target/classes"

echo
echo "T03 — a frontend edit reaches the browser without an image rebuild"
MARK="hot-reload-probe-$$"
PAGE_BACKUP=$(mktemp)
cp "$PAGE" "$PAGE_BACKUP"
sed "s|Frontend stub|$MARK|" "$PAGE_BACKUP" > "$PAGE"
visible() { curl -fsS -m 10 "http://localhost:${FRONTEND_PORT}" 2>/dev/null | grep -q "$MARK"; }
wait_for 120 visible \
  && pass "edit to page.tsx served without an image rebuild" \
  || fail "edit never appeared — WATCHPACK_POLLING is probably not set"

echo
echo "AC3 — the documentation exists"
for f in ../docs/docker-environment.md ../docs/AGENTS.md backend/AGENTS.md frontend/AGENTS.md; do
  [ -s "$f" ] && pass "${f#../}" || fail "${f#../} is missing or empty"
done

echo
echo "Hygiene — secrets stay out of git"
git ls-files --error-unmatch .env >/dev/null 2>&1 \
  && fail ".env is tracked — from T20 it holds real credentials" \
  || pass ".env is not tracked"
[ -s .env.example ] && pass ".env.example is committed as the template" || fail ".env.example is missing"

echo
[ "$FAILURES" -eq 0 ] && { echo "All acceptance criteria verified."; exit 0; }
echo "$FAILURES check(s) failed."
exit 1
