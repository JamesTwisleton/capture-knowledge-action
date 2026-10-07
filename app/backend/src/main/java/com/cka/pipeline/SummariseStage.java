package com.cka.pipeline;

import com.cka.core.event.ContentCaptured;
import com.cka.core.event.ContentSummarised;
import com.cka.provider.eventbus.EventBus;
import com.cka.provider.llm.LlmProvider;
import lombok.RequiredArgsConstructor;

/** Knowledge, first half: content captured in, content summarised out. */
@RequiredArgsConstructor
public class SummariseStage {

    private final LlmProvider llm;
    private final EventBus bus;

    public void start() {
        bus.subscribe(ContentCaptured.class, captured -> bus.publish(new ContentSummarised(
                captured.chainId(), captured.content(), llm.summarise(captured.content()))));
    }
}
