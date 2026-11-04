package com.cka.config;

import com.cka.pipeline.SummariseStage;
import com.cka.provider.eventbus.EventBus;
import com.cka.provider.llm.LlmProvider;
import java.util.ArrayList;
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
 * <p>One stage so far. T12 wires {@code SummariseStage} because T12 is the first ticket to ship
 * a provider a stage needs; the other three arrive with T06, T08 and T09.
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
            ObjectProvider<LlmProvider> llmProvider, ObjectProvider<EventBus> eventBus) {
        return _ -> {
            var llm = llmProvider.getIfAvailable();
            var bus = eventBus.getIfAvailable();
            if (llm != null && bus != null) {
                new SummariseStage(llm, bus).start();
                log.info("Summarise stage listening for captured content");
                return;
            }

            // Every absence, not the first one found, each naming the property that turns it
            // on — so one restart is enough when more than one is missing.
            var missing = new ArrayList<String>();
            if (llm == null) {
                missing.add("no LLM provider (cka.provider.llm)");
            }
            if (bus == null) {
                missing.add("no event bus (cka.provider.eventbus)");
            }
            log.info("Summarise stage not started: {}", String.join(", ", missing));
        };
    }
}
