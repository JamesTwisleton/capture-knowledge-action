#!/usr/bin/env bash
# T01/T02 — a backend edit restarts the app without an image rebuild.
set -euo pipefail
cd "$(dirname "$0")/../.."   # app/
source scripts/tests/lib.sh
FAILURES=0

restarts() { docker compose logs backend 2>/dev/null | grep -c "Started CaptureKnowledgeActionApplication" || true; }
before=$(restarts)
restarted() { [ "$(restarts)" -gt "$before" ]; }
docker compose exec -T backend touch src/main/java/com/cka/CaptureKnowledgeActionApplication.java
docker compose exec -T backend mvn -o -q compile >/dev/null 2>&1 || true
wait_for 90 restarted \
  && pass "DevTools restarted the app after an in-container compile" \
  || fail "no restart detected — DevTools may not be watching target/classes"

exit $(( FAILURES > 0 ))
