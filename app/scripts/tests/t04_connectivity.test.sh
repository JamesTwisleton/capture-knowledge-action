#!/usr/bin/env bash
# T04 — stopping the backend container turns the page red; starting it turns it green.
#
# This is the only check in this folder that needs a real browser, and the only one that
# is destructive.
#
# Why a browser: the page decides its colour in client-side JavaScript, so curl only ever
# sees the server-rendered "Checking…" placeholder — it cannot observe this criterion at
# all. The CORS bug fixed in #24 is the cautionary tale: curl saw a perfectly healthy
# backend while every real browser showed "Unreachable" indefinitely.
#
# Why destructive: it stops the backend container to prove the red path, so it cannot
# share the stack with any other check. verify-acceptance.sh runs it alone, last.
#
# New to bash? lib.sh's header comment explains the recurring idioms used here
# (set -euo pipefail, $(...), the "condition && pass || fail" pattern, and so on).
set -euo pipefail
cd "$(dirname "$0")/../.."   # app/
source scripts/tests/lib.sh
FAILURES=0

# Headless Chrome, not Playwright or Puppeteer. Chrome's own --dump-dom runs the page's
# JavaScript and prints the resulting DOM, which is the entirety of what this check needs;
# pulling in a browser-automation library and its ~150MB bundled browser to assert two
# attributes would not survive the Ponytail question "does it need to exist".
#
# Candidates in order: the names CI's ubuntu-latest runners ship, then the usual Linux
# package names, then the macOS bundle paths. `command -v` prints a command's path if it
# is on PATH and exits non-zero if it is not; the `[ -x ... ]` test covers the macOS
# entries, which are full paths to a file rather than something on PATH.
BROWSER=""
for candidate in google-chrome google-chrome-stable chromium chromium-browser \
                 "/Applications/Google Chrome.app/Contents/MacOS/Google Chrome" \
                 "/Applications/Chromium.app/Contents/MacOS/Chromium"; do
  if command -v "$candidate" >/dev/null 2>&1 || [ -x "$candidate" ]; then
    BROWSER="$candidate"
    break
  fi
done

# A hard failure, deliberately, rather than a skip: a check that quietly passes when its
# tooling is absent would let CI report green without ever having verified the criterion.
[ -n "$BROWSER" ] || { fail "no Chrome/Chromium found — this criterion cannot be checked without one"; exit 1; }

# The backend gets stopped halfway through this script, so it has to be put back however
# this script ends — an assertion failure under set -e, or a Ctrl-C partway through, must
# not leave the stack broken for the next run (or for KEEP_UP=1's "leave it up for me").
restore_backend() { docker compose start backend >/dev/null 2>&1 || true; }

# Chrome insists on a writable profile directory, and will reuse — and lock — the real
# one if it isn't given its own. mktemp -d creates a fresh empty directory and prints its
# path. `trap COMMAND EXIT` registers COMMAND to run when this script exits for ANY
# reason, which is what makes the restore above reliable rather than best-effort.
PROFILE=$(mktemp -d)
trap 'restore_backend; rm -rf "$PROFILE"' EXIT

