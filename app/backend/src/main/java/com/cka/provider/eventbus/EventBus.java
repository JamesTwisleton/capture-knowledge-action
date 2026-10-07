package com.cka.provider.eventbus;

import com.cka.core.event.PipelineEvent;
import com.cka.provider.Provider;

import java.util.function.Consumer;

/**
 * Publish and subscribe, deliberately simple (PRD 8.1). Every handoff in the pipeline goes
 * through here: no stage, and no provider, calls the next one directly.
 */
public interface EventBus extends Provider {

    void publish(PipelineEvent event);

    /**
     * Calls {@code handler} with every published event that is an instance of {@code type}.
     * Subscribing to {@link PipelineEvent} itself receives every event.
     */
    <E extends PipelineEvent> void subscribe(Class<E> type, Consumer<? super E> handler);
}
