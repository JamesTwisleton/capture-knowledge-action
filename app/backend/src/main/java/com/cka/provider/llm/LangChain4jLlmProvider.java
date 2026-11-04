package com.cka.provider.llm;

import com.cka.core.CapturedContent;
import com.cka.core.Provenance;
import com.cka.core.Summary;
import com.cka.provider.ProviderHealth;
import com.cka.provider.prompt.PromptStore;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import java.util.List;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * The LLM provider on LangChain4j (T12): captured content in, a {@link Summary} out, written to
 * the shape its content type's prompt asks for.
 *
 * <p>It holds a {@link ChatModel}, not an Anthropic client. That interface is LangChain4j's
 * vendor-neutral one, so Claude, Gemini, OpenAI and a local Ollama model are all the same type
 * here and swapping between them is a dependency and a bean — never a change to this class
 * (PRD 7.2). It is also what keeps every LLM call inside one gateway, which is the claim
 * Section 3 makes and the thing J7's cost tracking counts.
 *
 * <p>Summarising is the whole job. No work item ids are extracted — that is the decision
 * provider's, against the real candidate pool — and nothing is written to the knowledge
 * provider: {@code SummariseStage} publishes the result and T06 picks it up off the bus.
 */
@Component
@ConditionalOnProperty(prefix = "cka.provider", name = "llm", havingValue = "langchain4j")
@RequiredArgsConstructor
public class LangChain4jLlmProvider implements LlmProvider {

    /**
     * What a model is called when it does not say. {@code ChatResponse.modelName()} is optional
     * in LangChain4j and some bindings leave it unset; {@code Provenance.model} is
     * {@code @NonNull}, so the alternative is an exception from deep inside a summary that
     * otherwise succeeded. A recognisable placeholder in the audit trail beats losing the
     * entry — and reads as the gap it is rather than as a model called "null".
     */
    static final String UNREPORTED_MODEL = "unreported";

    private final ChatModel chat;
    private final PromptStore prompts;

    /**
     * The prompt is asked for by the content's own type, and {@link PromptStore#current} applies
     * the fallback if that type has none of its own — the decision belongs there, where the
     * versions are, not duplicated here.
     */
    @Override
    public Summary summarise(CapturedContent content) {
        var prompt = prompts.current(content.contentType());
        var response =
                chat.chat(List.of(SystemMessage.from(prompt.instructions()), UserMessage.from(asOneDocument(content))));

        return Summary.builder()
                .text(response.aiMessage().text())
                .producedBy(attribution(response.modelName()))
                .promptVersion(prompt.version())
                .build();
    }

    /**
     * A real call, for the same reason the SQL layer's check runs {@code SELECT 1} rather than
     * opening a connection: the failure this exists to catch is a key that is present and
     * revoked, which looks perfectly healthy until something uses it. The shortest prompt there
     * is, because the front end polls this.
     */
    @Override
    public ProviderHealth checkHealth() {
        try {
            chat.chat("ping");
            return ProviderHealth.up("reached " + vendor());
        } catch (Exception e) {
            return ProviderHealth.down("cannot reach " + vendor() + ": " + e.getMessage());
        }
    }

    /**
     * Both halves of what was captured, labelled, in one message. The transcript and the
     * platform's own summary go in together because PRD 8.2 says the step uses both — Meet
     * supplies a summary and ignoring it would throw away work already done — and they are
     * labelled because the model is being asked to tell them apart.
     *
     * <p>{@code platformSummary} is the one nullable field on {@link CapturedContent}: a capture
     * provider that supplies none must not put the word "null" in front of the model.
     */
    private String asOneDocument(CapturedContent content) {
        var document = new StringBuilder("# ").append(content.title()).append("\n\n");
        if (content.platformSummary() != null) {
            document.append("## The platform's own summary\n\n")
                    .append(content.platformSummary())
                    .append("\n\n");
        }
        return document.append("## Transcript\n\n").append(content.transcript()).toString();
    }

    /**
     * Named for the vendor that answered, never for LangChain4j. "langchain4j" would be true of
     * every LLM call CKA ever makes and so distinguishes nothing, and the audit trail exists to
     * say which provider and model decided (PRD 9.3) — a claim only worth recording if it names
     * Anthropic or Ollama or whoever it actually was.
     */
    private Provenance attribution(String modelName) {
        return Provenance.builder()
                .provider(vendor())
                .model(modelName == null ? UNREPORTED_MODEL : modelName)
                .build();
    }

    private String vendor() {
        return chat.provider().name().toLowerCase(Locale.ROOT);
    }
}
