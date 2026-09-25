#!/usr/bin/env bash
# T01 AC1 — the environment comes up and answers, including to a real browser.
set -euo pipefail
cd "$(dirname "$0")/../.."   # app/
source scripts/tests/lib.sh
FAILURES=0

for svc in event-bus backend frontend; do
  healthy "$svc" \
    && pass "$svc is healthy" \
    || fail "$svc never became healthy — does its healthcheck command exist in the image?"
done

# Retried, not one-shot: this runs alongside t01_hot_reload_backend.test.sh and
# t03_hot_reload_frontend.test.sh, which are busy recompiling and restarting these same
# two services. Both are already confirmed "healthy" above, so a single request landing
# mid-restart is a transient blip to ride out, not a real failure.
backend_up() { curl -fsS -m 5 "http://localhost:${BACKEND_PORT}/actuator/health" | grep -q '"status":"UP"'; }
wait_for 30 backend_up \
  && pass "backend serves /actuator/health (Actuator, as T02 requires)" \
  || fail "backend /actuator/health did not report UP"

frontend_up() { curl -fsS -m 5 -o /dev/null "http://localhost:${FRONTEND_PORT}"; }
wait_for 30 frontend_up \
  && pass "frontend serves a page" \
  || fail "frontend did not respond"

# Regression test: the page used to say "Unreachable" forever, because Actuator sent no
# Access-Control-Allow-Origin and curl — unlike a browser — doesn't enforce CORS, so
# nothing here caught it. Only checking the header itself proves what fetch() sees.
cors_header_present() {
  curl -fsS -m 5 -D - -o /dev/null -H "Origin: http://localhost:${FRONTEND_PORT}" \
       "http://localhost:${BACKEND_PORT}/actuator/health" \
    | grep -qi "^access-control-allow-origin: http://localhost:${FRONTEND_PORT}"
}
wait_for 30 cors_header_present \
  && pass "backend answers the frontend's origin with Access-Control-Allow-Origin" \
  || fail "no CORS header for the frontend's origin — a real browser will report this as unreachable"

exit $(( FAILURES > 0 ))
