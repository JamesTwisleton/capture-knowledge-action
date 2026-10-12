#!/usr/bin/env bash
# T07 — the backend talks to the real broker in this environment.
#
# Deliberately NOT a repeat of RabbitEventBusTest. That test already proves publish and
# subscribe against a real RabbitMQ, including serialisation and routing, but it does so
# against a Testcontainers broker whose address Spring Boot injects for it. What it cannot
# prove is the wiring *here*: that EVENT_BUS_HOST resolves on the Compose network, that the
# credentials in .env are the ones the broker accepts, and that the exchange the code expects
# is the exchange that actually exists. Those are the ways this breaks in the real stack while
# every unit test stays green.
#
# New to bash? lib.sh's header comment explains the recurring idioms used here.
set -euo pipefail
cd "$(dirname "$0")/../.."   # app/
source scripts/tests/lib.sh
FAILURES=0

MGMT_PORT="${EVENT_BUS_MGMT_PORT:-15672}"
USER="${RABBITMQ_USER:-guest}"
PASS="${RABBITMQ_PASS:-guest}"
# %2F is "/" percent-encoded — RabbitMQ's default virtual host is literally named "/", and it
# appears as a path segment in these URLs, so it has to be escaped or the path breaks.
API="http://localhost:${MGMT_PORT}/api"

# -u sends HTTP basic auth credentials. The management API is a separate HTTP interface on
# its own port, unrelated to the AMQP port the application connects on.
mgmt() { curl -fsS -m 5 -u "${USER}:${PASS}" "$@"; }

healthy event-bus \
  && pass "event-bus is healthy" \
  || fail "event-bus never became healthy"

# The exchange is declared by the backend at startup, not by this script and not by the
# image — so its existence is evidence the application connected and completed its
# declarations, which a mere TCP check on 5672 would not give.
exchange_declared() { mgmt "${API}/exchanges/%2F/cka.events" >/dev/null 2>&1; }
wait_for 60 exchange_declared \
  && pass "the backend declared the cka.events exchange" \
  || fail "cka.events was never declared — did the backend fail to connect to the broker?"

# Topic, specifically. A direct exchange would still pass the check above while quietly
# breaking AuditTrail, which binds a wildcard to receive all six event types (T05).
if exchange_declared; then
  TYPE=$(mgmt "${API}/exchanges/%2F/cka.events" | sed -n 's/.*"type":"\([a-z]*\)".*/\1/p')
  [ "$TYPE" = "topic" ] \
    && pass "cka.events is a topic exchange, so wildcard bindings work" \
    || fail "cka.events is '$TYPE', not topic — wildcard subscriptions will not match"
fi

# One connection from the application itself. Proves the credentials and host in .env are
# usable, rather than only that the broker is listening.
backend_connected() { [ "$(mgmt "${API}/connections" | grep -c '"state":"running"' || true)" -gt 0 ]; }
wait_for 60 backend_connected \
  && pass "the backend holds an open connection to the broker" \
  || fail "no running connection on the broker — check EVENT_BUS_HOST and the credentials in .env"

exit $(( FAILURES > 0 ))
