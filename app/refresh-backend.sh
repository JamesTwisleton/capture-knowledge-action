#!/usr/bin/env bash
# Recompile the backend inside its container. Spring Boot DevTools notices the new
# classes and restarts the app — usually a second or two, no image rebuild.
#
# This is a convenience wrapper. The command it runs is no secret:
#     docker compose exec backend mvn -o compile
#
# New to bash? `set -euo pipefail` makes this script stop the instant any command in
# it fails, rather than continuing on regardless — see app/scripts/tests/lib.sh's
# header comment for a fuller explanation of that and the other idioms used below.
set -euo pipefail
cd "$(dirname "$0")"
# dirname "$0" is the directory this script itself lives in — app/, where
# docker-compose.yml is — so this works regardless of which directory it's run from.

# `docker compose ps --status running --services` prints the name of every currently
# running service, one per line. Piping that into `grep -qx backend` checks whether
# any of those lines is EXACTLY "backend" (-x matches the whole line, not just part
# of it, so a hypothetical service named "backend-worker" couldn't cause a false
# match) — quietly (-q), since only the exit code is being used here.
#
# The leading `!` negates that check's result, so the block below runs when backend
# is NOT running.
if ! docker compose ps --status running --services 2>/dev/null | grep -qx backend; then
  # >&2 redirects this echo's output to stderr (file descriptor 2) instead of the
  # normal stdout — the conventional place for error/status messages that aren't the
  # actual output a caller might want to capture.
  echo "The backend container isn't running. Start the stack first:" >&2
  echo "    docker compose up -d" >&2
  exit 1
fi

echo "Compiling in the container…"
# `docker compose exec` runs a command inside the already-running backend container
# (as opposed to starting a new one). `-T` skips allocating a pseudo-terminal, since
# nothing here needs to interact with this command while it runs. `-o` compiles
# offline; `-q` suppresses Maven's own progress output, leaving just this script's
# own status lines.
docker compose exec -T backend mvn -o -q compile
echo "Done. DevTools is restarting the app; watch it with:"
echo "    docker compose logs -f backend"
