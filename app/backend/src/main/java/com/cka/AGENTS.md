# `com.cka` — application code

The root package of the CKA core. Everything the backend runs lives here or below.

T05 laid out the packages. Each has its own `AGENTS.md`; start there rather than here when
working inside one.

## Where new code goes

| Package | Holds |
|---|---|
| [`core`](core/AGENTS.md) | The domain model — work item, captured content, summary, action, audit entry — and, in [`core.event`](core/event/AGENTS.md), the event payloads that travel on the bus. Plain records and enums; no framework types. |
| [`provider`](provider/AGENTS.md) | One sub-package per capability (`capture`, `llm`, `knowledge`, `decision`, `workitem`, `eventbus`, `audit`), each an interface plus, from later tickets, its implementations. |
| [`pipeline`](pipeline/AGENTS.md) | The stages that connect providers to the bus, and the audit trail. |
| `config` | *Not created yet.* Spring `@Configuration` that wires the pipeline stages from whichever provider beans are configured. Arrives with the first real provider — see [`provider/AGENTS.md`](provider/AGENTS.md#selecting-an-implementation). |
| `api` | *Not created yet.* The REST surface (T15). |

## Conventions

- **Providers are named by capability, never by vendor.** `KnowledgeProvider`, not
  `ObsidianProvider`; `WorkItemProvider`, not `GitHubProvider`. The vendor name belongs in the
  implementing class (`MarkdownVaultKnowledgeProvider`), not the interface. This is a PRD-level
  rule, not a style preference — Section 10 depends on provider types staying registrable.
- **The domain model stays framework-free.** `com.cka.core` should compile without Spring on the
  classpath. Annotations and wiring belong in `config`, adapters in `provider`.
- **The application class stays bare.** No `@Bean` methods, no component scanning tweaks, no
  `CommandLineRunner` bolted on. Configuration goes in `com.cka.config` where it can be read and
  tested on its own.
- **Health is Actuator's.** Do not hand-roll a health endpoint; `/actuator/health` is what the
  container healthcheck, T04's connectivity slice and CI all read. See the parent
  [`backend/AGENTS.md`](../../../../../AGENTS.md) for the rest of the backend-wide conventions
  (`var` usage, Lombok, the dev Dockerfile).

## Gotchas specific to this package

- **Lombok needs the explicit `annotationProcessorPaths`** already configured in `pom.xml`. javac 25
  dropped processor discovery from `-classpath`, so without it Lombok silently generates nothing —
  no error, the members just are not there. If a `@Getter` appears to do nothing, that is why.
- **A `pom.xml` change is not hot-reloadable.** DevTools restarts the app for compiled output only;
  adding a dependency needs `docker compose restart backend`.
