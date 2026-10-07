package com.cka.pipeline;

import com.cka.core.Knowledge;
import com.cka.core.event.ContentSummarised;
import com.cka.core.event.KnowledgeStored;
import com.cka.provider.eventbus.EventBus;
import com.cka.provider.knowledge.KnowledgeProvider;
import lombok.RequiredArgsConstructor;

/**
 * Knowledge, second half: content summarised in, knowledge stored out — published only once
 * the write has completed, because that completion is what the Action stage is waiting for.
 */
@RequiredArgsConstructor
public class KnowledgeStage {

    private final KnowledgeProvider knowledge;
    private final EventBus bus;

    public void start() {
        bus.subscribe(ContentSummarised.class, summarised -> {
            var page = knowledge.store(summarised.content(), summarised.summary());
            bus.publish(new KnowledgeStored(summarised.chainId(),
                    new Knowledge(page, summarised.content(), summarised.summary())));
        });
    }
}
