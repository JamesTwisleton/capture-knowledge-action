[← Back to the CKA PRD](../README.md) · Previous: [9. Trust, safety and audit](09-trust-safety-and-audit.md)

## 10. Extensibility

Two distinct axes, kept separate in the design:

1. **New implementations of existing provider types** — for example a new work item provider. Fully specified in v1: implement the interface, register the bean, document the configuration.
2. **Entirely new provider types and pipeline steps** — for example an agent provider that generates code from a decision, or a step that does something none of the current stages do. Stated as a first-class design goal in v1: provider types are registrable and pipeline steps pluggable, and the orchestration must not hard-code "meeting then transcribe then comment". The registration mechanism is intentionally not fully specified yet.

Extensibility applies within providers too: the individual operations of a provider (for a work item provider — create, comment, transition, query) are separately overridable, so a team can customise how tickets are created without replacing the whole provider.

**Vendor APIs change.** Jira, Confluence, GitHub and the rest release new API versions with new features, and retire old ones. A provider implementation therefore declares which version of the vendor's API it targets and which *optional capabilities* it supports beyond the required core operations — for example, a newer work item API might add bulk transitions or richer comment formatting. The core discovers those optional capabilities at runtime and uses them where present, so a provider written against a newer API can expose new functionality without any change to the core, and an older provider keeps working with the features it has. More than one version of a provider can be installed side by side; the wizard shows which one is active and flags when a vendor has deprecated the version in use.

Contribution-friendliness is a product requirement, not an afterthought: clear interfaces, a reference implementation per category, documented extension points, and a contributor guide.

---

← Back to the PRD: [README](../README.md) · Previous: [9. Trust, safety and audit](09-trust-safety-and-audit.md) · Next: [11. Deployment and portability](11-deployment-and-portability.md) →
