package com.cka.pipeline;

import com.cka.core.event.ContentCaptured;
import com.cka.core.event.ContentSummarised;
import com.cka.provider.eventbus.EventBus;
import com.cka.provider.llm.LlmProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/** Knowledge, first half: content captured in, content summarised out. */
@Slf4j
@RequiredArgsConstructor
public class SummariseStage {

    private final LlmProvider llm;
    private final EventBus bus;

    public void start() {
        bus.subscribe(ContentCaptured.class, captured -> {
            var summary = llm.summarise(captured.content());
            // The model and prompt version are what the audit chain records, so a reader can
            // match a line here against an entry there without opening the database.
            log.info(
                    "Summarised with prompt v{} via {}/{} — {} characters [chain {}]",
                    summary.promptVersion(),
                    summary.producedBy().provider(),
                    summary.producedBy().model(),
                    summary.text().length(),
                    captured.chainId());
            bus.publish(ContentSummarised.builder()
                    .chainId(captured.chainId())
                    .content(captured.content())
                    .summary(summary)
                    .build());
        });
    }
}
