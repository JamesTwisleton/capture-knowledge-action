[← Back to the CKA PRD](../README.md) · Previous: [15. Decision log](15-decision-log.md)

## 16. Open questions

- Confirm the licence (Apache 2.0 recommended).
- Trademark and domain check for the product name.
- Which cloud hosts the public demo and is therefore the tested deploy path in v1.
- Which provider combination the public demo uses (an offline vault and GitHub Issues in place of Confluence and Jira is the current thought).
- Which offline knowledge base works best as an LLM-readable store: the plain Markdown vault alone, a self-hosted wiki such as Wiki.js or Outline, or an embeddings index built over the vault for retrieval. To be researched before v1, under the don't-reinvent rule.
- The wording of the default prompts for the four starting meeting types (stand-up, ticket refinement, sprint planning, retrospective), and whether any document types ship as defaults beyond the general fallback.
- Verify Jev's practical limits on choice count against realistic candidate pool sizes (a few hundred to around a thousand items).
- Versioning strategy for CKA's own provider interfaces as the ecosystem grows (distinct from vendor API versions, which [Section 10](10-extensibility.md#10-extensibility) covers).
- How much of a vendor's newer API surface to expose as optional capabilities in v1.
- Multi-tenancy: does one deployment ever serve more than one organisation, or is that explicitly out of scope?
- OAuth token storage and refresh: which secrets providers are supported in v1.
- Initial notification provider list beyond email.

## 17. Next steps

1. Circulate this PRD to product and engineering contacts; gather comments on Sections 1–6 first.
2. Prototype journeys J1, J4, J5 and J7 in Penpot; put the prototypes in front of the same reviewers.
3. Build the GitHub Pages landing page from the validated journeys.
4. Build a local-first vertical slice of J1 with example data (fabricated meetings and work items), then the one tested cloud deploy path.

---

← Back to the PRD: [README](../README.md) · Previous: [15. Decision log](15-decision-log.md) · Next: [Changelog](changelog.md) →
