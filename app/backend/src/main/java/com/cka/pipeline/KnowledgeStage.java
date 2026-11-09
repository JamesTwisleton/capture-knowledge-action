package com.cka.pipeline;

import com.cka.core.Knowledge;
import com.cka.core.event.ContentSummarised;
import com.cka.core.event.KnowledgeStored;
import com.cka.provider.eventbus.EventBus;
import com.cka.provider.knowledge.KnowledgeProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Knowledge, second half: content summarised in, knowledge stored out — published only once
 * the write has completed, because that completion is what the Action stage is waiting for.
 */
@Slf4j
@RequiredArgsConstructor
public class KnowledgeStage {

    private final KnowledgeProvider knowledge;
    private final EventBus bus;

    public void start() {
        bus.subscribe(ContentSummarised.class, summarised -> {
            var page = knowledge.store(summarised.content(), summarised.summary());
            // Where it landed, because that is the one thing a person goes and opens.
            log.info("Stored \"{}\" [chain {}]", page.id(), summarised.chainId());
            bus.publish(KnowledgeStored.builder()
                    .chainId(summarised.chainId())
                    .knowledge(Knowledge.builder()
                            .page(page)
                            .content(summarised.content())
                            .summary(summarised.summary())
                            .build())
                    .build());
        });
    }
}
