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

# Printed unconditionally, not only on failure. Browser version is the single most useful
# fact when this check behaves differently on a runner than on a laptop, and by the time
# it has failed the cheap opportunity to record it has passed.
echo "  browser: $BROWSER ($("$BROWSER" --version 2>/dev/null || echo 'version unavailable'))"

# The backend gets stopped halfway through this script, so it has to be put back however
# this script ends — an assertion failure under set -e, or a Ctrl-C partway through, must
# not leave the stack broken for the next run (or for KEEP_UP=1's "leave it up for me").
restore_backend() { docker compose start backend >/dev/null 2>&1 || true; }

# Chrome insists on a writable profile directory, and will reuse — and lock — the real
# one if it isn't given its own. mktemp -d creates a fresh empty directory and prints its
# path. `trap COMMAND EXIT` registers COMMAND to run when this script exits for ANY
# reason, which is what makes the restore above reliable rather than best-effort.
PROFILE=$(mktemp -d)
BROWSER_ERR="$PROFILE/browser.stderr"
LAST_DUMP="$PROFILE/last-dump.html"
trap 'restore_backend; rm -rf "$PROFILE"' EXIT

# --dump-dom prints the DOM *after* scripts have run, which is the whole point here: this
# page sets its state in a useEffect, so the server-rendered HTML always says "checking".
# --virtual-time-budget makes Chrome fast-forward timers rather than wait in real time, so
# the page's fetch resolves before the dump instead of racing it. The rest simply stop
# Chrome behaving like a desktop app: no GPU, no sandbox and no /dev/shm reliance (both
# need privileges or space CI runners don't reliably grant), no first-run wizard, and its
# own throwaway profile.
#
# The awkward part: Chrome writes the finished DOM and then **never exits**, because the
# Next dev server's hot-reload websocket means the page never goes network-idle and the
# virtual clock never drains. Waiting on the process would hang the whole suite. So this
# backgrounds Chrome, waits for the *output* to be complete, and then kills it — the dump
# itself is correct and complete, it is only the shutdown that hangs. (Verified: without
# --virtual-time-budget Chrome exits cleanly but dumps at the load event, which is always
# before the fetch resolves, so the page still reads "Checking…". The budget is required.)
#
# stderr is kept rather than discarded, in $BROWSER_ERR, so diagnose() below can explain a
# browser that failed to start instead of just reporting an empty page.
render() {
  local pid deadline
  : >"$LAST_DUMP"
  "$BROWSER" --headless --disable-gpu --no-sandbox --disable-dev-shm-usage \
             --no-first-run --no-default-browser-check --disable-extensions \
             --user-data-dir="$PROFILE" --virtual-time-budget=4000 \
             --dump-dom "http://localhost:${FRONTEND_PORT}" >"$LAST_DUMP" 2>"$BROWSER_ERR" &
  pid=$!
  # Wait for a *complete* document rather than merely a non-empty file: stdout arrives in
  # chunks, so a closing </html> is the signal that the dump finished rather than that it
  # merely started. Not coupled to anything this test asserts, deliberately.
  deadline=$(( SECONDS + 45 ))
  while [ "$SECONDS" -lt "$deadline" ]; do
    [ -s "$LAST_DUMP" ] && grep -q '</html>' "$LAST_DUMP" 2>/dev/null && break
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
  cat "$LAST_DUMP"
}

