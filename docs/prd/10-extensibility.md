[← Back to the CKA PRD](../../README.md) · Previous: [9. Trust, safety and audit](09-trust-safety-and-audit.md)

## 10. Extensibility

Two distinct axes, kept separate in the design:

1. **New implementations of existing provider types** — for example a new work item provider. Fully specified in v1: implement the interface, register the bean, document the configuration.
2. **Entirely new provider types and pipeline steps** — for example an agent provider that generates code from a decision, or a step that does something none of the current stages do. Stated as a first-class design goal in v1: provider types are registrable and pipeline steps pluggable, and the orchestration must not hard-code "meeting then transcribe then comment". The registration mechanism is intentionally not fully specified yet.

Extensibility applies within providers too: the individual operations of a provider (for a work item provider — create, comment, transition, query) are separately overridable, so a team can customise how tickets are created without replacing the whole provider.

**Named extension points.** Two are called out because they are easy to miss when writing a new provider:

- **The work item mapper** ([7.9](07-core-concepts-and-domain-model.md#79-work-item-model)). Every work item provider ships one, wired by Spring DI, converting between the provider's native concept and the generic core work item. Adding a work item provider means writing its mapper.
- **The connection/health check.** A required operation on every provider interface, not an optional extra. The front end's provider status view and the wizard's connection tester are built on it, so a provider without one cannot be shown as working.
- **MCP compatibility** ([8.1](08-architecture.md#81-overview)). A spec published in the repo covering the whole stack: CKA's own core capabilities, so any MCP-capable agent can query and act on triage, the audit trail, knowledge and provider status directly; and every provider interface, so a provider can be implemented as — or fronted by — any MCP-conformant server rather than bespoke CKA code. This decentralises where "the server" lives and extends the composability claim past swappable providers: you are not locked into CKA's own process either. Architecturally the deterministic REST/JSON API is what CKA is built on, and the MCP reference implementation is a thin compatibility layer wrapping it; MCP is the target entry point in the everyday sense, which describes intended usage rather than which layer is authoritative. The MVP builds only the core-capabilities side; provider-side MCP adapters are a documented future extension point, not built now.

**Vendor APIs change.** Jira, Confluence, GitHub and the rest release new API versions with new features, and retire old ones. A provider implementation therefore declares which version of the vendor's API it targets and which *optional capabilities* it supports beyond the required core operations — for example, a newer work item API might add bulk transitions or richer comment formatting. The core discovers those optional capabilities at runtime and uses them where present, so a provider written against a newer API can expose new functionality without any change to the core, and an older provider keeps working with the features it has. More than one version of a provider can be installed side by side; the wizard shows which one is active and flags when a vendor has deprecated the version in use.

Contribution-friendliness is a product requirement, not an afterthought: clear interfaces, a reference implementation per category, documented extension points, and a contributor guide.

**Repo conventions**, which apply equally to human contributors and to coding agents:

- **An `agents.md` in every folder**, including Java packages, describing that folder's purpose and how to work in it. An agent dropped into a directory should not have to infer intent from the code around it.
- **Minimal, hand-picked dependencies.** No starter bundles; each dependency is added because something needs it.
- **Test-driven development**, red-green-refactor, rather than tests written after the fact.
- **Decisions documented in a `docs/` folder** that links to the README rather than duplicating it — what was set up, how it works, how to debug it, and why it was done that way.

---

← Back to the PRD: [README](../../README.md) · Previous: [9. Trust, safety and audit](09-trust-safety-and-audit.md) · Next: [11. Deployment and portability](11-deployment-and-portability.md) →
