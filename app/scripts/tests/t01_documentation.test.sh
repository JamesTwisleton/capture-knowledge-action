#!/usr/bin/env bash
# T01 AC3 — the documentation exists.
# New to bash? lib.sh's header comment explains the recurring idioms used here.
set -euo pipefail
cd "$(dirname "$0")/../.."   # app/
source scripts/tests/lib.sh
FAILURES=0

# A plain word list after "in" — bash loops over each one in turn, assigning it to $f.
for f in ../docs/docker-environment.md ../docs/AGENTS.md backend/AGENTS.md frontend/AGENTS.md; do
  # -s is true if the file exists AND is non-empty (a file that exists but is
  # completely blank would still fail this, which is the point).
  #
  # "${f#../}" is parameter expansion: it strips the shortest possible match of the
  # pattern "../" from the FRONT of $f, leaving the rest untouched. It only exists
  # here to make the pass/fail message read naturally (the docs paths need a literal
  # "../" to reach up out of app/, but printing "../docs/..." in the output would be
  # a confusing thing for a reader to see, so this trims it to just "docs/..." for
  # display purposes only — the actual file check above still uses the real path).
  [ -s "$f" ] && pass "${f#../}" || fail "${f#../} is missing or empty"
done

exit $(( FAILURES > 0 ))
