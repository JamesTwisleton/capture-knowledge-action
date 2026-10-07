#!/usr/bin/env bash
# Hygiene — the repo-wide conventions no single ticket owns: secrets stay out of git, and
# every folder holding source carries an AGENTS.md. Unprefixed for exactly that reason;
# every other file here is named for the ticket whose criterion it proves.
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

# An AGENTS.md in every folder is a standing convention, and T02 and T03 both restate it
# for their own trees. Derived rather than a hard-coded list, so a package added by a later
# ticket — T05 adds several — is covered the moment it appears, with no edit here.
#
# Reading the find from the inside out:
#   \( -name node_modules -o ... \) -prune -o ... -print
#     -prune tells find not to descend into a directory at all. Pairing it with -o ("or")
#     is the standard idiom for "skip these trees entirely, then print matches from
#     what's left" — faster, and it avoids asserting anything about code we didn't write.
#   \( -name '*.java' -o -name '*.ts' -o -name '*.tsx' \)
#     any source file. The parentheses are escaped because the shell would otherwise try
#     to interpret them itself rather than passing them to find.
#   xargs -n1 dirname | sort -u
#     turn each file path into the folder holding it, then reduce to the unique set.
#
# `while read ... done < <(command)` is process substitution: it feeds the command's
# output into the loop as if it were a file. A plain pipe would run the loop in a subshell,
# where assignments to $missing would be discarded when that subshell exited.
missing=""
while read -r dir; do
  [ -f "$dir/AGENTS.md" ] || missing="$missing $dir"
done < <(find . \( -name node_modules -o -name .next -o -name target \) -prune -o \
              \( -name '*.java' -o -name '*.ts' -o -name '*.tsx' \) -print \
           | xargs -n1 dirname | sort -u)

# -z is "this string is empty", i.e. nothing was added to $missing by the loop above.
[ -z "$missing" ] \
  && pass "every folder holding source has an AGENTS.md" \
  || fail "no AGENTS.md in:$missing"

exit $(( FAILURES > 0 ))
