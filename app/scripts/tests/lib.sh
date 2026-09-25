# Shared helpers, sourced by every script in this folder. Not runnable on its own.

pass() { printf '  \033[32mPASS\033[0m  %s\n' "$1"; }
fail() { printf '  \033[31mFAIL\033[0m  %s\n' "$1"; FAILURES=$((FAILURES + 1)); }

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
