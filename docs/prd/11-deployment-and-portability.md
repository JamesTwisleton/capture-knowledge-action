[← Back to the CKA PRD](../../README.md) · Previous: [10. Extensibility](10-extensibility.md)

## 11. Deployment and portability

- A single Git repository. Run locally first (Docker Compose — which runs the whole stack on one machine in containers) to try providers before committing to anything. The local environment runs a **real RabbitMQ broker**, the offline Markdown vault and the SQLite audit store — not the in-memory event bus, which is kept for tests. Decoupling between the stages is only worth claiming if the local run exercises it against a real bus.
- **Entry points:** `./setup.sh` (prerequisite checks, a terminal credential wizard, writes `.env`) and `./start.sh` (runs Docker Compose). See [J4](../../README.md#j4--setup-wizard).
- **Developer experience**, treated as part of the deliverable rather than left to each contributor to rig up:
  - Debug ports exposed and mapped for the JVM, and for other services where applicable, so a debugger can attach to anything in the environment. The ports are listed in the documentation.
  - Backend hot reload: build output mounted as a volume, so `mvn compile` triggers an automatic app restart.
  - Front-end hot reload via the Next.js dev server's **polling-based** file watcher (`WATCHPACK_POLLING=true`). Native filesystem change events do not reliably cross a Docker bind mount, so polling is the idiomatic approach here rather than a workaround.
  - A `docs/` folder explaining how the environment is put together, how to debug it, and the decisions behind it, linking to the README rather than duplicating it.
- Terraform modules per cloud: GCP, AWS, Azure. "Does your team use GCP? Here is the Terraform." Cloud is itself a provider category.
- **Target cloud host:** the public demo, once deployed beyond the local MVP, is hosted on **GCP**, which therefore becomes the one tested deploy path.
- **v1 honesty:** one cloud path is built, tested and hosts the public demo. The others ship as scaffolding, clearly marked UNTESTED, with simple run instructions and a contribution path. *The MVP is local only — the tested cloud deploy path follows it, and no part of it is built yet.*
- **Design discipline:** an abstraction that only truly works on one provider is worse than none, because it hides the coupling. Provider-specific details stay behind the interface.

---

← Back to the PRD: [README](../../README.md) · Previous: [10. Extensibility](10-extensibility.md) · Next: [12. Known limitations and honest tradeoffs](12-known-limitations.md) →
