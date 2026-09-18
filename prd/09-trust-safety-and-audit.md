[← Back to the CKA PRD](../README.md) · Previous: [8. Architecture](08-architecture.md)

## 9. Trust, safety and audit

### 9.1 The gate model

```mermaid
---
title: The gate model, from transcript segment to action
---
%%{init: {"theme":"base","themeVariables":{"background":"#000000","fontSize":"20px","fontFamily":"Helvetica, Arial, sans-serif","primaryColor":"#000000","primaryTextColor":"#ffffff","primaryBorderColor":"#ffffff","secondaryColor":"#000000","secondaryTextColor":"#ffffff","secondaryBorderColor":"#ffffff","tertiaryColor":"#000000","tertiaryTextColor":"#ffffff","tertiaryBorderColor":"#ffffff","lineColor":"#ffffff","textColor":"#ffffff","mainBkg":"#000000","nodeBorder":"#ffffff","nodeTextColor":"#ffffff","clusterBkg":"#000000","clusterBorder":"#ffffff","titleColor":"#ffffff","edgeLabelBackground":"#000000"},"flowchart":{"nodeSpacing":80,"rankSpacing":100,"padding":24,"curve":"linear"}}}%%
flowchart TB
  T["TRANSCRIPT SEGMENT"] ==> G1{"GATE 1 · MENTION<br/>Jev confidence at or<br/>above the threshold?"}
  G1 ==>|"no"| X1["IGNORE"]
  G1 ==>|"yes"| C1["COMMENT ON THE WORK ITEM<br/>low risk, no trigger phrase needed"]
  C1 ==> G2{"GATE 2 · INTENT<br/>trigger phrase present, with<br/>an explicit item ID?"}
  G2 ==>|"no"| X2["NO FURTHER ACTION"]
  G2 ==>|"yes"| G3{"GATE 3 · DECISION<br/>Jev confidence at or above<br/>the per-action threshold?"}
  G3 ==>|"no"| INBOX["TRIAGE INBOX<br/>a human decides"]
  G3 ==>|"yes"| G4{"GATE 4 · TRUST<br/>is this action type set<br/>to auto-approve?"}
  G4 ==>|"no"| INBOX
  G4 ==>|"yes"| ACT["APPLY THE ACTION"]
  C1 -.-> AUD[("AUDIT SERVICE")]
  INBOX -.-> AUD
  ACT -.-> AUD
  classDef default fill:#000000,stroke:#ffffff,stroke-width:2px,color:#ffffff
  linkStyle default stroke:#ffffff,stroke-width:3px
```

- **Gate 1 — mention.** Jev confidence that a candidate work item was mentioned is at or above the mention threshold (default 90%). Outcome: a comment. No trigger phrase required.
- **Gate 2 — intent.** The team trigger phrase is present and names the item by its ID in the same sentence. Only segments passing this gate are ever considered for mutating actions.
- **Gate 3 — decision.** Jev confidence that the requested action is what was asked is at or above the per-action-type threshold.
- **Gate 4 — trust.** The action type's trust setting is auto-approve; otherwise the proposal goes to the Triage Inbox.

### 9.2 Trust scores

- Per action type (comment, transition, close, assign, ...), never global. A reasonable global default set exists; each type is individually configurable with minimal effort.
- Proposed defaults: comment — automatic; transition, close, assign — review.
- Computed from human approve/reject decisions over a configurable window. Drives both the "switch to auto-approve" suggestion and the "disable, or switch provider" suggestion.

### 9.3 Audit

- Every action — automatic or human-approved — is recorded with provider, model, confidence, decider and outcome.
- The audit trail is the basis for the trust ramp, the activity view and post-hoc investigation ("why did it close that?").
- It lives in the audit service, not in any third-party platform, so an unavailable knowledge or work item provider cannot take accountability down with it.

---

← Back to the PRD: [README](../README.md) · Previous: [8. Architecture](08-architecture.md) · Next: [10. Extensibility](10-extensibility.md) →
