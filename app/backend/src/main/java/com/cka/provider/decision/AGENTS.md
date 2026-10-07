# `com.cka.provider.decision` — decision providers

`DecisionProvider.detectMentions`: knowledge plus the candidate pool in, a `Mention` per reference
out, each matched to a real candidate with a confidence, or unmatched. T08 adds two
implementations: the Jev adapter, and an LLM fallback ported from TypeSafe's official
`system-one-adapter-python`.

- **A mention can only match an item in the pool it was given.** There is no id-extraction step
  (PRD 8.2), so a provider that "matches" an id it invented breaks the design's central promise.
  A real implementation needs a test that proves it, scoring against a pool that deliberately
  excludes the item the text names.
- **Return unmatched references, don't drop them.** They become unmatched mentions in the audit
  chain (PRD 9.1).
- **Thresholds are not this interface's job.** It reports confidences; `ActionStage` compares them
  against action thresholds, so the same gates apply whichever provider scored.
- **Action classification is not here yet, on purpose.** T08 adds it after reading the upstream
  adapter, so its shape comes from the vendor's `SystemOneResponse`, not from a guess made in T05.
