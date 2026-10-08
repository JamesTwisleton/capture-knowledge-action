# `com.cka.provider.llm` — LLM providers

`LlmProvider.summarise`: captured content in, `Summary` out, using the prompt for the content's
type (PRD 8.2). T12 implements it on LangChain4j.

- **Summarising is the whole job.** It does not extract work item ids — mention detection is the
  decision provider's, against the real candidate pool — and it does not write to the knowledge
  provider. `SummariseStage` publishes its result; the knowledge provider picks it up from the bus.
- **Always fill `Summary.producedBy`** with the provider and the exact model. The audit trail and
  cost tracking attribute by it.
- **Don't widen this interface for the decision fallback.** T08's LLM-backed decision provider is
  its own `DecisionProvider` implementation, built on LangChain4j directly; it does not call
  through `LlmProvider`.
