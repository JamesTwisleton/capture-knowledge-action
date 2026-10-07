# `com.cka.provider` — provider interfaces

One sub-package per capability, each holding that capability's interface and, from later
tickets, its implementations (PRD 7.2):

| Package | Interface | Reference implementation (ticket) |
|---|---|---|
| [`capture`](capture/AGENTS.md) | `ContentListener` | Google Drive folder watcher (T09) |
| [`llm`](llm/AGENTS.md) | `LlmProvider` | LangChain4j (T12) |
| [`knowledge`](knowledge/AGENTS.md) | `KnowledgeProvider` | Markdown vault (T06) |
| [`decision`](decision/AGENTS.md) | `DecisionProvider` | Jev, and an LLM fallback (T08) |
| [`workitem`](workitem/AGENTS.md) | `WorkItemProvider`, `WorkItemMapper` | GitHub Issues (T13) |
| [`eventbus`](eventbus/AGENTS.md) | `EventBus` | RabbitMQ (T07) |
| [`audit`](audit/AGENTS.md) | `AuditStore` | SQL (T10, T14) |

Test doubles for every interface are in the test tree, under
[`com.cka.testdouble`](../../../../../test/java/com/cka/testdouble/AGENTS.md).

## Conventions specific to this package

- **Interfaces are named by capability; implementations by vendor.** `KnowledgeProvider` lives in
  `knowledge`; `MarkdownVaultKnowledgeProvider` goes next to it.
- **Every provider interface extends `Provider`**, whose `checkHealth()` is the connection check
  the PRD makes mandatory (7.2, 10). It reports, it does not throw.
- **Method shapes are a plain, deterministic API**, taking and returning `core` types. Not shaped
  around MCP tool schemas (T18 wraps this API later), and not around any one vendor's SDK.
- **Providers never touch the bus.** They are called by a [`pipeline`](../pipeline/AGENTS.md)
  stage and return a value; the stage publishes. `EventBus` is the one exception, being the bus.

## Selecting an implementation

Implementations are Spring beans, chosen by configuration through DI (PRD 8.1). The convention,
for the first ticket that adds one:

```java
@Component
@ConditionalOnProperty(prefix = "cka.provider", name = "knowledge", havingValue = "markdown-vault")
public class MarkdownVaultKnowledgeProvider implements KnowledgeProvider { … }
```

- Property `cka.provider.<capability>`, set from the env var `CKA_PROVIDER_<CAPABILITY>` by
  Spring's relaxed binding — add it to [`app/.env.example`](../../../../../../../.env.example) with
  the implementation.
- **Provider types stay registrable** (PRD 10) for free: a new type is a new interface with
  beans implementing it. There is no registry class or enum of types to edit, and there must not
  be one.
- The stages get wired into Spring in `com.cka.config` by the same ticket. Until then nothing in
  production implements these interfaces, so nothing is wired — the app boots as it did after T04.

## Gotchas

- **`checkHealth()` is not an Actuator `HealthIndicator`, deliberately.** Actuator rolls every
  indicator into `/actuator/health`, which the container healthcheck and T04's connectivity slice
  read as "is the backend up". A revoked GitHub token must show as a provider problem in the front
  end, not mark the whole backend unhealthy and get the container restarted.
