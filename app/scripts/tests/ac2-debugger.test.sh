#!/usr/bin/env bash
# AC2 — a debugger can attach to the backend.
set -euo pipefail
cd "$(dirname "$0")/../.."   # app/
source scripts/tests/lib.sh
FAILURES=0

python3 - "$JVM_DEBUG_PORT" <<'PY' && pass "JDWP handshake accepted" || fail "no JDWP handshake — the port may be open but dead"
import socket, sys
with socket.create_connection(("localhost", int(sys.argv[1])), timeout=15) as s:
    s.sendall(b"JDWP-Handshake")
    sys.exit(0 if s.recv(14) == b"JDWP-Handshake" else 1)
PY

exit $(( FAILURES > 0 ))