# A check that fails without saying why is half a check — the same reasoning as
# verify-acceptance.sh's own diagnose(). Everything here is a question someone would
# otherwise have to re-run CI to answer.
diagnose() {
  echo "  --- diagnosing the browser render ---"
  echo "  dump size: $(wc -c <"$LAST_DUMP" 2>/dev/null || echo 0) bytes"
  if [ -s "$LAST_DUMP" ]; then
    grep -q '</html>' "$LAST_DUMP" 2>/dev/null \
      && echo "  dump looks complete (has a closing </html>)" \
      || echo "  dump is INCOMPLETE — no closing </html>, so Chrome was killed mid-write"
    # The indicator element if it is there at all, else whatever the page says about the
    # backend, else the page's first heading — in that order of usefulness.
    grep -o '<[^>]*data-connection-state="[^"]*"[^>]*>' "$LAST_DUMP" | head -1 \
      || grep -o 'Backend:.\{0,80\}' "$LAST_DUMP" | head -1 \
      || grep -o '<h1[^>]*>[^<]*' "$LAST_DUMP" | head -1 \
      || echo "  no indicator, no status line and no heading in the dump"
  else
    echo "  dump is EMPTY — Chrome produced nothing at all"
  fi
  if [ -s "$BROWSER_ERR" ]; then
    echo "  browser stderr (last 15 lines):"
    tail -15 "$BROWSER_ERR" | sed 's/^/    /'
  else
    echo "  browser wrote nothing to stderr"
  fi
  # Proves whether the problem is the browser or the stack underneath it. If curl can read
  # a healthy backend and the frontend serves a page, the fault is in the render.
  #
  # Assigned first rather than inlined into the echo, because curl still writes its -w
  # output on failure — inlining produces "000" glued to the error text instead of a
  # clean verdict.
  local be fe
  be=$(curl -fsS -m 5 "http://localhost:${BACKEND_PORT}/actuator/health" 2>/dev/null | head -c 120) \
    && echo "  curl backend:  $be" || echo "  curl backend:  unreachable"
  fe=$(curl -fsS -m 5 -o /dev/null -w '%{http_code}' "http://localhost:${FRONTEND_PORT}" 2>/dev/null) \
    && echo "  curl frontend: HTTP $fe" || echo "  curl frontend: unreachable"
  echo "  ------------------------------------"
}

# Pull out just the one element the page marks with data-connection-state. grep -o prints
# only the matched text rather than the whole line, and the pattern runs from that
# element's opening "<" through to its ">" — so the state attribute and the inline style
# carrying the colour both come back together, in one string.
#
# `|| true` matters: grep exits non-zero when it matches nothing, and with `set -o
# pipefail` that would propagate out of the enclosing $(...) and kill the script under
# `set -e` — which is exactly what hid every assertion after the first one on the initial
# CI run. "No match" is an expected answer here, not a script error.
indicator() { render | grep -o '<[^>]*data-connection-state="[^"]*"[^>]*>' | head -1 || true; }

# Named so it reads as a sentence at the call sites below: `in_state connected`.
in_state() { indicator | grep -q "data-connection-state=\"$1\""; }

# Extract whatever colour the indicator is currently painted. Deliberately returns the
# value rather than comparing it to a hard-coded green or red: the test asserts that the
# two states *differ*, so the page stays free to restyle itself without editing this file.
# Same `|| true` reasoning as indicator() above.
colour_of() { printf '%s' "$1" | grep -o 'color: rgb([0-9, ]*)' | head -1 || true; }

# Compile the page before the browser ever sees it. The dev server builds a route on first
# request, and letting that happen inside a timed browser render would charge Turbopack's
# cold compile to the browser's budget — slow enough on a cold CI runner to look like a
# failure to connect rather than what it is.
curl -fsS -m 60 -o /dev/null "http://localhost:${FRONTEND_PORT}" 2>/dev/null || true

# --- green while the backend is up ------------------------------------------------------

if wait_for 180 in_state connected; then
  pass "page reports connected while the backend is up"
else
  fail "page never reported connected — a real browser cannot read /actuator/health"
  diagnose
fi

UP_COLOUR=$(colour_of "$(indicator)")
[ -n "$UP_COLOUR" ] \
  && pass "connected state is actually coloured ($UP_COLOUR)" \
  || fail "connected state has no colour — the criterion is red/green, not just text"

# --- red once it is stopped -------------------------------------------------------------

echo "  stopping the backend container…"
docker compose stop backend >/dev/null 2>&1

if wait_for 120 in_state disconnected; then
  pass "stopping the backend turns the page to its cannot-connect state"
else
  fail "page still did not report disconnected after the backend was stopped"
  diagnose
fi

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
