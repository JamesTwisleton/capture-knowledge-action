# `com.cka.provider.llm` — LLM providers

`LlmProvider.summarise`: captured content in, `Summary` out, using the prompt for the content's
type (PRD 8.2). `LangChain4jLlmProvider` (T12) is the implementation.

| | |
|---|---|
| Selected by | `cka.provider.llm=langchain4j` (`CKA_PROVIDER_LLM`), and **off by default** |
| Model | whichever `ChatModel` bean exists; `AnthropicChatModelConfiguration` supplies Claude |
| Settings | `cka.llm.api-key` (`LLM_API_KEY`), `cka.llm.model`, `max-tokens`, `timeout-seconds` |
| Prompt | read from `PromptStore.current(contentType)` — T11 applies the fallback, not this class |

- **This is the only capability that ships switched off.** Every other provider can satisfy
  itself from the stack alone; this one needs a credential nobody has on a fresh clone, and
  selecting it without one stops the backend starting. So `docker compose up` works immediately
  and `CKA_PROVIDER_LLM=langchain4j` turns summarising on — two variables rather than one,
  because a blank key with the provider selected is a boot failure.
- **It holds a `ChatModel`, not an Anthropic client.** That interface is LangChain4j's
  vendor-neutral one, so Gemini, OpenAI and a local Ollama model are the same type here:
  substituting one is a dependency and a `@Bean`, never an edit to the provider.
- **A second vendor is selected by `cka.llm.vendor`, not by `@ConditionalOnMissingBean`.** That
  annotation is evaluated while configuration classes are processed, so which vendor won would
  depend on registration order — the same
  unreliability [`../../config/AGENTS.md`](../../config/AGENTS.md) bans for `@ConditionalOnBean`.
  `anthropic` is the default; a Gemini binding carries `havingValue = "gemini"` and the property
  decides, deterministically.
- **Attribute to the vendor, never to the gateway.** `Provenance.provider` comes from
  `ChatModel.provider()` — "anthropic", "ollama" — because "langchain4j" is true of every LLM
  call CKA will ever make and so distinguishes nothing, and the audit trail exists to say which
  provider and model decided (PRD 9.3). The model name comes from the *response*, not the
  configuration, so it records what actually answered; a binding that reports none falls back to
  `unreported` rather than failing a summary that otherwise succeeded.
- **The health check makes a real call.** Same reasoning as the SQL layer's `SELECT 1`: the
  failure worth catching is a key that is present and revoked, which looks healthy until
  something uses it. The shortest prompt available, because the front end polls this.
- **Both the transcript and the platform's own summary go to the model** (PRD 8.2). Meet supplies
  a summary and ignoring it would throw away work already done. `platformSummary` is the one
  nullable field on `CapturedContent`, so content without one must not put "null" in the prompt.
- **Summarising is the whole job.** It does not extract work item ids — mention detection is the
  decision provider's, against the real candidate pool — and it does not write to the knowledge
  provider. `SummariseStage` publishes its result; the knowledge provider picks it up from the bus.
- **Always fill `Summary.producedBy`** with the provider and the exact model. The audit trail and
  cost tracking attribute by it.
- **Don't widen this interface for the decision fallback.** T08's LLM-backed decision provider is
  its own `DecisionProvider` implementation, built on LangChain4j directly; it does not call
  through `LlmProvider`.
