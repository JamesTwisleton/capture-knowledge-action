package com.cka.provider.llm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.cka.core.CapturedContent;
import com.cka.core.Prompt;
import com.cka.core.event.ContentCaptured;
import com.cka.core.event.ContentSummarised;
import com.cka.core.event.PipelineEvent;
import com.cka.pipeline.SummariseStage;
import com.cka.provider.knowledge.KnowledgeProvider;
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

/**
 * T12's acceptance criterion, as a test: a content-captured event in, a content-summarised event
 * out, carrying a summary written to the configured prompt's shape.
 *
 * <p>The second half of that criterion is the interesting half — "verifiable with no knowledge
 * provider running at all; if the test needs one, the decoupling isn't real". So there is no
 * {@link KnowledgeProvider} here, mocked or otherwise: it is imported only to be named in this
 * sentence, and the test would not compile differently without it. The real provider runs over a
 * mocked {@link ChatModel}, which is the only thing standing in.
 *
 * <p>Distinct from {@code PipelineWiringTest}, which proves T05 — the whole chain with a stand-in
 * for every interface. This proves one ticket's own promise with its real implementation in place.
 */
@DisplayName("A captured content event is summarised onto the bus, with no knowledge provider present")
class SummariseAcrossTheBusTest {

    private final ChatModel chat = mock(ChatModel.class);
    private final PromptStore prompts = mock(PromptStore.class);
    private final InMemoryEventBus bus = new InMemoryEventBus();

    @Test
    @DisplayName("Publishing content captured publishes content summarised, in the prompt's shape")
    void publishingContentCapturedPublishesContentSummarised() {
        var published = new ArrayList<PipelineEvent>();
        bus.subscribe(PipelineEvent.class, published::add);

        when(prompts.current("Sprint Refinement"))
                .thenReturn(Prompt.builder()
                        .contentType("Sprint Refinement")
                        .version(3)
                        .instructions("List the items discussed and what was decided.")
                        .build());
        when(chat.provider()).thenReturn(ModelProvider.ANTHROPIC);
        when(chat.chat(any(List.class)))
                .thenReturn(ChatResponse.builder()
                        .aiMessage(AiMessage.from("- #142 — agreed done, to be closed."))
                        .modelName("claude-sonnet-5")
                        .build());

        new SummariseStage(new LangChain4jLlmProvider(chat, prompts), bus).start();

        var chainId = UUID.randomUUID();
        var content = CapturedContent.builder()
                .sourceUri("https://drive.google.com/file/refinement")
                .title("Sprint Refinement 2026-10-07")
                .contentType("Sprint Refinement")
                .transcript("Priya: #142 is merged and verified, so it can be closed.")
                .platformSummary("The team agreed #142 is done.")
                .build();
        bus.publish(ContentCaptured.builder().chainId(chainId).content(content).build());

        assertThat(published).hasSize(2);
        assertThat(published.get(1)).isInstanceOf(ContentSummarised.class);
        var summarised = (ContentSummarised) published.get(1);
        // The same chain id throughout, because T14's audit chain is assembled by it: a new id
        // here and the summary could never be traced back to the recording it came from.
        assertThat(summarised.chainId()).isEqualTo(chainId);
        assertThat(summarised.content()).isEqualTo(content);
        assertThat(summarised.summary().text()).isEqualTo("- #142 — agreed done, to be closed.");
        assertThat(summarised.summary().promptVersion()).isEqualTo(3);
        assertThat(summarised.summary().producedBy().provider()).isEqualTo("anthropic");
        assertThat(summarised.summary().producedBy().model()).isEqualTo("claude-sonnet-5");
    }

    @Test
    @DisplayName("An item with no prompt of its own is summarised with whatever the store falls back to")
    void anItemWithNoPromptOfItsOwnIsStillSummarised() {
        // The fallback itself is T11's, and SqlPromptStoreTest proves it. What matters here is
        // that this provider asks for the content's own type and uses what comes back, so an
        // untyped item still produces an event rather than failing the stage.
        when(prompts.current(anyString()))
                .thenReturn(Prompt.builder()
                        .contentType(Prompt.FALLBACK_CONTENT_TYPE)
                        .version(1)
                        .instructions("Summarise this content.")
                        .build());
        when(chat.provider()).thenReturn(ModelProvider.ANTHROPIC);
        when(chat.chat(any(List.class)))
                .thenReturn(ChatResponse.builder()
                        .aiMessage(AiMessage.from("A summary."))
                        .modelName("claude-sonnet-5")
                        .build());

        var published = new ArrayList<ContentSummarised>();
        bus.subscribe(ContentSummarised.class, published::add);
        new SummariseStage(new LangChain4jLlmProvider(chat, prompts), bus).start();

        bus.publish(ContentCaptured.builder()
                .chainId(UUID.randomUUID())
                .content(CapturedContent.builder()
                        .sourceUri("https://example.test/notes")
                        .title("Some notes")
                        .contentType("One-to-one")
                        .transcript("Whatever was said.")
                        .build())
                .build());

        assertThat(published).hasSize(1);
        assertThat(published.getFirst().summary().promptVersion()).isEqualTo(1);
    }
}
