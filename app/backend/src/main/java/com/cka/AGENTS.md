# `com.cka` — application code

The root package of the CKA core. Everything the backend runs lives here or below.

Right now it holds one class, `CaptureKnowledgeActionApplication`, and that is deliberate: T01–T04
only need the app to boot, serve health and restart on compile. The structure below arrives with
T05.

## Where new code goes

T05 defines the provider abstractions, and it is the first ticket to add packages here. Expected
shape, from [`prd/mvp-tickets.md`](../../../../../../../docs/prd/mvp-tickets.md) and
[Section 8](../../../../../../../docs/prd/08-architecture.md):

| Package | Holds |
|---|---|
| `com.cka.core` | The domain model — work item, event payload, page reference. No framework types. |
| `com.cka.provider` | One sub-package per capability (`knowledge`, `eventbus`, `llm`, `decision`, `workitem`, `audit`), each an interface plus its implementations. |
| `com.cka.config` | Spring `@Configuration` that selects provider implementations from env vars. |
| `com.cka.api` | The REST surface (T15). |

Add an `AGENTS.md` to each as you create it, saying what belongs there and what does not.

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
  [`backend/AGENTS.md`](../../../../AGENTS.md) for the rest of the backend-wide conventions
  (`var` usage, Lombok, the dev Dockerfile).

## Gotchas specific to this package

- **Lombok needs the explicit `annotationProcessorPaths`** already configured in `pom.xml`. javac 25
  dropped processor discovery from `-classpath`, so without it Lombok silently generates nothing —
  no error, the members just are not there. If a `@Getter` appears to do nothing, that is why.
- **A `pom.xml` change is not hot-reloadable.** DevTools restarts the app for compiled output only;
  adding a dependency needs `docker compose restart backend`.
