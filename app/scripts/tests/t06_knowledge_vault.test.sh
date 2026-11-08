#!/usr/bin/env bash
# T06 — a summarised content event becomes a note on the host, in this environment.
#
# MarkdownVaultKnowledgeProviderTest already proves what is written and StoreAcrossTheBusTest
# proves the event in and the event out, but both run against a temp directory of the test's own
# choosing and an in-memory bus. What they cannot prove is the wiring *here*: that VAULT_PATH
# points at the mounted volume rather than somewhere inside the container, that the note
# therefore survives on the host where Obsidian can open it, and that the real broker carries a
# content-summarised event to the real provider with nothing calling it directly.
#
# So the event is published to the live exchange over the management API — the same route
# t07_event_bus uses to inspect it — rather than by running the pipeline, because the capture
# stage that would normally publish it is T09's and does not exist yet.
#
# New to bash? lib.sh's header comment explains the recurring idioms used here.
set -euo pipefail
cd "$(dirname "$0")/../.."   # app/
source scripts/tests/lib.sh
FAILURES=0

MGMT_PORT="${EVENT_BUS_MGMT_PORT:-15672}"
MGMT_USER="${RABBITMQ_USER:-guest}"
MGMT_PASS="${RABBITMQ_PASS:-guest}"
API="http://localhost:${MGMT_PORT}/api"
mgmt() { curl -fsS -m 5 -u "${MGMT_USER}:${MGMT_PASS}" "$@"; }

# The host side of the volume mount in docker-compose.yml, which is always ./data/vault. The
# container path comes from .env; this is where it has to surface for Obsidian to open it.
VAULT_DIR="data/vault"
# Unique per run, so a repeat does not read the previous run's note and call it a pass.
TITLE="Acceptance Check $(date +%s)-$$"
NOTE="${VAULT_DIR}/${TITLE}.md"

healthy backend \
  && pass "backend is healthy" \
  || fail "backend never became healthy"

# A content-summarised event, shaped as the pipeline's own records serialise. __TypeId__ is what
# RabbitEventBusConfiguration's class mapper reads to decide which event this is — a payload
# without it is rejected by the consumer rather than silently mis-handled.
PAYLOAD=$(cat <<JSON
{"chainId":"11111111-2222-3333-4444-555555555555",
 "content":{"sourceUri":"https://example.test/acceptance","title":"${TITLE}",
            "contentType":"Sprint Refinement","transcript":"The acceptance check said this.",
            "platformSummary":"The platform summarised it."},
 "summary":{"text":"The acceptance check summary.",
            "producedBy":{"provider":"acceptance","model":"none"},"promptVersion":1}}
JSON
)
# The management API takes the message body as a JSON *string*, so the payload above is escaped
# into one. sed is enough: the payload is ours and contains no backslashes or control characters.
ESCAPED=$(printf '%s' "$PAYLOAD" | tr -d '\n' | sed 's/"/\\"/g')

mgmt -X POST "${API}/exchanges/%2F/cka.events/publish" \
     -H "content-type: application/json" \
     -d "{\"properties\":{\"headers\":{\"__TypeId__\":\"ContentSummarised\"},\"content_type\":\"application/json\"},
          \"routing_key\":\"cka.event.ContentSummarised\",
          \"payload\":\"${ESCAPED}\",\"payload_encoding\":\"string\"}" \
  | grep -q '"routed":true' \
  && pass "the broker routed a content-summarised event to a subscriber" \
  || fail "nothing is bound to cka.event.ContentSummarised — is the Knowledge stage started?"

# The note on the *host*, which is the whole point: its existence here proves VAULT_PATH landed
# on the mounted volume and that the provider wrote through it.
note_written() { [ -s "$NOTE" ]; }
wait_for 30 note_written \
  && pass "the backend wrote $NOTE on the host volume" \
  || fail "$NOTE never appeared — is VAULT_PATH pointing outside the mounted volume?"

if note_written; then
  grep -q "The acceptance check summary." "$NOTE" \
    && pass "the note carries the summary" \
    || fail "the note exists but has no summary in it"

  # The front matter Obsidian reads as properties, and the wiki link that groups notes by type.
  # Plain Markdown either way — the criterion's "opens in a plain text editor" is this grep.
  head -1 "$NOTE" | grep -q -- "---" \
    && pass "the note opens with YAML front matter" \
    || fail "the note has no front matter, so Obsidian will show no properties"

  grep -q "\[\[Sprint Refinement\]\]" "$NOTE" \
    && pass "the content type is a wiki link" \
    || fail "the note has no [[content type]] link"

  # Removed again: this is somebody's real vault when the stack is run for the demo, and a note
  # per acceptance run would accumulate there. The reset in T19 does not cover the vault yet.
  rm -f "$NOTE"
fi

exit $(( FAILURES > 0 ))
