package com.cka.provider.llm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.cka.core.CapturedContent;
import com.cka.core.Prompt;
import com.cka.core.event.ContentCaptured;
import com.cka.core.event.ContentSummarised;
import com.cka.provider.eventbus.EventBus;
import com.cka.provider.prompt.PromptStore;
import com.cka.testsupport.InMemoryEventBus;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.ModelProvider;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.response.ChatResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * Selecting the provider wires it, and wires the stage that uses it.
 *
 * <p>Worth a context load of its own because {@code cka.provider.llm} ships as {@code none} — so
 * every other test in the suite proves the <em>off</em> path, and nothing would otherwise prove
 * the on path until someone ran the stack with a real key. The failure this catches is concrete:
 * defaulting the property to {@code langchain4j} while the key is unset breaks every
 * {@code @SpringBootTest} in the repository at once, and the reverse mistake — a conditional
 * that never matches — would be invisible.
 *
 * <p>No API call happens here. Building an {@link ChatModel} does not contact the vendor, so a
 * throwaway key is enough to prove the beans exist; what they would do with it is
 * {@code LangChain4jLlmProviderTest}'s subject.
 */
@SpringBootTest(
        properties = {
            "cka.provider.llm=langchain4j",
            "cka.llm.api-key=not-a-real-key-and-never-used",
            "cka.provider.eventbus=none"
        })
@Import(LlmProviderWiringTest.AnEventBusThatNeedsNoBroker.class)
@DisplayName("Selecting the LangChain4j provider wires it and the Summarise stage")
class LlmProviderWiringTest {

    @Autowired
    private LlmProvider llm;

    @Autowired
    private EventBus bus;

    @MockitoBean
    private ChatModel mockedModel;

    @MockitoBean
    private PromptStore prompts;

    @Test
    @DisplayName("The provider bean is the LangChain4j one")
    void theProviderBeanIsTheLangChain4jOne() {
        assertThat(llm).isInstanceOf(LangChain4jLlmProvider.class);
    }

    /**
     * The stage needs an {@link EventBus}, and subscribing to RabbitMQ with no broker running
     * throws — deliberately, see {@code PipelineConfiguration}. So this supplies the in-memory
     * bus instead of standing a broker up: what is under test is which beans Spring assembles,
     * and {@code SummariseAcrossTheBusTest} already proves what they do once assembled.
     */
    @TestConfiguration
    static class AnEventBusThatNeedsNoBroker {

        @Bean
        EventBus eventBus() {
            return new InMemoryEventBus();
        }
    }

    @Test
    @DisplayName("The Summarise stage is listening, so captured content is summarised onto the bus")
    void theSummariseStageIsListening() {
        // Asserting on behaviour rather than on a bean existing, because the stage is no longer a
        // bean: PipelineConfiguration builds and starts it once the context is ready. "A stage
        // bean exists" would also have passed while nothing had subscribed, which is the actual
        // failure — captured content reaching a bus nobody is listening to and simply stopping.
        when(prompts.current("Sprint Refinement"))
                .thenReturn(Prompt.builder()
                        .contentType("Sprint Refinement")
                        .version(1)
                        .instructions("Summarise.")
                        .build());
        when(mockedModel.provider()).thenReturn(ModelProvider.ANTHROPIC);
        when(mockedModel.chat(any(List.class)))
                .thenReturn(ChatResponse.builder()
                        .aiMessage(AiMessage.from("Decided."))
                        .modelName("claude-sonnet-5")
                        .build());

        var summarised = new ArrayList<ContentSummarised>();
        bus.subscribe(ContentSummarised.class, summarised::add);
        bus.publish(ContentCaptured.builder()
                .chainId(UUID.randomUUID())
                .content(CapturedContent.builder()
                        .sourceUri("https://drive.google.com/file/refinement")
                        .title("Sprint Refinement 2026-10-07")
                        .contentType("Sprint Refinement")
                        .transcript("#142 is done.")
                        .build())
                .build());

        assertThat(summarised).hasSize(1);
        assertThat(summarised.getFirst().summary().text()).isEqualTo("Decided.");
    }
}
