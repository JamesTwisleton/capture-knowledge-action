[← Back to the CKA PRD](../README.md)

# CKA MVP: ticket breakdown

Source: scoping conversation on 18 September 2026, building on PRD v0.9.3; revised 19 September 2026 from the demo-script session (see [`demo-script.md`](demo-script.md)), which introduced the content-summarised event and the low-confidence proposed-comment path.
Status: **filed as GitHub issues** — epic [#1](https://github.com/JamesTwisleton/capture-knowledge-action/issues/1) with T01–T21 attached as sub-issues. Acceptance criteria below are drawn from the discussion; anything not settled is listed under **Open questions** at the end and flagged inline. This page stays the canonical narrative version; the issues are the working tracker.

> [!NOTE]
> This page is delivery planning, not part of the PRD's numbered sections, so editing it does not require a PRD version bump. It is linked from [Section 17, Next steps](16-17-open-questions-and-next-steps.md#17-next-steps).

## MVP goal

A local, dockerised, end-to-end run of the core value proposition, in real (not throwaway) code:

1. Drop a Google Meet recording into a watched Drive folder.
2. The system picks it up, processes it, writes the knowledge to an Obsidian-compatible Markdown vault.
3. A decision layer proposes actions against GitHub Issues.
4. A human accepts or rejects proposals in a triage list.
5. Accepted actions are applied to real GitHub issues; comments are posted automatically on mentioned tickets.
6. Everything is visible in a front end (activity log, triage, errors), and everything is audited.

The end state is a screen recording of the full flow, plus a repo anyone can clone, configure with env vars, and run.

Not in the MVP: the full setup wizard (PRD J4), the cost dashboard (J7), other providers, cloud deployment, multi-meeting-type support beyond one, and provider-side MCP adapters (the spec covers them; none are built).

## Structure on GitHub

GitHub has no native epic type. Of the options considered — an issue with a checklist of linked sub-issues, native sub-issues, or a Project with an "Epic" label — **native sub-issues** were chosen: one parent epic, [#1](https://github.com/JamesTwisleton/capture-knowledge-action/issues/1), with T01–T21 attached as real GitHub sub-issues. That gives a progress bar on the parent and a proper parent/child relationship rather than a markdown checklist.

Labels: every ticket carries `mvp`; the parent also carries `epic`. Area labels (`backend`, `frontend`, `infrastructure`, `provider`, `documentation`) are applied per ticket.

Ownership: the earlier decision was that James hand-builds the Spring Boot and Next.js skeletons and the three reference providers (Meet, Obsidian, GitHub) to keep architectural ownership, with strict TDD. Other tickets are candidates for delegation to coding agents. Assignees are not yet set on the issues.

## Standing conventions (apply to every ticket)

- **TDD**, proper red-green-refactor, not tests bolted on afterwards.
- **Ponytail** governs what agents write: does it need to exist, is it already in the codebase, does the stdlib do it, does the platform do it, is there an installed dependency, one line, and only then the minimum code that works.
- **Matt Pocock's skills pipeline** for the agent workflow: grill, spec, tickets (tracer-bullet, with blocking edges), implement (test-first, then code review before commit), review. His TypeScript-specific rules need adapting for the Java backend.
- **An `agents.md` in every folder** across the repo (including Java packages), explaining the folder's purpose and how an agent should work in it.
- **Minimal, hand-picked dependencies.** No Spring Initializr starter bundle.
- **Config via env vars** (`.env`). No hardcoded secrets or demo shortcuts.
- **Not throwaway code.** Narrow scope, production-quality implementation.
- Use the glossary at the end of this file consistently.
- Providers are named by capability, never by vendor.
- **A deterministic API is the core.** The application services and REST surface (T15) are the single source of truth, designed, built and tested as a normal deterministic API in their own right. MCP compatibility (T18) is a compatibility layer built on top of that already-working API once it exists, not a parallel design surface the API is shaped around or a dependency the core waits on.

## Agreed order

| # | Ticket | Issue |
|---|--------|-------|
| T01 | Docker Compose environment | [#2](https://github.com/JamesTwisleton/capture-knowledge-action/issues/2) |
| T02 | Spring Boot skeleton in backend container | [#3](https://github.com/JamesTwisleton/capture-knowledge-action/issues/3) |
| T03 | Next.js skeleton in frontend container | [#4](https://github.com/JamesTwisleton/capture-knowledge-action/issues/4) |
| T04 | Health check connectivity slice | [#5](https://github.com/JamesTwisleton/capture-knowledge-action/issues/5) |
| T05 | Provider interface skeletons | [#6](https://github.com/JamesTwisleton/capture-knowledge-action/issues/6) |
| T06 | Knowledge provider (Markdown vault) | [#7](https://github.com/JamesTwisleton/capture-knowledge-action/issues/7) |
| T07 | Event bus implementation | [#8](https://github.com/JamesTwisleton/capture-knowledge-action/issues/8) |
| T08 | Decision layer (Jev, with LLM fallback) | [#9](https://github.com/JamesTwisleton/capture-knowledge-action/issues/9) |
| T09 | Google Meet capture provider | [#10](https://github.com/JamesTwisleton/capture-knowledge-action/issues/10) |
| T10 | SQL persistence layer | [#11](https://github.com/JamesTwisleton/capture-knowledge-action/issues/11) |
| T11 | Prompt storage and versioning | [#12](https://github.com/JamesTwisleton/capture-knowledge-action/issues/12) |
| T12 | LLM provider | [#13](https://github.com/JamesTwisleton/capture-knowledge-action/issues/13) |
| T13 | GitHub work item provider | [#14](https://github.com/JamesTwisleton/capture-knowledge-action/issues/14) |
| T14 | Audit log (action decision chain) | [#15](https://github.com/JamesTwisleton/capture-knowledge-action/issues/15) |
| T15 | Backend endpoints for the front end | [#16](https://github.com/JamesTwisleton/capture-knowledge-action/issues/16) |
| T16 | Front end views | [#17](https://github.com/JamesTwisleton/capture-knowledge-action/issues/17) |
| T17 | Error handling | [#18](https://github.com/JamesTwisleton/capture-knowledge-action/issues/18) |
| T18 | MCP compatibility: publish the spec, and expose the core through it | [#22](https://github.com/JamesTwisleton/capture-knowledge-action/issues/22) |
| T19 | Test data, seed and reset | [#19](https://github.com/JamesTwisleton/capture-knowledge-action/issues/19) |
| T20 | Setup and start scripts, README | [#20](https://github.com/JamesTwisleton/capture-knowledge-action/issues/20) |
| T21 | Demo script and recording | [#21](https://github.com/JamesTwisleton/capture-knowledge-action/issues/21) |

## Credentials and config needed

- **Google OAuth app** (client ID and secret), published (not left in "Testing"), so refresh tokens don't hit the 7-day test-mode expiry. James's own app for the demo; anyone running it themselves creates and publishes their own.
- **GitHub personal access token** with scopes to read, comment on and close issues.
- **LLM provider API key** (for summarising meetings, and as the decision fallback).
- **Jev (TypeSafe) API key**, optional. If absent, the decision provider falls back to the LLM provider.
- **Trigger phrase** (the team's wake word for actions), set in `.env`.
- Vault location, watched Drive folder, and other provider settings as env vars.

---

## T01. Docker Compose environment

**Goal:** the skeleton every other piece plugs into, and a pleasant local dev loop.

**Scope**
- Compose file with containers for backend (Spring Boot), frontend (Next.js) and event bus, stubbed until their internals exist.
- Backend and frontend on the same Docker network.
- Debug ports exposed and mapped for the JVM (and for other services where applicable), listed in the documentation, so a debugger can attach to anything in the environment.
- Backend hot reload: build output mounted as a volume, so running `mvn compile` triggers an automatic app restart (Spring Boot DevTools or equivalent).
- Configuration via env vars.
- Documentation: a `docs/` folder explaining how the Docker environment is set up, how it works, how to debug it, and the decisions made and why. It links to the main README rather than duplicating it.
- `agents.md` for the folder(s) involved.

**Acceptance criteria**
- `docker compose up` brings up the stubbed environment.
- A remote debugger from an IDE can attach to the backend container.
- Documentation exists as described and is enough for an agent or new contributor to understand the shape and the reasons.

**Notes:** end-to-end verification of hot reload needs T02.

## T02. Spring Boot skeleton in the backend container

**Scope**
- Built from scratch with hand-picked dependencies, no Initializr starter bundle, no bloat.
- Runs inside the backend container from T01, with restart-on-compile and debugger attach working.
- Actuator health endpoint available.
- TDD from the first line of code.
- `agents.md` in every folder.

**Acceptance criteria:** app starts in the container, health endpoint responds, a code change followed by `mvn compile` restarts the app, and the first tests were written before the code they cover.

## T03. Next.js skeleton in the frontend container

**Scope**
- Minimal hand-picked dependencies.
- Runs inside the frontend container.
- Hot reload of the front end working in the container, via polling-based file watching (`WATCHPACK_POLLING=true`), since native filesystem change events are unreliable across a Docker bind mount.
- Debug support consistent with the backend.
- `agents.md` in every folder.

**Acceptance criteria:** the page loads from the container and edits show up without rebuilding the image.

## T04. Health check connectivity slice

**Goal:** the thinnest possible proof that the front end and back end can talk to each other.

**Scope**
- Use the built-in Spring Actuator health endpoint (or a minimal custom one).
- A basic front-end page showing green ("connected to back end") or red ("cannot connect").
- Correct networking between the containers.

**Acceptance criteria:** stopping the backend container turns the page red; starting it turns it green.

**Note:** decide whether the browser calls the backend on its exposed host port or the Next.js server proxies the call, because the browser cannot resolve Docker service names.

## T05. Provider interface skeletons

**Goal:** the domain model's abstractions, defined before any implementation fills them in.

**Scope**
- Interfaces, named by capability: content listener, knowledge provider, event bus provider, LLM provider, decision provider, work item provider, audit store.
- Implementations selected by configuration through Spring dependency injection, matching provider type to implementation bean.
- **Work item mapper** interface: converts a provider's native concept into the generic core work item and back; one mapper implementation per provider, wired by Spring DI.
- Core work item model supports **nested work items** (parent/child), and carries a **provider work item type name** string holding the provider's own label (for example "Epic") distinct from the generic type.
- **Event payload** shape defined for what travels on the bus.
- Provider types remain registrable (PRD Section 10), so new types can be added without changing the core.
- No real implementations here, only test doubles.
- Service/application-layer method shapes are designed as a clean, deterministic API in their own right, not pre-shaped around MCP tool schemas — the MCP compatibility layer (T18) wraps this API once it exists, rather than the API being derived from it.
- `agents.md` in each package.

**Acceptance criteria:** the core can be wired with test doubles for every interface and run a fake event through the chain.

## T06. Knowledge provider: Markdown vault

**Why first:** everything else writes into or reads from it, so it gives the other providers a stable target.

**Scope**
- Local Markdown vault following Obsidian conventions: one Markdown file per captured item, double-square-bracket links, tags or a metadata block at the top of each file, attachments in a sub-folder.
- **Subscribes to the "content summarised" event** and does the write itself. The LLM provider (T12) does not call it and does not know which knowledge provider is configured; it publishes and this ticket's code picks the work up.
- Stores transcript, summary and a link to the recording; returns a page reference.
- Emits a "knowledge stored" event when a write completes (delivered over the bus once T07 lands).
- Vault location set by env var and mounted as a volume so it can be opened in Obsidian from the host.
- Obsidian is not a dependency.
- Connection/health check for the front end's provider status.

**Acceptance criteria:** publishing a content-summarised event produces a readable Markdown file in the vault and a knowledge-stored event on the bus, with no direct call from the publisher; the file opens correctly in Obsidian and in a plain text editor.

## T07. Event bus implementation

**Scope**
- A real broker running locally in Docker Compose: **RabbitMQ**, not only in-memory.
- Simple publish/subscribe provider interface. Events per the PRD: content captured, **content summarised**, knowledge stored, action proposed, action applied, action failed.
- Every handoff in the pipeline is one of these, including LLM provider → knowledge provider, so no provider calls the next one directly and any of them can be swapped or triggered independently.

**Acceptance criteria:** a test publishes a fake event and a subscriber receives it via the real broker.

**Why RabbitMQ:** simplest of the PRD's candidates (Kafka, RabbitMQ, Google Pub/Sub) to stand up locally — one container, no coordinator/cluster service to run alongside it (unlike Kafka), well-supported by Spring AMQP.

## T08. Decision layer: Jev, with LLM fallback

**Scope**
- Decision provider interface, with two implementations:
  - **Jev adapter:** a thin wrapper written in this codebase that calls the TypeSafe API directly and slots in like the other providers. TypeSafe ship JavaScript and Python SDKs and a Python LangChain integration, but no *official* Java SDK, so the adapter calls the HTTP API itself. Community Java clients exist and were evaluated; all were days old and effectively unused, two were forks of each other, and one had no licence at all, so none is a safe dependency for code that handles credentials. Re-check before building — the ecosystem is young and this may have changed. Uses Jev's choice and score primitives.
  - **LLM fallback:** if no Jev key is configured, the LLM provider makes the same decision through the same interface (less calibrated, same shape). **Do not invent this shape.** It is a Java port of TypeSafe's **official** [`system-one-adapter-python`](https://github.com/typesafe-ai/system-one-adapter-python) — a drop-in LLM-backed replacement for their own `system_one` API — so the contract is defined upstream by the vendor. Port: the `SystemOneResponse` shape, the `Noul` / `Score` / `Choice` question types, corrective retries on malformed model output, probability normalisation, and the per-attempt debug and token-usage reporting. Read that repo before writing the class. It runs in-process on LangChain4j, not as a Python sidecar, so the core stays one runtime and cost tracking still sees every call.
- Consumes the "knowledge stored" event. It is not called directly by the LLM step.
- **Mention detection is Jev's job alone.** It classifies directly against the candidate pool of real work items, scoring confidence per candidate from the knowledge content itself (transcript and summary near the trigger phrase). There is no separate ID-extraction step — the LLM step (T12) only summarises.
- Mention detection has **three outcomes, and no fourth** — nothing is discarded silently:
  - At or above the mention threshold (default 90%, configurable): comment on the item automatically, no approval needed.
  - Below the threshold, but a real candidate scored: create a **proposed comment** in the triage list for a human to confirm. Accepting posts the comment; rejecting posts nothing.
  - No candidate matched at all: record an **unmatched mention** (visible in the audit chain and front end via T14/T16). Nothing is proposed, because there is no item to act on.
- Action classification: for segments containing the trigger phrase, decide whether a mutating action was requested against whichever candidate mention detection matched, and with what confidence.
- The gate chain as **action checks** compared against **action thresholds**: mention, intent, decision, trust (PRD Section 9).
- Produces triage proposals — of both kinds, proposed comments and proposed mutating actions — and automatic comment actions, each recording provider, model and confidence.
- Trigger phrase read from `.env`.
- Built in two passes within this one ticket: first against a stubbed candidate pool and a stubbed LLM fallback, so it is fully testable on its own; then wired to the real GitHub candidate pool (T13) and the real LLM fallback (T12) once those land.

**Acceptance criteria:** publishing a fake knowledge-stored event with stubbed candidate work items produces a triage decision, without needing real Meet or GitHub; once T12 and T13 land, the same flow runs against real data.

**Notes:** Jev is early access (released 15 September 2026), so its API may move.

## T09. Google Meet capture provider

**Scope**
- Content listener interface, with a **folder watcher** implementation that **polls** the Drive folder on an interval (decision: polling, not push notifications, because the MVP runs on a local machine with no public webhook).
- Reads the Meet-generated transcript and summary alongside the recording and uses them together.
- Assigns the meeting type from the meeting title. For the MVP, "Sprint Refinement".
- Google OAuth using the configured, **published** OAuth app (client ID and secret in env — publishing avoids the 7-day refresh-token expiry that applies to unpublished "Testing" apps). A one-time consent flow (run from the setup script) captures a refresh token; the core uses it to mint access tokens and refreshes them silently.
- Publishes a "content captured" event.
- Exposes polling status so the front end can show that it is polling and has found something.
- Connection/health check.

**Acceptance criteria:** dropping a recording (with its Meet transcript and summary) into the watched folder produces a content-captured event within one poll interval.

## T10. SQL persistence layer

**Goal:** the shared database foundation that prompt storage (T11) and the audit log (T14) both build on, so neither ticket has to invent it.

**Scope**
- A SQL persistence layer behind the audit store interface from T05, using **Spring Data JDBC** for the data access layer. SQLite by default (zero setup, single file); other SQL databases substitutable by configuration. Keep the SQL dialect-portable so no database-specific types leak into the domain layer.
- Versioned schema migrations via **Flyway**, checked into the repo alongside the schema they create, so the tables added by later tickets (prompts in T11, the audit chain in T14) arrive the same way.
- Database file kept on a volume in Docker Compose so data survives container restarts (proposed).
- Connection settings and database location via env vars.
- Connection/health check, consistent with the other providers.
- Repository tests run against a real SQLite database (temp file or in-memory), written test-first.
- Does not define the prompt or audit tables themselves; those belong to T11 and T14.
- `agents.md` in each folder.

**Acceptance criteria**
- With no extra configuration, the app creates and connects to a SQLite database on startup and applies migrations.
- A migration added by a later ticket applies cleanly to an existing database.
- Only SQLite is tested in the MVP. The layer is written so other SQL databases can be substituted later, and the documentation says so plainly.

**Depends on:** T02 (Spring Boot skeleton), T05 (audit store interface). Blocks T11 and T14.

**Notes:** the PRD treats the audit store as optional (audit-dependent features are disabled if none is configured). In the MVP it is always on, with SQLite as the default.

## T11. Prompt storage and versioning

**Scope**
- Summary prompts as first-class data in SQL, keyed by meeting type (content type).
- Versioned: every change creates a new version; the current active version is fetched at capture time; the version used is recorded against each output so the audit trail shows which prompt version produced which result.
- Seeded with a default Sprint Refinement prompt; configurable.
- A proper, extensible meeting-type-to-prompt mechanism, exercised with a single type in the MVP.
- Endpoints to list, create and version prompts are in the MVP (see T15, T16), not deferred.

**Acceptance criteria:** a prompt can be created, edited (producing a new version), fetched by meeting type, and a previous version retrieved.

**Depends on:** T10 (SQL persistence layer).

## T12. LLM provider

**Scope**
- LLM provider through LangChain4j.
- Summarises the Meet transcript and summary using the prompt for the meeting type (from T11). Extracting ticket identifiers is not part of this step — Jev's mention detection (T08) works directly from this summarised knowledge content against the real candidate pool.
- No audio transcription in the MVP: Meet always supplies a transcript, so this step only summarises; a capture provider with no transcript is out of scope for now.
- **Subscribes to the "content captured" event** and **publishes a "content summarised" event** when done. It does not pass its result to the knowledge provider, call it, or know which one is configured — T06 subscribes and does the write, and it is that write completing which fires the knowledge-stored event Jev consumes.
- Records provider and model for each call, for the audit trail.
- API key via env var.

**Acceptance criteria:** given a content-captured event carrying a Meet transcript and summary for a Sprint Refinement meeting, publishes a content-summarised event carrying a summary in the configured prompt's shape. Verifiable with no knowledge provider running at all — if the test needs one, the decoupling isn't real.

**Note:** this ticket no longer depends on T06. The LLM and knowledge providers are joined only by an event, so either can be built and tested without the other.

## T13. GitHub work item provider

**Scope**
- Query a candidate pool of issues (MVP scope: **all issues in the configured repo, no filtering**), comment on issues, and apply approved state changes (for example close).
- GitHub work item mapper implementing the work item mapper interface.
- **Unmatched reference handling:** if a mentioned identifier from the decision layer (T08) doesn't correspond to a real issue in the candidate pool, no work-item action is attempted for it. It is surfaced as a distinct entry in the audit chain and front end (see T14, T16) rather than silently dropped.
- Comments on mentioned tickets are posted automatically (low risk, no trigger phrase needed). Anything that changes state only happens after triage approval.
- Personal access token via env var; connection/health check.

**Acceptance criteria:** the provider can list candidates, comment on a real issue, close it, and correctly reports when a mentioned identifier has no match in the candidate pool.

## T14. Audit log: action decision chain

**Scope**
- Audit store built on the SQL persistence layer from T10 (SQLite by default; any SQL database substitutable).
- Records the full **action decision chain**: original captured content, through knowledge storage, mention detection (including unmatched mentions), comments, proposals, human accept or reject, applied action and outcome or error.
- Every entry records provider, model, confidence, decider (system or human) and prompt version.
- Both accept and reject decisions are written and feed the trust data for that action type.
- Accept/reject history visible in the front end, as groundwork for the PRD J5 "you've rejected most of these recently" suggestion.

**Acceptance criteria:** after a full run, the chain for any applied or rejected action can be traced back to the original recording.

**Depends on:** T10 (SQL persistence layer). Must land before T15 (backend endpoints) and T18 (MCP compatibility) — both are consumers of the services this ticket's data backs.

## T15. Backend endpoints for the front end

**Goal:** a normal, deterministic REST/JSON API for the Next.js front end — the core application service surface for triage, the audit trail, knowledge and provider status. This settles the API contract before the front end is built. This is the ground truth; it is built and tested as an API in its own right, not shaped around MCP tool schemas. T18 (MCP compatibility) wraps this same API once it exists, rather than the other way around.

**Scope**
- Activity log (processing states over time).
- Triage list, plus accept, reject and accept-all.
- Audit/decision chain data.
- Provider health/status for the three configured providers.
- Stored knowledge (documentation) view.
- Reset and seed endpoints (see T19).
- Prompt list, create and edit endpoints, versioned (see T11).

**Acceptance criteria:** documented endpoints, each covered by tests, covering everything T16 needs.

## T16. Front end views

**Scope**
- **Connection tester / health dashboard:** live status of each provider (Meet, Obsidian vault, GitHub). Built as reusable scaffolding for the later setup wizard, not throwaway UI.
- **Activity log:** live view of polling, "found something", processing, saved to knowledge store, actions taken, with distinct failed and unmatched-mention states alongside pending and done.
- **Stored documentation view:** open the note that was written to the vault.
- **Triage list:** proposals with confidence, transcript excerpt, and which provider and model produced them. Two kinds, visually distinguishable at a glance: **proposed comments** (a mention below the confidence threshold — accepting posts the comment) and **proposed actions** (a mutating change — accepting applies it). Accept and reject individually, plus accept all. Accepting in triage is the gate; there is no second confirmation dialog.
- Accept/reject history visible.
- **Trust-ramp suggestions:** the triage view surfaces the PRD J5 banners (for example "you've rejected most of these recently", "switch to auto-approve", "disable or switch provider") once there is enough accept/reject history for an action type.
- **Prompt editor:** list, view versions of, and edit the meeting-type prompt(s), via the T15 endpoints.
- **Reset button** (see T19).

**Acceptance criteria:** every beat of the demo script (T21) can be performed from the UI.

## T17. Error handling

**Scope**
- Failures from the GitHub API (invalid or expired token, network errors, and so on) become a visible failed state on the affected action, shown in the activity log and the front end, and written to the audit chain with provider and error.
- **Retry, switch-provider and dismiss** actions available on a failed action from the front end (PRD J6 Outbox behaviour).
- Nothing stalls silently and nothing is lost.
- The demo shows this by setting the GitHub token to nonsense.
- These service methods (retry, switch provider, dismiss) land here as ordinary deterministic API operations; T18's MCP layer exposes them as tools once it wraps the finished API, with no separate phasing needed.

**Acceptance criteria:** with an invalid token, accepting a proposal results in a clearly displayed error state and an audit entry; restoring the token allows the action to be retried, switched to another provider, or dismissed.

**Note:** notification delivery (email, Slack and so on) is out of scope for the MVP — the visible failed state in the front end is the notification.

## T18. MCP compatibility: publish the spec, and expose the core through it

**Goal:** a deterministic API (T15, extended by T16/T17) is the core of CKA; MCP is a compatibility layer built on top of that already-working API, not a design the API is shaped around. MCP is still the intended **target entry point** in the sense that matters to users — the primary way an AI agent is meant to reach CKA's capabilities day to day — but architecturally it is downstream: this ticket wraps the finished, independently-tested application services from T15–T17 in MCP tools, it doesn't drive their shape.

MCP compatibility itself is a **specification/methodology**, published in the repo, that any component — a provider, or the core itself — can implement independently. There is no single "the CKA MCP server"; this ticket publishes that spec and builds CKA's own reference implementation of one part of it (the core's capabilities).

**Discoverability is a first-class requirement.** The bar isn't just "an MCP client can call this" — it's that someone who has never heard of CKA, who simply asks their own AI assistant to do something CKA can do, gets it to work without bespoke integration. That means tool names, descriptions and parameter schemas have to be self-sufficient: a generic MCP client, with no CKA-specific prompting or documentation, should be able to read the exposed tool list and correctly map an ordinary user request onto the right tool calls, purely from what the schema says.

**Scope**
- **The spec** (`docs/mcp-spec.md`, linked from the README and the GitHub Pages site): defines the MCP tool/resource shapes for two things, across the whole stack —
  1. **CKA's own core capabilities**: triage (list/query/approve/reject), the action decision chain/audit log, stored knowledge (vault entries), provider health/status, error recovery (retry/switch-provider/dismiss).
  2. **Each provider interface** (content listener, LLM provider, knowledge provider, decision provider, work item provider): the shape an external MCP server would need to expose to satisfy that interface, so a provider implementation doesn't have to be bespoke CKA code — it can be any MCP-conformant server.
- **For the MVP**, CKA's own reference implementation follows the spec for **the core capabilities only** (item 1 above), built with the official **Spring AI MCP Server Boot Starter** (on the official `modelcontextprotocol/java-sdk`, maintained with Spring AI) — an installed, purpose-built dependency, per Ponytail, not a hand-rolled JSON-RPC implementation.
- This reference implementation runs inside the backend container (T01/T02), as part of the same Spring Boot app, and each MCP tool calls straight through to the same deterministic service methods T15–T17 already built and tested — it adds a tool-shaped face on top, it does not reimplement or restructure the underlying logic.
- Because it lands after T15, T16 and T17, this ticket covers every capability, including retry/switch-provider/dismiss, in one pass — there is no need to phase the MCP surface behind a later ticket.
- Tool descriptions and schemas are written and tested for the discoverability bar above: clear names, full natural-language descriptions of what each tool does and when to use it, and structured parameters — good enough that a fresh AI agent, given only a plain user request, picks the right tool without CKA-specific guidance.
- **Provider interfaces satisfied by an external MCP server** (item 2 above — for example, a work item provider that's just any MCP server matching the spec, rather than bespoke CKA code) are specified but **not built** for the MVP. This is deliberate: the MVP's provider scope stays at the three reference implementations already agreed (Meet, Markdown vault, GitHub), and MCP-based providers are documented as the natural next extension, not built now.
- `agents.md` documenting the spec and how the MVP's own reference implementation maps to it.

**Acceptance criteria:** the spec document exists, is linked from the README and GitHub Pages, and covers both the core capabilities and every provider interface; an MCP client with no prior CKA-specific knowledge (for example Claude Desktop or the Claude CLI, configured against CKA's own reference implementation), given a plain-language request such as "do I have anything from my last meeting that needs reviewing?", correctly discovers and calls the right tool(s) from the exposed list alone, and can act (for example approve a pending triage item) with the result identical to doing the same thing from the front end.

**Depends on:** T15, T16, T17 (the deterministic API and its full set of operations, including error recovery, wrapped here rather than driving their design).

**Notes:** this is the non-functional composability pitch from the demo script made concrete — CKA becomes a tool surface other AI agents can act through, not only a pipeline you configure through env vars, and it stays decentralised: nothing about the architecture assumes CKA's own process is the only place the spec is implemented. The demo recording (T21) **mentions** MCP but does not show an external MCP client driving CKA live — decided when the script was written, to keep the recording independent of T18 being finished.

**Open:** authentication/authorization for the core's exposed capabilities. Several of them are real, mutating actions (approve, retry, switch provider), so they need at least the same protection as the equivalent REST endpoints — undecided for the MVP, since the MVP otherwise assumes a trusted local machine. Exact tool list and read-only vs. mutating boundaries also to confirm when scoped. Whether provider-side MCP adapters ever get built, or stay spec-only indefinitely, is a product call for later.

## T19. Test data, seed and reset

**Scope**
- A real recording made by James: a call about a Sprint Refinement meeting, with the trigger phrase spoken and ticket numbers mentioned, including a deliberately wrong but real ticket number spoken, so mention detection matches an unrelated real issue with enough confidence to produce a misclassified proposal for the reject path (distinct from an unmatched mention, T08's case for a reference with no real match at all).
- Seed synthetic GitHub issues in a dedicated demo repository.
- A **reset button in the front end** calling a backend endpoint that undoes the GitHub actions taken, deletes the synthetic data, and repopulates it, so the demo can be run again from a clean slate.
- The audit chain is what tells the reset which actions to undo.

**Acceptance criteria:** run the full flow, press reset, and the repo and app are back to the starting state.

**Notes:** verify what the GitHub API permits. Comments can be deleted and issues reopened; deleting issues outright may need admin permission. Use a dedicated repo.

## T20. Setup and start scripts, README

**Scope**
- `./setup.sh`, a terminal wizard: checks prerequisites (Docker and so on), then asks for the Google OAuth client ID and secret and runs the one-time consent to capture a refresh token, the GitHub token, the LLM provider key, the optional Jev key and the trigger phrase, with inline instructions and required scopes for each, and writes `.env`.
- `./start.sh` runs Docker Compose.
- README: requirements (Docker, a Google account, and so on), how to create and **publish** a Google OAuth app, how to create a GitHub token, the config guide, and how to run the demo.
- ~~Add an **Apache License 2.0** `LICENSE` file at the repo root; README includes a short License section pointing to it.~~ **Done ahead of this ticket** — pulled forward because the repository is public and describes itself as open source, so every day without a licence was a day readers had no grant of rights. The README's License section and `LICENSE` are already in place.
- No separate doctor script.

**Acceptance criteria:** a fresh clone, `./setup.sh`, `./start.sh`, and the pipeline runs.

## T21. Demo script and recording

**Scope:** a written script, doubling as the MVP test plan, then the recording.

**Status:** the script is **written** — see [`demo-script.md`](demo-script.md), narrated 19 September 2026 and structured for delivery, with a beat checklist mapping it onto the ten beats below. The recording remains to be made.

**Beats**
1. Drop the recording into the watched folder.
2. The interface shows polling and that it found something.
3. Show it processing.
4. Show the note saved to the knowledge store and open it.
5. Show the triage list.
6. Accept some proposals and reject the deliberate misclassification.
7. Show the comments posted on tickets (comments need no approval) and the approved ticket actions in GitHub.
8. Show the audit trail reflecting the accept and reject decisions.
9. Break the GitHub token (set to nonsense) and show the error state in the front end.
10. Reset from the front end and show the clean state.

---

## Open questions and gaps

- **MCP authentication** (T18): several of the core's exposed capabilities are mutating (approve, retry, switch provider); the MVP otherwise assumes a trusted local machine, so this needs a decision when T18 is scoped.
- **MCP exact tool list** (T18) and which tools, if any, are read-only vs. mutating for the MVP.
- **Whether provider-side MCP adapters ever get built** (T18) — spec'd for the whole stack, but the MVP only implements the core-capabilities side.
- **Trademark and domain check** for the product name (PRD Section 16) — still open.

## Glossary additions

- **Content listener:** generic interface for anything that listens for new content from a capture provider.
- **Folder watcher:** a content listener implementation for folder-based capture (for example a Drive folder of Meet recordings). Other implementations, such as a webhook receiver, could exist.
- **Event payload:** the message/data shape travelling on the event bus.
- **Content summarised event:** published by the LLM provider once it has summarised (and transcribed first, if the capture platform supplied no transcript). The knowledge provider subscribes to it and performs the write; that write completing is what fires the knowledge-stored event. The LLM provider never calls the knowledge provider directly.
- **Proposed comment:** a triage entry for a mention that scored below the mention threshold. Accepting it posts the comment on the work item; rejecting it posts nothing. Distinct from a **proposed action**, which is a mutating change, and from an **unmatched mention**, which has no candidate to act on and so is never proposed.
- **MCP compatibility spec:** a specification, published in the repo, defining the MCP tool/resource shapes for CKA's core capabilities and for each provider interface. It's a methodology any component can implement independently, not a single centralised server — CKA's own reference implementation (T18) is one conformant instance of it, not the only possible one.
- **Trigger phrase:** the canonical term for the team's wake word (PRD 7.4). "Wake word" and "wake phrase" are plain-language glosses only — never identifiers, config keys or field names.
- **Work item mapper:** interface converting a provider's native concept (for example a Jira epic or GitHub milestone) into the generic core work item and back; one implementation per provider, wired by Spring DI.
- **Nested work items:** parent/child work item relationships, generalising Jira, Azure DevOps Boards and GitHub Issues.
- **Provider work item type name:** a string on a work item holding the provider's own type label (for example "Epic"), kept distinct from the generic work item type.
- **Action check:** a single check within the mention, intent, decision, trust chain.
- **Action threshold:** the value a confidence score is compared against at an action check.
- **Action decision chain:** the full traceable record from original captured content through to whatever actions were taken from it.
- **Unmatched mention:** a reference that mention detection cannot confidently match to any candidate in the pool. No action is proposed for it, but it is recorded and surfaced (audit chain, front end) rather than silently dropped.

---

← Back to the PRD: [README](../README.md) · Related: [16–17. Open questions and next steps](16-17-open-questions-and-next-steps.md) · Epic: [#1](https://github.com/JamesTwisleton/capture-knowledge-action/issues/1)
