# `com.cka.config` — where the application is assembled

Spring wiring that is about *which* implementation and *when* it starts, not about how any one
of them works. Vendor-specific beans stay beside their provider — `RabbitEventBusConfiguration`
in `provider/eventbus`, `AnthropicChatModelConfiguration` in `provider/llm` — because that is a
detail of one implementation, while this package is the composition of the whole.

| | |
|---|---|
| `PipelineConfiguration` | Builds the pipeline stages and the reconciler and starts them (T09, T12, T06) |
| `ClockConfiguration` | The one clock the application reads time from, in UTC (T06) |

## Conventions specific to this package

- **Stages are constructed here, not annotated there.** `com.cka.pipeline` classes carry no
  Spring annotations at all, which is what lets `PipelineWiringTest` assemble the entire chain
  from mocks with no container. Adding `@Component` to a stage would take that away for nothing.
- **Start on `ApplicationReadyEvent`, not during bean creation.** Subscribing while the context
  is still being built means the broker can deliver an event into a half-built object graph, and
  the first symptom would be a failure in whatever that event reached — nowhere near here.
- **Resolve optional providers with `ObjectProvider`, never `@ConditionalOnBean`.** That
  annotation is evaluated while configuration classes are processed, so its answer depends on
  whether the bean it asks about happens to have been registered yet; Spring's own documentation
  limits it to auto-configuration for that reason. Both this package and the providers are found
  by scanning, so the ordering is not ours to rely on. Ask once every bean definition exists and
  the question has one answer — an `ObjectProvider` read inside a listener does that, and so does
  a plain `Optional` parameter. What matters is only that it is not asked while configuration
  classes are still being processed, which is where a stage wired by scan order comes from.
- **A provider that is absent is a stage that does not start, and the log says so.** Running with
  `CKA_PROVIDER_LLM=none` is supported — the capture and knowledge halves work without it — so
  the absence is reported at `INFO` rather than treated as a misconfiguration.
- **Stages start independently of each other.** Each needs the bus and its own provider, and
  nothing orders them: a vault with no LLM configured still stores whatever reaches the bus. That
  is what lets half a pipeline be run and demonstrated, and it is why the absent-provider message
  names one stage rather than refusing to start any.
- **The bus is the one shared requirement**, so it is reported once rather than per stage. With
  no bus there is nothing for any stage to subscribe to, and saying that three times tells a
  reader nothing the first line did not.

## Gotchas

- **One exception to "a provider being down must not take the backend down":** a failed
  `subscribe()` at startup. The queue is declared before the listener attaches and is anonymous
  and auto-delete, so a caught failure leaves no queue and nothing recreates it when the broker
  comes back — the backend would run healthy and silently drop every recording. Compose already
  orders startup behind the broker's healthcheck, so failing lets it restart until the broker
  answers. See [`../pipeline/AGENTS.md`](../pipeline/AGENTS.md).
