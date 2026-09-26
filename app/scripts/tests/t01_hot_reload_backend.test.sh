#!/usr/bin/env bash
# T01/T02 — a backend edit restarts the app without an image rebuild.
# New to bash? lib.sh's header comment explains the recurring idioms used here.
set -euo pipefail
cd "$(dirname "$0")/../.."   # app/
source scripts/tests/lib.sh
FAILURES=0

# How many times has the app printed its startup banner so far? Counting that, before
# and after touching a source file, is how this proves a restart actually happened —
# rather than, say, the app never having stopped in the first place.
#
# `docker compose logs backend` prints every log line the backend container has ever
# produced; piping it through `grep -c PATTERN` counts how many of those lines match.
# grep exits with a failure code if it finds ZERO matches, which under `set -e` would
# normally kill this whole script — `|| true` catches that and forces success anyway,
# since "zero restarts so far" is a perfectly normal count, not an error.
restarts() { docker compose logs backend 2>/dev/null | grep -c "Started CaptureKnowledgeActionApplication" || true; }
before=$(restarts)
restarted() { [ "$(restarts)" -gt "$before" ]; }

# `docker compose exec` runs a command inside an already-running container, as
# opposed to `docker compose run`, which would start a brand new one. `-T` disables
# allocating a pseudo-terminal for that command — appropriate here since nothing is
# meant to interact with it, only capture its exit code.
#
# touch merely updates the file's modified time without changing its content, which
# is enough on its own to make Spring Boot DevTools notice something changed; the
# actual recompile is what follows.
docker compose exec -T backend touch src/main/java/com/cka/CaptureKnowledgeActionApplication.java
docker compose exec -T backend mvn -o -q compile >/dev/null 2>&1 || true
# `-o` compiles offline (no network dependency-resolution attempt); `-q` is quiet
# (suppress Maven's own progress output). `>/dev/null 2>&1` discards both its normal
# output and its error output — this line's own success or failure isn't the point;
# `wait_for restarted` just below is what actually decides whether this test passes.
wait_for 90 restarted \
  && pass "DevTools restarted the app after an in-container compile" \
  || fail "no restart detected — DevTools may not be watching target/classes"

exit $(( FAILURES > 0 ))
