package com.cka.provider.llm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.cka.core.CapturedContent;
import com.cka.core.Prompt;
import com.cka.provider.prompt.PromptStore;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.ModelProvider;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.response.ChatResponse;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * T12 against a mocked {@link ChatModel}. The model is the one collaborator worth faking: what
 * this class actually does is choose the prompt, put the content in front of it, and attribute
 * the result — and a real model call would make none of that more certain while costing money
 * and a network on every run.
 *
 * <p>Nothing here needs a knowledge provider, which is T12's acceptance criterion in miniature:
 * "if the test needs one, the decoupling isn't real".
 */
@DisplayName("The LLM provider summarises with the content type's prompt and attributes the result")
class LangChain4jLlmProviderTest {

    private static final CapturedContent REFINEMENT = CapturedContent.builder()
            .sourceUri("https://drive.google.com/file/refinement")
            .title("Sprint Refinement 2026-10-07")
            .contentType("Sprint Refinement")
            .transcript("We agreed #142 is done and can be closed.")
            .platformSummary("The team discussed #142.")
            .build();

    private final ChatModel chat = mock(ChatModel.class);
    private final PromptStore prompts = mock(PromptStore.class);
    private final LangChain4jLlmProvider provider = new LangChain4jLlmProvider(chat, prompts);

    private void given(Prompt prompt, String reply) {
        when(prompts.current(anyString())).thenReturn(prompt);
        when(chat.provider()).thenReturn(ModelProvider.ANTHROPIC);
        when(chat.chat(anyList()))
                .thenReturn(ChatResponse.builder()
                        .aiMessage(AiMessage.from(reply))
                        .modelName("claude-sonnet-5")
                        .build());
    }

    private static Prompt prompt(String contentType, int version, String instructions) {
        return Prompt.builder()
                .contentType(contentType)
                .version(version)
                .instructions(instructions)
                .build();
    }

    /**
     * {@code ArgumentCaptor.captor()}, not {@code forClass(List.class)}. A class literal cannot
     * carry an element type — {@code List.class} is a {@code Class<List>} — so the captor comes
     * back raw and both the capture and the read are unchecked. {@code captor()} takes the type
     * from the variable it is assigned to, which is how the element type survives.
     */
    private List<ChatMessage> sentMessages() {
        ArgumentCaptor<List<ChatMessage>> captor = ArgumentCaptor.captor();
        verify(chat).chat(captor.capture());
        return captor.getValue();
    }

    @Test
    @DisplayName("The content type's prompt becomes the system message, and the content the user message")
    void theContentTypesPromptBecomesTheSystemMessage() {
        given(prompt("Sprint Refinement", 3, "Summarise the refinement."), "Decided: close #142.");

        provider.summarise(REFINEMENT);

        var messages = sentMessages();
        assertThat(messages).hasSize(2);
        assertThat(messages.get(0)).isInstanceOf(SystemMessage.class);
        assertThat(((SystemMessage) messages.get(0)).text()).isEqualTo("Summarise the refinement.");
        assertThat(messages.get(1)).isInstanceOf(UserMessage.class);
    }

    @Test
    @DisplayName("The prompt is asked for by the content's own type, so a type with none gets the fallback")
    void thePromptIsAskedForByTheContentsOwnType() {
        // The fallback is PromptStore's to apply, not this class's — asking for the right type is
        // the whole of this provider's responsibility, and asking for the wrong one is a bug a
        // test on the returned text would never catch.
        given(prompt("Sprint Refinement", 1, "Summarise."), "Decided.");

        provider.summarise(REFINEMENT);

        verify(prompts).current("Sprint Refinement");
    }

    @Test
    @DisplayName("The transcript and the platform's own summary are both put in front of the model")
    void bothTheTranscriptAndThePlatformSummaryAreSent() {
        // PRD 8.2: Meet supplies a summary of its own and the LLM step uses both together.
        // Sending only the transcript would quietly throw away what the platform already did.
        given(prompt("Sprint Refinement", 1, "Summarise."), "Decided.");

        provider.summarise(REFINEMENT);

        var sent = ((UserMessage) sentMessages().get(1)).singleText();
        assertThat(sent).contains(REFINEMENT.transcript()).contains(REFINEMENT.platformSummary());
    }

