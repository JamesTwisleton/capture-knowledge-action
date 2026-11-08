package com.cka.provider.llm;

import dev.langchain4j.model.anthropic.AnthropicChatModel;
import dev.langchain4j.model.chat.ChatModel;
import java.time.Duration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

/**
 * The one vendor binding behind {@link LangChain4jLlmProvider}: a {@link ChatModel} talking to
 * Claude.
 *
 * <p>Here rather than in {@code com.cka.config} for the reason {@code RabbitEventBusConfiguration}
 * gives — which provider is in use is the {@code cka.provider.llm} property's business; how this
 * one reaches a model is this file's.
 *
 * <p><strong>Substituting a vendor is this file, not the provider.</strong> Gemini, OpenAI and a
 * local Ollama model are all {@code ChatModel}s in LangChain4j, so pointing CKA at one means a
 * dependency and a {@code @Bean} returning that type — {@code LangChain4jLlmProvider} never
 * learns which it got, and asks the model itself for the attribution it records.
 *
 * <p>The vendor is chosen by {@code cka.llm.vendor}, defaulting to {@code anthropic}, and
 * <em>not</em> by {@code @ConditionalOnMissingBean(ChatModel.class)}. That annotation is
 * evaluated while configuration classes are processed, so
 * whether this one backs off for a vendor declared elsewhere depends on which was registered
 * first; Spring limits it to auto-configuration for exactly that reason, and
 * {@code com.cka.config} already bans the same pattern for the same cause. A second binding
 * therefore carries {@code havingValue = "gemini"} and the property picks one deterministically,
 * rather than two configurations racing to decide which model CKA talks to.
 */
@Configuration
@ConditionalOnProperty(prefix = "cka.provider", name = "llm", havingValue = "langchain4j")
@ConditionalOnProperty(prefix = "cka.llm", name = "vendor", havingValue = "anthropic")
class AnthropicChatModelConfiguration {

    /**
     * Read from the {@link Environment} rather than injected with {@code @Value}, so a missing
     * key fails with a sentence naming the env var instead of Spring's "Could not resolve
     * placeholder". The key is the one credential this provider has, and getting told which one
     * is absent is the difference between a one-minute fix and a stack trace.
     *
     * @param model   the exact model id, recorded against every summary for the audit trail
     * @param timeout generous, because summarising a 40-minute transcript is not a quick call
     */
    @Bean
    ChatModel chatModel(Environment environment) {
        var apiKey = environment.getProperty("cka.llm.api-key");
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("No LLM API key: set LLM_API_KEY in app/.env "
                    + "(cka.llm.api-key). Without it there is nothing to summarise with — set "
                    + "CKA_PROVIDER_LLM=none to start the stack without an LLM provider at all.");
        }
        return AnthropicChatModel.builder()
                .apiKey(apiKey)
                .modelName(environment.getProperty("cka.llm.model", "claude-sonnet-5"))
                .maxTokens(environment.getProperty("cka.llm.max-tokens", Integer.class, 8192))
                .timeout(Duration.ofSeconds(environment.getProperty("cka.llm.timeout-seconds", Long.class, 180L)))
                .build();
    }
}
