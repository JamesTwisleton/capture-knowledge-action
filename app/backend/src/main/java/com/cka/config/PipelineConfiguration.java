package com.cka.config;

import com.cka.pipeline.CaptureStage;
import com.cka.pipeline.KnowledgeStage;
import com.cka.pipeline.RecaptureDeletedNotes;
import com.cka.pipeline.SummariseStage;
import com.cka.provider.capture.CapturedItems;
import com.cka.provider.capture.ContentListener;
import com.cka.provider.eventbus.EventBus;
import com.cka.provider.knowledge.KnowledgeProvider;
import com.cka.provider.llm.LlmProvider;
import java.time.Duration;
import java.util.function.Consumer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.ContextClosedEvent;

/**
 * Where the pipeline is assembled: which provider bean each stage gets, and when the stages
 * start listening. The stages themselves are plain classes with constructor dependencies and no
 * Spring annotations — deliberately, and it is what lets {@code PipelineWiringTest} build the
 * whole chain out of mocks with no container at all.
 *
 * <p>Three stages, each wired by the ticket that shipped the provider it calls: T09 for
 * {@code CaptureStage}, T12 for {@code SummariseStage}, T06 for {@code KnowledgeStage}.
 * {@code ActionStage} arrives with T08.
 */
@Slf4j
@Configuration
class PipelineConfiguration {

    /** Whether deleting a page asks for its meeting again — see application.yaml for why. */
    @Value("${cka.capture.recapture-deleted-notes}")
    private boolean recaptureDeletedNotes;

    /** How often the store is compared against what was captured. */
    @Value("${cka.capture.poll-interval}")
    private Duration reconcileInterval;

    /**
     * Stages are built and started here, on {@link ApplicationReadyEvent}, rather than declared
     * as beans conditional on their providers.
     *
     * <p>The obvious shape — a {@code @Bean} carrying {@code @ConditionalOnBean(LlmProvider.class)}
     * — is quietly unreliable. {@code @ConditionalOnBean} is evaluated while configuration classes
     * are processed, so its answer depends on whether the bean it asks about has been registered
     * yet; Spring's own documentation limits it to auto-configuration for exactly that reason.
     * Both this class and the providers are found by scanning, so "is there an LLM provider?"
     * would be answered in whatever order the scan happens to produce. Asking once every bean
     * definition exists gets one answer instead — which an {@link ObjectProvider} read inside
     * the listener does, and so would a plain {@code Optional} parameter; what matters is only
     * that the question is not asked while configuration classes are still being processed.
     *
     * <p>Nothing to start is a supported way to run: with {@code CKA_PROVIDER_LLM=none} the
     * capture and knowledge halves still work, and the log says why summarising is off rather
     * than leaving someone to infer it from silence.
     *
     * <p><strong>A broker that is not there stops the backend starting, and should.</strong>
     * {@code EventBus.subscribe} declares its queue before attaching a listener, so a failure
     * here leaves no queue — and the queue is anonymous and auto-delete, so nothing recreates it
     * when the broker returns. Catching this would leave a backend that is up, reports healthy,
     * and silently drops every captured recording; letting it fail lets Compose restart until the
     * broker answers, which is what its healthcheck ordering is already for. This is the one
     * place the "an unreachable dependency must not take the backend down" rule does not apply,
     * because here the degraded state cannot recover.
     */
    @Bean
    ApplicationListener<ApplicationReadyEvent> pipelineStarter(
            ObjectProvider<ContentListener> contentListener,
            CapturedItems capturedItems,
            ObjectProvider<LlmProvider> llmProvider,
            ObjectProvider<KnowledgeProvider> knowledgeProvider,
            ObjectProvider<EventBus> eventBus) {
        return ready -> {
            var bus = eventBus.getIfAvailable();
            if (bus == null) {
                // Said once rather than per stage: with no bus nothing can start, and repeating
                // that for each stage adds nothing a reader does not already have.
                log.info("No pipeline stages started: no event bus (cka.provider.eventbus)");
                return;
            }
            // Capture first, so the log reads in the order content travels. Each stage is still
            // independent of the others: a vault with no capture configured stores whatever
            // reaches the bus, which is how half a pipeline can be run on its own.
            start(
                    "Capture",
                    contentListener,
                    "cka.provider.capture",
                    listener -> new CaptureStage(listener, bus).start());
            start("Summarise", llmProvider, "cka.provider.llm", llm -> new SummariseStage(llm, bus).start());
            start(
                    "Knowledge",
                    knowledgeProvider,
                    "cka.provider.knowledge",
                    vault -> new KnowledgeStage(vault, bus).start());

            // Needs both halves, and is off if either is absent: there is nothing to reconcile
            // without something that captures and something that stores.
            var capturing = contentListener.getIfAvailable() != null;
            var store = knowledgeProvider.getIfAvailable();
            if (!recaptureDeletedNotes) {
                log.info("Deleting a note will not capture its meeting again "
                        + "(cka.capture.recapture-deleted-notes is false)");
            } else if (capturing && store != null) {
                var reconciler = new RecaptureDeletedNotes(capturedItems, store, reconcileInterval);
                reconciler.start();
                // Tied to this context's close, because the reconciler is not a bean and so has
                // no lifecycle of its own. Without it, DevTools closing a context on a class
                // change leaves the old thread polling a connection pool that has shut down —
                // one warning an interval, forever, and another thread on the next change.
                //
                // An anonymous class rather than a lambda: Spring reads the event type from the
                // class's generic signature, which a lambda erases, and would then call it for
                // every event the context publishes.
                ready.getApplicationContext().addApplicationListener(new ApplicationListener<ContextClosedEvent>() {
                    @Override
                    public void onApplicationEvent(ContextClosedEvent closed) {
                        reconciler.stop();
                    }
                });
            }
        };
    }

    /**
     * Starts one stage if the provider it calls exists, and names the property that would supply
     * it if not. Stages are independent: a vault with no LLM configured still stores whatever
     * reaches the bus, which is what lets half the pipeline be run and demonstrated on its own.
     */
    private <P> void start(String stage, ObjectProvider<P> provider, String property, Consumer<P> wire) {
        var found = provider.getIfAvailable();
        if (found == null) {
            log.info("{} stage not started: no provider ({})", stage, property);
            return;
        }
        wire.accept(found);
        log.info("{} stage listening", stage);
    }
}
