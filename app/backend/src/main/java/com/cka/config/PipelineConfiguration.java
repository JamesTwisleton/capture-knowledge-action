package com.cka.config;

import com.cka.pipeline.KnowledgeStage;
import com.cka.pipeline.SummariseStage;
import com.cka.provider.eventbus.EventBus;
import com.cka.provider.knowledge.KnowledgeProvider;
import com.cka.provider.llm.LlmProvider;
import java.util.function.Consumer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Where the pipeline is assembled: which provider bean each stage gets, and when the stages
 * start listening. The stages themselves are plain classes with constructor dependencies and no
 * Spring annotations — deliberately, and it is what lets {@code PipelineWiringTest} build the
 * whole chain out of mocks with no container at all.
 *
 * <p>Two stages, each wired by the ticket that shipped the provider it calls: T12 for
 * {@code SummariseStage}, T06 for {@code KnowledgeStage}. The other two arrive with T08 and T09.
 */
@Slf4j
@Configuration
class PipelineConfiguration {

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
            ObjectProvider<LlmProvider> llmProvider,
            ObjectProvider<KnowledgeProvider> knowledgeProvider,
            ObjectProvider<EventBus> eventBus) {
        return _ -> {
            var bus = eventBus.getIfAvailable();
            if (bus == null) {
                // Said once rather than per stage: with no bus nothing can start, and repeating
                // that for each stage adds nothing a reader does not already have.
                log.info("No pipeline stages started: no event bus (cka.provider.eventbus)");
                return;
            }
            start("Summarise", llmProvider, "cka.provider.llm", llm -> new SummariseStage(llm, bus).start());
            start(
                    "Knowledge",
                    knowledgeProvider,
                    "cka.provider.knowledge",
                    vault -> new KnowledgeStage(vault, bus).start());
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
