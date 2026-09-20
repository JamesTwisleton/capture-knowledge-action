[← Back to the CKA PRD](../README.md) · Previous: [13. Open source and business model](13-open-source-and-business-model.md)

## 14. Landing page

A static site on GitHub Pages acts as the product's front door, separate from the running app. Scroll-driven, product-website style, opening with the product name itself as the logo — a **Capture → Knowledge → Action** lockup front and centre, with the tagline "Turn your knowledge into action automatically, with the tools you already know." — then, in order: the golden-path story (five persona beats of J1, each with a real prototype screenshot linking into the prototype), the calls to action, the problem (suite lock-in and upsell — see decision 26 — with composability as the answer), choose your providers, see what it costs, the data-sensitivity warning, and a closing CTA. Every section is individually linkable by URL fragment (`#story`, `#problem`, `#providers`, `#cost`, `#privacy`, `#try`). Copy line to test: "Use whatever AI you like — including your own."

**Built.** Live at [the site's root](https://jamestwisleton.github.io/capture-knowledge-action/); the interactive prototype (J4) moved to `/app/` beneath it so the root URL is the one link worth sharing with anyone — technical or not. Everything above is implemented, including the verbatim data-sensitivity warning and the "Use whatever AI you like" line. Built ahead of the PRD's intended sequence (Section 17 originally has this follow reviewer-validated journeys); it has since been through several rounds of exactly that feedback — see the changelog from v0.9 on.

### Demo recording

Alongside the click-through prototype — which stays on fabricated data — the site carries a **screen recording of the real running MVP**, following a written demo script. The script doubles as the MVP's test plan, which is why it is specified here rather than left to be improvised at recording time. The beats: drop the recording into the watched folder; watch polling find it; watch it processing; open the note written to the knowledge store; review the triage list; accept some proposals and reject the deliberate misclassification; see the automatic comments and the approved actions on the real GitHub issues; show the audit trail carrying both the accept and the reject; break the GitHub token to show the error state; reset.

**The script is written:** [`demo-script.md`](demo-script.md). It opens on the lock-in problem and the composability answer before any software appears, which matches the landing page's own order (Section 2 before the product), and it closes by naming cost tracking as deferred rather than quietly omitting it.

A **reset control in the front end** makes the demo repeatable: it calls a backend endpoint that undoes the GitHub actions taken, deletes the synthetic data and repopulates it, returning the repo and the app to their starting state. The action decision chain is what tells the reset which actions to undo. The endpoint is demo-configuration only and must not be reachable in a normal deployment. See [`mvp-tickets.md`](mvp-tickets.md) T18 and T20.

---

← Back to the PRD: [README](../README.md) · Previous: [13. Open source and business model](13-open-source-and-business-model.md) · Next: [15. Decision log](15-decision-log.md) →
