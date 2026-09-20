[← Back to the CKA PRD](../README.md) · Previous: [11. Deployment and portability](11-deployment-and-portability.md)

## 12. Known limitations and honest tradeoffs

| Limitation | Stance |
|---|---|
| Trigger phrase and imperfect audio | A garbled or cut-off phrase may not trigger. Ambiguous segments go to a "you may have said something here" triage list. Responsibility is placed on the user: choose a distinctive phrase, use a decent audio setup. Documented as a known issue. |
| "Close it" — which item is "it"? | CKA does not try to work out what "it", "that one" or "the ticket we just discussed" refers to from earlier in the conversation. A trigger phrase with no item ID in the same sentence is ignored rather than guessed. The setup journey teaches this explicitly: the system does not do everything for you. |
| Mistranscribed or non-existent item IDs | Handled by Jev's calibrated mention detection over the scoped pool rather than exact matching; items below threshold are proposed as comments in the Triage Inbox rather than ignored; items outside the pool are not detected; a reference matching no candidate becomes a recorded unmatched mention. All three behaviours are explained to the user as a known limitation, in the setup wizard and the documentation. The MVP demo recording deliberately includes a wrong ticket number — one that is real but not the one meant — so the misclassification and the reject path are shown rather than only the happy path. |
| Wrong or missing content type | A wrong type produces a summary shaped for the wrong kind of content, never a wrong action. A missing type gets the general prompt, and the stored page and activity view show a call to action: assign an existing type, or create a new type with its own prompt. Either choice re-runs the summary. |
| Same-vendor pairings | Confluence + Jira (and equivalents) are deeply linked and may unlock integration bonuses a mixed-vendor stack lacks. All core functionality is vendor-agnostic; the wizard tells you when a chosen pairing has extra integration available. |
| Messaging semantics | Kafka, Pub/Sub and RabbitMQ differ in delivery and ordering guarantees. The event bus interface stays basic; advanced guarantees are the extender's responsibility. |
| Costs are ballpark | Token usage cannot be predicted exactly. Predicted and actual costs are both shown so the estimate's accuracy is visible. |
| Data sensitivity | See the [data-sensitivity warning](../README.md) at the top of the PRD. The user is responsible for confirming their organisation permits the data flow; local models are the privacy-safe option. |
| Untested deploy paths | Only one cloud path is tested in v1. Others are clearly marked. |
| Folder polling latency | Recordings are detected on the next poll, not instantly. Polling was chosen because push notifications need a publicly reachable webhook, which a local run does not have. |
| Decision quality without Jev | With no Jev key configured, the LLM fallback makes the decisions. It works through the same interface and produces the same shape of result, but it is not calibrated, so its confidences mean less. Jev itself is early access (released 15 September 2026) and its API may change. |
| Candidate pool has no filtering in the MVP | The MVP's GitHub candidate pool is every issue in the configured repository. A reference matching none of them is recorded as an unmatched mention rather than acted on. |
| MVP scope | One meeting type, three reference providers (Google Meet, Markdown vault, GitHub Issues), RabbitMQ as the event bus, local only. No cost dashboard, no web setup wizard, no audio transcription, no notification delivery. See the [MVP ticket breakdown](mvp-tickets.md). |
| MCP is spec'd wider than it is built | The MCP compatibility spec covers CKA's core capabilities *and* every provider interface, but the MVP implements only the core-capabilities side. A provider fronted by an arbitrary MCP server is described and not built, so that half of the spec is untested against a real implementation. |
| MCP access control | The MVP's exposed core capabilities include mutating actions (approve, retry, switch provider) and the MVP assumes a trusted local machine. Authentication for the MCP surface is an open question, not a solved one. |

---

← Back to the PRD: [README](../README.md) · Previous: [11. Deployment and portability](11-deployment-and-portability.md) · Next: [13. Open source and business model](13-open-source-and-business-model.md) →
