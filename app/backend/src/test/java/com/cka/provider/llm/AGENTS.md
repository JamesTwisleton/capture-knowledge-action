# `com.cka.provider.llm` — LLM provider tests

Tests for [`main/.../provider/llm`](../../../../../../main/java/com/cka/provider/llm/AGENTS.md).

| Class | What it proves |
|---|---|
| `LangChain4jLlmProviderTest` | The prompt chosen, the content sent, the attribution recorded |
| `SummariseAcrossTheBusTest` | T12's acceptance criterion: captured content in, summarised out |
| `LlmProviderWiringTest` | Selecting the provider wires it *and* leaves the stage listening |

- **The `ChatModel` is mocked, and nothing here calls a model.** What this provider does is pick
  a prompt, put the content in front of it and attribute the answer; a real call would make none
  of that more certain while costing money and a network on every run. The one thing a live call
  would prove — that the key works — is `checkHealth()`'s job in the running stack.
- **No `KnowledgeProvider` appears in `SummariseAcrossTheBusTest`, deliberately.** T12's criterion
  is "verifiable with no knowledge provider running at all; if the test needs one, the decoupling
  isn't real", so its absence is the assertion. It is imported there only to be named in a
  sentence explaining that.
- **`LlmProviderWiringTest` asserts the stage is *listening*, not that a bean exists.** The stage
  is not a Spring bean — `PipelineConfiguration` constructs and starts it — so "a bean exists"
  would have passed while nothing had subscribed, which is the actual failure: captured content
  reaching a bus nobody listens to and simply stopping.
- **It needs a context of its own because the provider ships off.** Every other `@SpringBootTest`
  in the repository proves the *off* path, since `cka.provider.llm` defaults to `none`. This one
  is why defaulting it on is caught immediately: doing so broke all 37 context loads at once.
- **It supplies `InMemoryEventBus` as the `EventBus` bean** rather than standing up a broker.
  Subscribing to RabbitMQ with no broker running throws, on purpose — see
  [`../../config/AGENTS.md`](../../../../../../main/java/com/cka/config/AGENTS.md).
