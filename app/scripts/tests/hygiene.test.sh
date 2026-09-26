#!/usr/bin/env bash
# Hygiene — secrets stay out of git.
# New to bash? lib.sh's header comment explains the recurring idioms used here.
set -euo pipefail
cd "$(dirname "$0")/../.."   # app/
source scripts/tests/lib.sh
FAILURES=0

# `git ls-files --error-unmatch PATH` exits successfully if PATH is tracked by git,
# and fails (non-zero exit) if it isn't — regardless of whether the file exists on
# disk. That's exactly the question here: not "does .env exist" (it should, locally)
# but "is it checked in" (it must not be, since it holds real credentials).
#
# `>/dev/null 2>&1` throws away both the normal output and the error output of that
# command — its exit code is all that's being used, so any text it would print isn't
# wanted in this script's own output.
#
# Notice the pass/fail are swapped relative to every other check in this folder: here
# succeeding at the git command is the FAILURE case (it means .env is tracked), so
# `&&` leads to fail() and `||` leads to pass().
git ls-files --error-unmatch .env >/dev/null 2>&1 \
  && fail ".env is tracked — from T20 it holds real credentials" \
  || pass ".env is not tracked"
[ -s .env.example ] && pass ".env.example is committed as the template" || fail ".env.example is missing"

exit $(( FAILURES > 0 ))
