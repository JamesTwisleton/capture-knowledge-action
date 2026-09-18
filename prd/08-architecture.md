[← Back to the CKA PRD](../README.md) · Previous: [7. Core concepts and domain model](07-core-concepts-and-domain-model.md)

## 8. Architecture

### 8.1 Overview

A web front end talks to a Java core over a REST API (a standard style of web interface between a front end and a back end). The core orchestrates the three stages and talks to every provider through a common interface, so nothing above the provider layer knows or cares which vendor is underneath.

```mermaid
---
title: Architecture overview
---
%%{init: {"theme":"base","themeVariables":{"background":"#000000","fontSize":"20px","fontFamily":"Helvetica, Arial, sans-serif","primaryColor":"#000000","primaryTextColor":"#ffffff","primaryBorderColor":"#ffffff","secondaryColor":"#000000","secondaryTextColor":"#ffffff","secondaryBorderColor":"#ffffff","tertiaryColor":"#000000","tertiaryTextColor":"#ffffff","tertiaryBorderColor":"#ffffff","lineColor":"#ffffff","textColor":"#ffffff","mainBkg":"#000000","nodeBorder":"#ffffff","nodeTextColor":"#ffffff","clusterBkg":"#000000","clusterBorder":"#ffffff","titleColor":"#ffffff","edgeLabelBackground":"#000000"},"flowchart":{"nodeSpacing":60,"rankSpacing":100,"padding":30,"curve":"linear"}}}%%
flowchart TB
  UI["NEXT.JS WEB UI<br/>setup wizard, content types and prompts,<br/>Triage Inbox, Outbox, dashboard, activity view"]
  API["SPRING BOOT CORE<br/>REST API"]
  UI ==> API
  subgraph CORE["CORE"]
    direction LR
    ORCH["STAGE ORCHESTRATION<br/>event driven"]
    REG["PROVIDER REGISTRY<br/>types, implementations,<br/>versions and capabilities"]
    TRUST["TRUST AND<br/>GATING POLICY"]
    COST["COST AND<br/>USAGE TRACKING"]
    PROMPTS["CONTENT TYPES AND<br/>PROMPT TEMPLATES"]
  end
  API ==> CORE
  subgraph PROV["PROVIDER INTERFACES, ONE PER CAPABILITY"]
    direction LR
    P1["MEETING<br/>CAPTURE"]
    P2["KNOWLEDGE"]
    P3["WORK ITEM"]
    P4["LLM<br/>via LangChain4j or LiteLLM"]
    P5["DECISION<br/>Jev"]
    P6["CODE<br/>HOSTING"]
    P7["EVENT BUS"]
    P8["AUDIT<br/>SQL"]
    P9["NOTIFICATION"]
  end
  CORE ==> PROV
  P1 --> I1["Google Meet, Teams, Zoom"]
  P2 --> I2["Markdown vault (Obsidian-compatible),<br/>Confluence, Notion"]
  P3 --> I3["Jira, GitHub Issues,<br/>Azure DevOps Boards"]
  P4 --> I4["Claude, Gemini, OpenAI,<br/>Ollama local models"]
  P7 --> I7["In-memory, Kafka,<br/>Pub/Sub, RabbitMQ"]
  P8 --> I8["SQLite default,<br/>Postgres, MySQL"]
  classDef default fill:#000000,stroke:#ffffff,stroke-width:2px,color:#ffffff
  style CORE fill:#000000,stroke:#ffffff,stroke-width:3px,color:#ffffff
  style PROV fill:#000000,stroke:#ffffff,stroke-width:3px,color:#ffffff
  linkStyle default stroke:#ffffff,stroke-width:3px
```

- **Backend:** Java (latest long-term support, or LTS, release) with Spring Boot (the standard Java application framework). Each provider category is a Java interface; each implementation is a Spring bean (a component Spring creates and wires in for you), selected by configuration through dependency injection (DI) — the mechanism that lets the core ask for "a work item provider" and receive whichever one is configured. The backend does all processing and orchestration, and is the piece that needs to scale in a large organisation.
- **Front end:** Next.js (a React web framework), talking to the core over the REST API. Hosts the setup wizard, content-type and prompt editor, Triage Inbox, Outbox, cost dashboard, and a simple activity and knowledge view (what was captured, what was stored, what was done — backed by the audit service).
- **Event-driven core:** stages publish and consume events ("content captured", "knowledge stored", "action proposed", "action applied", "action failed") over the event bus provider. This is what makes the stages composable rather than a hard-coded chain.
- **LLM access:** through an LLM gateway library — LangChain4j on the JVM (Java Virtual Machine — the Java runtime), or LiteLLM as a self-hosted proxy. This supplies the unified API across cloud and local models and per-call cost calculation; CKA does not reimplement it. Local models (for example via Ollama) are therefore first-class with no extra work.
- **Decision layer — Jev (TypeSafe AI):** a constrained-decision model, not a chat model; it returns a probability, a choice among fixed options or a score, rather than prose. Used for (a) *mention detection*: given a transcript and a scoped candidate list, a calibrated confidence per candidate; (b) *action classification*: given a trigger-phrase segment, whether a mutating action was requested and with what confidence; (c) optionally, *content-type classification*. Jev's fixed-choice nature is what drove the candidate-pool scoping design: the architecture was shaped around the tool's characteristics rather than bolting the tool on.
- **Audit service:** a dedicated service that stores its records in a SQL database through a pluggable SQL provider. SQLite by default (zero setup); Postgres, MySQL and others by configuration. It is optional: if no audit store is configured, audit-dependent functionality (trust ramp, history, activity view) is disabled and the UI signposts clearly what is missing and how to enable it.
- **Event bus provider:** a deliberately simple publish/subscribe interface with implementations for Kafka, Google Pub/Sub and RabbitMQ (all message brokers), plus in-memory for local runs. Delivery and ordering semantics beyond the basics are not abstracted; teams that need Kafka-specific guarantees extend the provider themselves.
- **Secrets and tokens:** OAuth tokens and provider credentials are held in a secrets provider (local encrypted store by default; cloud secret managers via Terraform). No provider-specific claim, key format or error type may leak into the domain layer.

### 8.2 The LLM's actual jobs

| Job | Input → output | Provider |
|---|---|---|
| Transcription | Audio → text. **Skipped when the capture provider already supplies a transcript** — Google Meet and Microsoft Teams both can — which also removes the cost of this step | LLM / speech-to-text provider (cloud or local) |
| Summarisation | Text → summary, decisions, follow-ups, **using the prompt template for the item's content type** ([7.6](07-core-concepts-and-domain-model.md#76-content-types-and-summary-prompts)) | LLM provider |
| Mention detection | Text + candidate pool → confidence per item | Jev |
| Action classification | Trigger-phrase segment → action requested? + confidence | Jev |
| Content-type classification (optional) | Text → one of the configured content types, when no rule has assigned one | Jev |

Each job can use a different provider and model independently, since they have very different cost and complexity profiles. The dashboard attributes spend per job and, because summarisation prompts are per content type, per content type too.

---

← Back to the PRD: [README](../README.md) · Previous: [7. Core concepts and domain model](07-core-concepts-and-domain-model.md) · Next: [9. Trust, safety and audit](09-trust-safety-and-audit.md) →
