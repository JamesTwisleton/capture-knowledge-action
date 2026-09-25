[← Back to the CKA PRD](../README.md) · Previous: [13. Open source and business model](13-open-source-and-business-model.md)

## 14. Landing page

A static site on GitHub Pages acts as the product's front door, separate from the running app. Scroll-driven, product-website style, opening with the product name itself as the logo — a **Capture → Knowledge → Action** lockup front and centre, with the tagline "Turn your knowledge into action automatically, with the tools you already know." — then, in order: the golden-path story (five persona beats of J1, each with a real prototype screenshot linking into the prototype), the calls to action, the problem (suite lock-in and upsell — see decision 26 — with composability as the answer), choose your providers, see what it costs, the data-sensitivity warning, and a closing CTA. Every section is individually linkable by URL fragment (`#story`, `#problem`, `#providers`, `#cost`, `#privacy`, `#try`). Copy line to test: "Use whatever AI you like — including your own."

**Built.** Live at [the site's root](https://jamestwisleton.github.io/capture-knowledge-action/); the interactive prototype (J4) moved to `/app/` beneath it so the root URL is the one link worth sharing with anyone — technical or not. Everything above is implemented, including the verbatim data-sensitivity warning and the "Use whatever AI you like" line. Built ahead of the PRD's intended sequence (Section 17 originally has this follow reviewer-validated journeys); it has since been through several rounds of exactly that feedback — see the changelog from v0.9 on.

---

← Back to the PRD: [README](../README.md) · Previous: [13. Open source and business model](13-open-source-and-business-model.md) · Next: [15. Decision log](15-decision-log.md) →
