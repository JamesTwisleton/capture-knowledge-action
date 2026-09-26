# Shared helpers, sourced by every script in this folder. Not runnable on its own.
#
# If you don't read bash fluently, start here — these are the idioms every file in
# this folder reuses. Rather than re-explain them in each file, every *.test.sh file
# just points back to this comment for anything not specific to what it's checking.
#
# - `set -euo pipefail`, at the top of every *.test.sh file (this file only defines
#   functions, so it doesn't need it itself): `-e` stops the script immediately the
#   moment any command exits with a failure code, instead of bash's normal behaviour
#   of plowing on to the next line. `-u` makes referencing an unset variable an error,
#   instead of silently treating it as an empty string. `-o pipefail` makes a pipeline
#   (`a | b`) count as failed if EITHER side fails, not only the last one — plain bash
#   only looks at the last command's exit code otherwise.
# - `$(...)` runs whatever is inside and substitutes its printed output as text. For
#   example `id=$(docker compose ps -q "$1")` runs that docker command and stores
#   whatever it printed into the variable `id`.
# - A function's arguments work like a script's: `$1` is the first one, `"$@"` means
#   "every argument, each still separately quoted" (important if an argument contains
#   spaces), and `shift` discards `$1` and renumbers the rest down by one — the same
#   trick as popping the front off a queue.
# - `local` inside a function scopes that variable to the function only, so it can't
#   leak into or collide with anything else — the same idea as a variable declared
#   inside a method rather than at class level.
# - `[ expression ]` is bash's "test" command. The pieces used in this folder: `-n`
#   (string is non-empty), `-s` (a file exists and is non-empty), `-lt` / `-gt` /
#   `-eq` (numeric less-than / greater-than / equals), `=` (string equality).
# - `cmd1 && cmd2` runs `cmd2` only if `cmd1` succeeded; `cmd1 || cmd2` runs `cmd2`
#   only if `cmd1` failed. Almost every check in this folder is written as
#   `condition && pass "..." || fail "..."` — read that as a plain if/else: if the
#   condition succeeded, report a pass; otherwise report a fail.
# - `$(( expression ))` evaluates arithmetic and substitutes the resulting number —
#   e.g. `$(( SECONDS + 30 ))` means "30 seconds from now" (see $SECONDS below).
# - Every test file ends with `exit $(( FAILURES > 0 ))`. That comparison evaluates to
#   `1` (true) or `0` (false) — which is also exactly how shells define success and
#   failure, so it doubles as the script's exit code with no extra translation needed:
#   0 means every check in that file passed, anything else means at least one didn't.

pass() { printf '  \033[32mPASS\033[0m  %s\n' "$1"; }
fail() { printf '  \033[31mFAIL\033[0m  %s\n' "$1"; FAILURES=$((FAILURES + 1)); }
# \033[32m / \033[31m are ANSI colour codes (green, red) that terminals understand;
# \033[0m resets back to the default colour afterwards. printf, not echo, because
# printf's first argument is a format string ("%s\n" = insert this text, then a
# newline) rather than something echo might try to interpret as its own flags.

# Poll a command until it succeeds or the budget runs out.
#
# Called like `wait_for 30 some_function`: the first argument is a timeout in
# seconds; everything after it (once `shift` removes that first argument, below) is a
# command to retry — usually the name of a function defined just above the call site,
# since bash can run a function exactly like any other command, by name.
wait_for() {
  local deadline=$(( SECONDS + $1 )); shift
  # $SECONDS is a bash built-in, not a variable this script sets: it counts seconds
  # elapsed since the current shell started, automatically, in the background.
  while [ "$SECONDS" -lt "$deadline" ]; do "$@" && return 0; sleep 5; done
  # "$@" is now just the retry command (the timeout argument is gone, via shift
  # above). Try it; if it succeeds, return success straight away. Otherwise sleep 5
  # seconds and loop back to the while-condition, which re-checks the deadline.
  return 1
}

# Compose's own healthchecks already encode "ready", so trust those rather than
# sleeping and hoping. Read them with docker inspect: `compose ps --format` only
# learned Go templates in a release newer than some contributors will have.
healthy() {
  local id
  id=$(docker compose ps -q "$1" 2>/dev/null) || return 1
  # "$1" is this function's first argument — a service name like "backend". The
  # command prints that service's container ID, or nothing if it isn't running.
  # `2>/dev/null` discards this command's error output (stderr) — /dev/null is a
  # special file that just throws away anything written to it — since a missing
  # service is an expected case here, not something worth printing a warning about.
  [ -n "$id" ] || return 1
  # docker inspect -f '{{ ... }}' pulls one field out of a container's metadata using
  # Go's template syntax (Docker itself is written in Go). The template below reads
  # State.Health.Status if the container has a healthcheck configured at all, and
  # prints nothing otherwise — which is why the surrounding {{if}}...{{end}} is there.
  [ "$(docker inspect -f '{{if .State.Health}}{{.State.Health.Status}}{{end}}' "$id" 2>/dev/null)" = "healthy" ]
}
