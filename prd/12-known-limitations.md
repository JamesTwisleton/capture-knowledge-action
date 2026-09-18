[← Back to the CKA PRD](../README.md) · Previous: [11. Deployment and portability](11-deployment-and-portability.md)

## 12. Known limitations and honest tradeoffs

| Limitation | Stance |
|---|---|
| Trigger phrase and imperfect audio | A garbled or cut-off phrase may not trigger. Ambiguous segments go to a "you may have said something here" triage list. Responsibility is placed on the user: choose a distinctive phrase, use a decent audio setup. Documented as a known issue. |
| "Close it" — which item is "it"? | CKA does not try to work out what "it", "that one" or "the ticket we just discussed" refers to from earlier in the conversation. A trigger phrase with no item ID in the same sentence is ignored rather than guessed. The setup journey teaches this explicitly: the system does not do everything for you. |
| Mistranscribed or non-existent item IDs | Handled by Jev's calibrated mention detection over the scoped pool rather than exact matching; items below threshold are ignored; items outside the pool are not detected. Both behaviours are explained to the user as a known limitation, in the setup wizard and the documentation. |
| Wrong or missing content type | A wrong type produces a summary shaped for the wrong kind of content, never a wrong action. A missing type gets the general prompt, and the stored page and activity view show a call to action: assign an existing type, or create a new type with its own prompt. Either choice re-runs the summary. |
| Same-vendor pairings | Confluence + Jira (and equivalents) are deeply linked and may unlock integration bonuses a mixed-vendor stack lacks. All core functionality is vendor-agnostic; the wizard tells you when a chosen pairing has extra integration available. |
| Messaging semantics | Kafka, Pub/Sub and RabbitMQ differ in delivery and ordering guarantees. The event bus interface stays basic; advanced guarantees are the extender's responsibility. |
| Costs are ballpark | Token usage cannot be predicted exactly. Predicted and actual costs are both shown so the estimate's accuracy is visible. |
| Data sensitivity | See the [data-sensitivity warning](../README.md) at the top of the PRD. The user is responsible for confirming their organisation permits the data flow; local models are the privacy-safe option. |
| Untested deploy paths | Only one cloud path is tested in v1. Others are clearly marked. |

---

← Back to the PRD: [README](../README.md) · Previous: [11. Deployment and portability](11-deployment-and-portability.md) · Next: [13. Open source and business model](13-open-source-and-business-model.md) →