    @Test
    @DisplayName("Content with no platform summary is summarised from the transcript alone")
    void contentWithNoPlatformSummaryIsStillSummarised() {
        // platformSummary is the one nullable field on CapturedContent — a capture provider that
        // supplies none must not produce the literal text "null" in the prompt.
        given(prompt("General", 1, "Summarise."), "Decided.");
        var noSummary = CapturedContent.builder()
                .sourceUri("https://example.test/doc")
                .title("A document")
                .contentType("General")
                .transcript("The only text there is.")
                .build();

        provider.summarise(noSummary);

        var sent = ((UserMessage) sentMessages().get(1)).singleText();
        assertThat(sent).contains("The only text there is.").doesNotContain("null");
    }

    @Test
    @DisplayName("The summary records the prompt version it was written with")
    void theSummaryRecordsThePromptVersion() {
        // PRD 7.6 asks that the version used is recorded against each output, and T14's chain
        // carries it on the summarised step. This is the only place that knows it.
        given(prompt("Sprint Refinement", 4, "Summarise."), "Decided: close #142.");

        var summary = provider.summarise(REFINEMENT);

        assertThat(summary.promptVersion()).isEqualTo(4);
        assertThat(summary.text()).isEqualTo("Decided: close #142.");
    }

    @Test
    @DisplayName("The summary is attributed to the vendor behind the gateway, not to the gateway")
    void theSummaryIsAttributedToTheVendorNotTheGateway() {
        // "langchain4j" would be true of every LLM call CKA ever makes and so says nothing. The
        // audit trail and cost tracking attribute by provider and model (PRD 9.3), which only
        // means something if it names Anthropic or Ollama or whoever actually answered.
        given(prompt("Sprint Refinement", 1, "Summarise."), "Decided.");

        var summary = provider.summarise(REFINEMENT);

        assertThat(summary.producedBy().provider()).isEqualTo("anthropic");
        assertThat(summary.producedBy().model()).isEqualTo("claude-sonnet-5");
    }

    @Test
    @DisplayName("A model that reports no name of its own is attributed to the configured one")
    void aModelThatReportsNoNameIsAttributedToTheConfiguredOne() {
        // ChatResponse.modelName() is optional in LangChain4j and some providers leave it unset.
        // Provenance.model is @NonNull, so an unset name would fail the build of the Summary
        // rather than lose the attribution quietly.
        when(prompts.current(anyString())).thenReturn(prompt("Sprint Refinement", 1, "Summarise."));
        when(chat.provider()).thenReturn(ModelProvider.OLLAMA);
        when(chat.chat(anyList()))
                .thenReturn(ChatResponse.builder()
                        .aiMessage(AiMessage.from("Decided."))
                        .build());

        var summary = provider.summarise(REFINEMENT);

        assertThat(summary.producedBy().provider()).isEqualTo("ollama");
        assertThat(summary.producedBy().model()).isEqualTo("unreported");
    }

    @Test
    @DisplayName("The health check reports up when the model answers")
    void theHealthCheckReportsUpWhenTheModelAnswers() {
        when(chat.provider()).thenReturn(ModelProvider.ANTHROPIC);
        when(chat.chat(anyString())).thenReturn("ok");

        var health = provider.checkHealth();

        assertThat(health.up()).isTrue();
        assertThat(health.detail()).contains("anthropic");
    }

    @Test
    @DisplayName("The health check reports down, and says why, when the model cannot be reached")
    void theHealthCheckReportsDownWhenTheModelCannotBeReached() {
        // Must not throw — Provider.checkHealth reports. A revoked API key is the case this
        // exists for, and it surfaces as an exception from the client, not a null answer.
        when(chat.provider()).thenReturn(ModelProvider.ANTHROPIC);
        when(chat.chat(anyString())).thenThrow(new RuntimeException("401 authentication_error"));

        var health = provider.checkHealth();

        assertThat(health.up()).isFalse();
        assertThat(health.detail()).contains("401 authentication_error");
    }
}
