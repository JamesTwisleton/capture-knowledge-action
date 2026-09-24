#!/usr/bin/env bash
# Recompile the backend inside its container. Spring Boot DevTools notices the new
# classes and restarts the app — usually a second or two, no image rebuild.
#
# This is a convenience wrapper. The command it runs is no secret:
#     docker compose exec backend mvn -o compile
set -euo pipefail
cd "$(dirname "$0")"

if ! docker compose ps --status running --services 2>/dev/null | grep -qx backend; then
  echo "The backend container isn't running. Start the stack first:" >&2
  echo "    docker compose up -d" >&2
  exit 1
fi

echo "Compiling in the container…"
docker compose exec -T backend mvn -o -q compile
echo "Done. DevTools is restarting the app; watch it with:"
echo "    docker compose logs -f backend"