# --dump-dom prints the DOM *after* scripts have run, which is the whole point here: this
# page sets its state in a useEffect, so the server-rendered HTML always says "checking".
# --virtual-time-budget makes Chrome fast-forward timers rather than wait in real time, so
# the page's fetch resolves before the dump instead of racing it. The rest simply stop
# Chrome behaving like a desktop app: no GPU, no sandbox (it needs privileges CI containers
# don't grant), no first-run wizard, and its own throwaway profile.
#
# The awkward part: Chrome writes the finished DOM and then **never exits**, because the
# Next dev server's hot-reload websocket means the page never goes network-idle and the
# virtual clock never drains. Waiting on the process would hang the whole suite. So this
# backgrounds Chrome, waits for the *output* to be complete, and then kills it — the dump
# itself is correct and complete, it is only the shutdown that hangs. (Verified: without
# --virtual-time-budget Chrome exits cleanly but dumps at the load event, which is always
# before the fetch resolves, so the page still reads "Checking…". The budget is required.)
render() {
  local out pid deadline
  out=$(mktemp)
  "$BROWSER" --headless --disable-gpu --no-sandbox --no-first-run \
             --no-default-browser-check --disable-extensions \
             --user-data-dir="$PROFILE" --virtual-time-budget=4000 \
             --dump-dom "http://localhost:${FRONTEND_PORT}" >"$out" 2>/dev/null &
  pid=$!
  # Wait for a *complete* document rather than merely a non-empty file: stdout arrives in
  # chunks, so a closing </html> is the signal that the dump finished rather than that it
  # merely started. Not coupled to anything this test asserts, deliberately.
  deadline=$(( SECONDS + 30 ))
  while [ "$SECONDS" -lt "$deadline" ]; do
    [ -s "$out" ] && grep -q '</html>' "$out" 2>/dev/null && break
    sleep 1
  done
  # `|| true` on both: the process is expected to be killed rather than to finish, and
  # under `set -e` an unguarded non-zero exit here would abort the whole script.
  kill -9 "$pid" 2>/dev/null || true
  wait "$pid" 2>/dev/null || true
  # Chrome's helper processes are children, and killing the parent can orphan them. The
  # throwaway profile path appears in every one of their command lines, which makes it an
  # exact match for "this test's Chrome processes and nothing else on the machine".
  pkill -9 -f "$PROFILE" 2>/dev/null || true
  cat "$out"
  rm -f "$out"
}

# Pull out just the one element the page marks with data-connection-state. grep -o prints
# only the matched text rather than the whole line, and the pattern runs from that
# element's opening "<" through to its ">" — so the state attribute and the inline style
# carrying the colour both come back together, in one string.
indicator() { render | grep -o '<[^>]*data-connection-state="[^"]*"[^>]*>' | head -1; }

# Named so it reads as a sentence at the call sites below: `in_state connected`.
in_state() { indicator | grep -q "data-connection-state=\"$1\""; }

# Extract whatever colour the indicator is currently painted. Deliberately returns the
# value rather than comparing it to a hard-coded green or red: the test asserts that the
# two states *differ*, so the page stays free to restyle itself without editing this file.
colour_of() { printf '%s' "$1" | grep -o 'color: rgb([0-9, ]*)' | head -1; }

# --- green while the backend is up ------------------------------------------------------

wait_for 90 in_state connected \
  && pass "page reports connected while the backend is up" \
  || fail "page never reported connected — a real browser cannot read /actuator/health"

UP_COLOUR=$(colour_of "$(indicator)")
[ -n "$UP_COLOUR" ] \
  && pass "connected state is actually coloured ($UP_COLOUR)" \
  || fail "connected state has no colour — the criterion is red/green, not just text"

# --- red once it is stopped -------------------------------------------------------------

echo "  stopping the backend container…"
docker compose stop backend >/dev/null 2>&1

wait_for 90 in_state disconnected \
  && pass "stopping the backend turns the page to its cannot-connect state" \
  || fail "page still did not report disconnected after the backend was stopped"

DOWN_COLOUR=$(colour_of "$(indicator)")
[ -n "$DOWN_COLOUR" ] \
  && pass "disconnected state is actually coloured ($DOWN_COLOUR)" \
  || fail "disconnected state has no colour — the criterion is red/green, not just text"

[ -n "$UP_COLOUR" ] && [ -n "$DOWN_COLOUR" ] && [ "$UP_COLOUR" != "$DOWN_COLOUR" ] \
  && pass "the indicator's colour changes between the two states" \
  || fail "both states render the same colour — nothing visibly turns red or green"

# --- green again once it is back --------------------------------------------------------

echo "  starting the backend container again…"
docker compose start backend >/dev/null 2>&1

# Generous: this is a cold JVM start, and compose's own healthcheck allows a 60s
# start_period before it even begins probing.
backend_healthy() { healthy backend; }
wait_for 300 backend_healthy \
  || fail "backend never became healthy again after being restarted"

wait_for 120 in_state connected \
  && pass "starting the backend turns the page back to connected" \
  || fail "page stayed disconnected after the backend came back — is it polling, or only checking once on load?"

exit $(( FAILURES > 0 ))
