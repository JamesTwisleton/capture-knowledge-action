package com.cka.testsupport;

import com.cka.core.event.PipelineEvent;
import com.cka.provider.ProviderHealth;
import com.cka.provider.eventbus.EventBus;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Delivers each event synchronously, on the publishing thread, to every matching subscriber,
 * and remembers everything published so a test can assert on it. Synchronous delivery makes
 * a whole pipeline run finish before {@code publish} returns — no waiting in tests.
 */
public class InMemoryEventBus implements EventBus {

    private record Subscription<E extends PipelineEvent>(Class<E> type, Consumer<? super E> handler) {

        void deliver(PipelineEvent event) {
            if (type.isInstance(event)) {
                handler.accept(type.cast(event));
            }
        }
    }

    private final List<Subscription<?>> subscriptions = new ArrayList<>();
    private final List<PipelineEvent> published = new ArrayList<>();

    @Override
    public void publish(PipelineEvent event) {
        published.add(event);
        // Copied first: a handler may itself subscribe, which must not disturb this delivery.
        List.copyOf(subscriptions).forEach(subscription -> subscription.deliver(event));
    }

    @Override
    public <E extends PipelineEvent> void subscribe(Class<E> type, Consumer<? super E> handler) {
        subscriptions.add(new Subscription<>(type, handler));
    }

    public List<PipelineEvent> published() {
        return List.copyOf(published);
    }

    @Override
    public ProviderHealth checkHealth() {
        return ProviderHealth.up("in memory");
    }
}
