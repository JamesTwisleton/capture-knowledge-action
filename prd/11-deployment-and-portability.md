[← Back to the CKA PRD](../README.md) · Previous: [10. Extensibility](10-extensibility.md)

## 11. Deployment and portability

- A single Git repository. Run locally first (Docker Compose — which runs the whole stack on one machine in containers — with the in-memory event bus, the offline Markdown vault and the SQLite audit store) to try providers before committing to anything.
- Terraform modules per cloud: GCP, AWS, Azure. "Does your team use GCP? Here is the Terraform." Cloud is itself a provider category.
- **v1 honesty:** one cloud path is built, tested and hosts the public demo. The others ship as scaffolding, clearly marked UNTESTED, with simple run instructions and a contribution path.
- **Design discipline:** an abstraction that only truly works on one provider is worse than none, because it hides the coupling. Provider-specific details stay behind the interface.

---

← Back to the PRD: [README](../README.md) · Previous: [10. Extensibility](10-extensibility.md) · Next: [12. Known limitations and honest tradeoffs](12-known-limitations.md) →
