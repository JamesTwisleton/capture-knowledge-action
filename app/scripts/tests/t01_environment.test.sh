#!/usr/bin/env bash
# T01 AC1 — the environment comes up and answers, including to a real browser.
# New to bash? lib.sh's header comment explains the recurring idioms used here
# (set -euo pipefail, $(...), the "condition && pass || fail" pattern, and so on).
set -euo pipefail
cd "$(dirname "$0")/../.."   # app/
# dirname "$0" is the directory this script itself lives in (scripts/tests); ../..
# from there is app/, where docker-compose.yml and everything below expects to run.
source scripts/tests/lib.sh
FAILURES=0

# Loop over the three service names, one at a time, running the same check on each.
for svc in event-bus backend frontend; do
  healthy "$svc" \
    && pass "$svc is healthy" \
    || fail "$svc never became healthy — does its healthcheck command exist in the image?"
done

# Retried, not one-shot: this runs alongside t01_hot_reload_backend.test.sh and
# t03_hot_reload_frontend.test.sh, which are busy recompiling and restarting these same
# two services. Both are already confirmed "healthy" above, so a single request landing
# mid-restart is a transient blip to ride out, not a real failure.
#
# Each check below follows the same shape: define a small function, then hand its name
# to wait_for, which calls it repeatedly (see lib.sh) until it succeeds or times out.
# curl flags used throughout: -f fails (non-zero exit) on an HTTP error status rather
# than printing the error page as if it were a success; -s is silent (no progress
# meter); -S shows the actual error even with -s; -m N caps the request at N seconds;
# -o /dev/null discards the response body when only the status/headers matter.
backend_up() { curl -fsS -m 5 "http://localhost:${BACKEND_PORT}/actuator/health" | grep -q '"status":"UP"'; }
# grep -q: exit successfully the instant a match is found, print nothing either way —
# here it's only ever used for its exit code, never its output.
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
  # -D - dumps the response headers to stdout ("-" means "the terminal", not a file);
  # -o /dev/null throws the body away since only a header is being checked here;
  # -H adds a custom request header, mimicking the Origin header a real browser sends.
  curl -fsS -m 5 -D - -o /dev/null -H "Origin: http://localhost:${FRONTEND_PORT}" \
       "http://localhost:${BACKEND_PORT}/actuator/health" \
    | grep -qi "^access-control-allow-origin: http://localhost:${FRONTEND_PORT}"
    # grep -i: case-insensitive, since HTTP header names aren't guaranteed a specific
    # case. The leading ^ anchors the match to the start of a line, so this matches
    # the header line itself rather than the header's value appearing anywhere else.
}
wait_for 30 cors_header_present \
  && pass "backend answers the frontend's origin with Access-Control-Allow-Origin" \
  || fail "no CORS header for the frontend's origin — a real browser will report this as unreachable"

exit $(( FAILURES > 0 ))
