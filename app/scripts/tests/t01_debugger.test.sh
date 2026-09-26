#!/usr/bin/env bash
# T01 AC2 — a debugger can attach to the backend.
# New to bash? lib.sh's header comment explains the recurring idioms used here.
set -euo pipefail
cd "$(dirname "$0")/../.."   # app/
source scripts/tests/lib.sh
FAILURES=0

# Everything between "<<'PY'" and the closing "PY" is a heredoc: a block of text
# handed to the preceding command as if it had been typed into its standard input.
# `python3 -` means "read the script to run from standard input" rather than from a
# file — so this heredoc's contents ARE the Python program that runs. The quotes
# around 'PY' matter: they tell bash not to expand anything inside (no $variable
# substitution), so the Python code's own syntax — sys.argv, sys.exit — is left alone
# rather than bash trying to interpret it. "$JVM_DEBUG_PORT" becomes this Python
# script's sys.argv[1], the normal way a shell passes an argument to any program.
#
# The check itself: open a raw TCP socket to the debug port and speak the first step
# of JDWP (Java Debug Wire Protocol) by hand — send the literal bytes "JDWP-Handshake"
# and confirm the JVM echoes the same 14 bytes back. That's genuinely easier to write
# at the socket level in Python than in bash, which is why this one file reaches for
# it instead of curl or a bash /dev/tcp trick.
#
# The "&& pass ... || fail ..." on the end applies to the whole heredoc block, exactly
# the same as it applies to a single command elsewhere in this folder — bash treats
# "python3 - ... <<'PY' ... PY" as one command as far as && and || are concerned.
python3 - "$JVM_DEBUG_PORT" <<'PY' && pass "JDWP handshake accepted" || fail "no JDWP handshake — the port may be open but dead"
import socket, sys
with socket.create_connection(("localhost", int(sys.argv[1])), timeout=15) as s:
    s.sendall(b"JDWP-Handshake")
    sys.exit(0 if s.recv(14) == b"JDWP-Handshake" else 1)
PY

exit $(( FAILURES > 0 ))
