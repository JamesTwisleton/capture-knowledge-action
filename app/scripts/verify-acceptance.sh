#!/usr/bin/env bash
# Verifies T01's acceptance criteria against a running stack. CI runs this; so can you.
#
#     ./app/scripts/verify-acceptance.sh
#
# It brings the stack up, proves each criterion, and tears down. Every check asserts
# behaviour rather than configuration: a port that is open is not a debugger, and a
# container that is running is not a service that answers.
set -euo pipefail
cd "$(dirname "$0")/.."   # app/, where docker-compose.yml lives

BACKEND_PORT="${BACKEND_PORT:-8080}"
FRONTEND_PORT="${FRONTEND_PORT:-3000}"
JVM_DEBUG_PORT="${JVM_DEBUG_PORT:-5005}"
KEEP_UP="${KEEP_UP:-0}"

pass() { printf '  \033[32mPASS\033[0m  %s\n' "$1"; }
fail() { printf '  \033[31mFAIL\033[0m  %s\n' "$1"; FAILURES=$((FAILURES + 1)); }
FAILURES=0

cleanup() {
  if [ "$KEEP_UP" = "0" ]; then
    echo "Tearing down…"
    docker compose down -v >/dev/null 2>&1 || true
  fi
}
trap cleanup EXIT

# Compose needs .env to exist; it is gitignored, so CI starts from the template.
[ -f .env ] || cp .env.example .env

echo "Building and starting the stack…"
docker compose up -d --build >/dev/null

# The compose healthchecks already encode "ready", so wait on those rather than sleeping
# and hoping. Read them via docker inspect: `compose ps --format` only learned Go
# templates in a later release than some contributors will have.
health() {
  local cid
  cid=$(docker compose ps -q "$1" 2>/dev/null) || return 0
  [ -n "$cid" ] || { echo "missing"; return 0; }
  docker inspect -f '{{if .State.Health}}{{.State.Health.Status}}{{else}}none{{end}}' "$cid" 2>/dev/null || echo "missing"
}

echo "Waiting for healthchecks…"
for _ in $(seq 1 60); do
  pending=""
  for svc in event-bus backend frontend; do
    [ "$(health "$svc")" = "healthy" ] || pending="$pending $svc"
  done
  if [ -z "$pending" ]; then break; fi
  sleep 5
done
if [ -n "$pending" ]; then echo "  never became healthy:$pending"; fi

echo
echo "AC1 — docker compose up brings up the stubbed environment"
for svc in event-bus backend frontend; do
  [ "$(health "$svc")" = "healthy" ] \
    && pass "$svc reports healthy (its healthcheck actually works)" \
    || fail "$svc never reported healthy — check its healthcheck command exists in the image"
done
for svc in backend frontend event-bus; do
  if [ "$(docker compose ps -q "$svc" | wc -l)" -gt 0 ] \
     && docker inspect -f '{{.State.Running}}' "$(docker compose ps -q "$svc")" 2>/dev/null | grep -q true; then
    pass "$svc container is running"
  else
    fail "$svc container is not running"
  fi
done
curl -fsS -m 15 "http://localhost:${BACKEND_PORT}/actuator/health" | grep -q '"status":"UP"' \
  && pass "backend serves /actuator/health (T02 requires Actuator)" \
  || fail "backend /actuator/health did not report UP"
curl -fsS -m 30 -o /dev/null "http://localhost:${FRONTEND_PORT}" \
  && pass "frontend serves a page" \
  || fail "frontend did not respond"

echo
echo "AC2 — a remote debugger can attach to the backend"
python3 - "$JVM_DEBUG_PORT" <<'PY' && pass "JDWP handshake accepted on the debug port" || fail "no JDWP handshake"
import socket, sys
s = socket.create_connection(("localhost", int(sys.argv[1])), timeout=15)
s.sendall(b"JDWP-Handshake")
reply = s.recv(14)
s.close()
sys.exit(0 if reply == b"JDWP-Handshake" else 1)
PY

echo
echo "Scope — backend hot reload (T01 scope; end-to-end since T02 exists)"
starts() { docker compose logs backend 2>/dev/null | grep -c "Started CaptureKnowledgeActionApplication" || true; }
before=$(starts)
docker compose exec -T backend touch src/main/java/com/cka/CaptureKnowledgeActionApplication.java || true
docker compose exec -T backend mvn -o -q compile >/dev/null 2>&1 || true
for _ in $(seq 1 12); do
  after=$(starts)
  if [ "${after:-0}" -gt "${before:-0}" ]; then break; fi
  sleep 5
done
[ "${after:-0}" -gt "$before" ] \
  && pass "DevTools restarted the app after an in-container compile" \
  || fail "no restart detected after compile"

echo
echo "T03 — a frontend edit appears without rebuilding the image"
PAGE=frontend/app/page.tsx
cp "$PAGE" /tmp/cka-page.bak
MARK="hot-reload-probe-$$"
# Edit the static copy the server renders, then wait for it to appear over HTTP.
sed -i.bak "s|Frontend stub|$MARK|" "$PAGE" && rm -f "$PAGE.bak"
seen=0
for _ in $(seq 1 24); do
  if curl -fsS -m 10 "http://localhost:${FRONTEND_PORT}" 2>/dev/null | grep -q "$MARK"; then seen=1; break; fi
  sleep 5
done
cp /tmp/cka-page.bak "$PAGE" && rm -f /tmp/cka-page.bak
[ "$seen" = "1" ] \
  && pass "edit to page.tsx served without an image rebuild" \
  || fail "edit never appeared — polling watch (WATCHPACK_POLLING) is probably broken"

echo
echo "AC3 — documentation exists"
for f in ../docs/docker-environment.md ../docs/AGENTS.md backend/AGENTS.md frontend/AGENTS.md; do
  [ -s "$f" ] && pass "${f#../}" || fail "${f#../} missing or empty"
done

echo
echo "Hygiene — secrets must not be committed"
if git ls-files --error-unmatch .env >/dev/null 2>&1; then  # cwd is app/
  fail ".env is tracked by git — it holds credentials from T20 onwards"
else
  pass ".env is not tracked"
fi
[ -s .env.example ] && pass ".env.example is committed as the template" || fail ".env.example missing"

echo
if [ "$FAILURES" -eq 0 ]; then
  echo "All acceptance criteria verified."
else
  echo "$FAILURES check(s) failed."
  exit 1
fi
