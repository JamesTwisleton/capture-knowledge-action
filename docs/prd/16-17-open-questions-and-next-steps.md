[← Back to the CKA PRD](../../README.md) · Previous: [15. Decision log](15-decision-log.md)

## 16. Open questions

- Trademark and domain check for the product name.
- Which offline knowledge base works best as an LLM-readable store: the plain Markdown vault alone, a self-hosted wiki such as Wiki.js or Outline, or an embeddings index built over the vault for retrieval. To be researched before v1, under the don't-reinvent rule. (The MVP's choice of the Markdown vault settles the MVP, not this question.)
- The wording of the default prompts for the four starting meeting types (stand-up, ticket refinement, sprint planning, retrospective), and whether any document types ship as defaults beyond the general fallback.
- Verify Jev's practical limits on choice count against realistic candidate pool sizes (a few hundred to around a thousand items).
- Versioning strategy for CKA's own provider interfaces as the ecosystem grows (distinct from vendor API versions, which [Section 10](10-extensibility.md#10-extensibility) covers).
- How much of a vendor's newer API surface to expose as optional capabilities in v1.
- Multi-tenancy: does one deployment ever serve more than one organisation, or is that explicitly out of scope?
- A secrets provider abstraction beyond `.env`: which secrets providers are supported in v1, and how the Google refresh token is held once the MVP's `.env` is no longer the answer.
- **MCP authentication.** Several of CKA's exposed core capabilities are mutating (approve, retry, switch provider), so they need at least the same protection as the equivalent REST endpoints. The MVP otherwise assumes a trusted local machine; whether that is good enough for MCP too, or it needs its own access control, is open.
- **The exact MCP tool list**, and which tools if any are read-only rather than mutating in the MVP.
- **Whether provider-side MCP adapters are ever built.** The spec covers them; whether CKA or the ecosystem around it implements one is a product call for later, not the MVP.
- Initial notification provider list beyond email.

**Closed in v0.9.4** (answered in the sections above, and kept here so the trail is visible): the licence is [Apache License 2.0](13-open-source-and-business-model.md#13-open-source-and-business-model); the public demo is hosted on [GCP](11-deployment-and-portability.md#11-deployment-and-portability); the demo's provider combination is Google Meet, a Markdown vault and GitHub Issues ([7.2](07-core-concepts-and-domain-model.md#72-providers)); and OAuth token handling for the MVP is a published OAuth app plus a refresh token captured once by `./setup.sh` ([J4](../../README.md#j4--setup-wizard)).

## 17. Next steps

1. Circulate this PRD to product and engineering contacts; gather comments on Sections 1–6 first. **Still open.**
2. Prototype journeys J1, J4, J5 and J7 in Penpot; put the prototypes in front of the same reviewers. **Prototyping done, reviewer circulation still open.** Rather than static Penpot mockups, an [interactive click-through prototype](https://jamestwisleton.github.io/capture-knowledge-action/app/) was built and deployed to GitHub Pages, covering all four journeys with fabricated data — putting it in front of reviewers is the part still to do.
3. Build the GitHub Pages landing page from the validated journeys. **Built, out of the PRD's intended sequence** — it was meant to follow reviewer validation of the journeys (step 2's remaining half), but was built alongside the prototype instead. Live at the [site's root](https://jamestwisleton.github.io/capture-knowledge-action/), matching Section 14: a scroll-driven front door separate from the prototype app, linking into it. Worth a second look once reviewer feedback lands, in case the journeys it presents need to change.
4. Build the **MVP**: a real, local, dockerised end-to-end run of J1 — a real Google Meet recording into a watched Drive folder, summarised with a versioned prompt, written to an Obsidian-compatible Markdown vault, evaluated by the decision provider, and turned into comments and approved state changes on **real GitHub issues** — with `./setup.sh` and `./start.sh` as the entry points and a scripted screen recording as the deliverable. Then the one tested cloud deploy path (GCP). **Scoped and specified; build still open.** This replaces the earlier plan for a slice built on fabricated data: the point of the MVP is that nothing in it is fabricated except the seeded demo issues. The interactive prototype keeps that fabricated-data role.

   Scope, acceptance criteria and glossary: [**MVP ticket breakdown**](mvp-tickets.md). It is tracked on GitHub as epic [#1](https://github.com/JamesTwisleton/capture-knowledge-action/issues/1) with 22 sub-issues, T01–T22, in the agreed order. Agent workflow conventions apply to all of them: Ponytail for lean code, Matt Pocock's grill → spec → tickets → implement → review pipeline, strict TDD, and an `agents.md` in every folder.

   Explicitly **not** in the MVP: the web setup wizard (J4), the cost dashboard (J7), audio transcription, notification delivery, providers beyond the three reference implementations, cloud deployment, and more than one meeting type.
5. The landing page tells J1 as a **persona-driven story** — five numbered beats (the team meets → they just talk → Priya says the trigger phrase → CKA kicks into action → Amanda reviews and is offered auto-approve), each with a real screenshot from the prototype, each screenshot linking into it. This replaced an earlier embedded-video approach (v0.9.1), which reviewer feedback found confusing; the prototype itself is where people go to see it move. **Possible follow-up:** similar short story chapters for J4 (setup in minutes), J5 (the trust ramp's disable/switch side) and J7 (costs) if reviewer feedback asks for them — same screenshot pipeline, no video.

> [!NOTE]
> **The repository is the source of truth** — this README and the pages under [`prd/`](.). The Google Doc is a point-in-time copy, so changes are applied here first and the document regenerated from them, never the other way round.

---

← Back to the PRD: [README](../../README.md) · Previous: [15. Decision log](15-decision-log.md) · Next: [Changelog](changelog.md) →
