package com.cka.core;

import lombok.Builder;
import lombok.NonNull;

/**
 * One version of the summary prompt for a content type: the instructions given to the LLM when
 * it summarises content of that type (PRD 7.6).
 *
 * <p>Prompts are data, not configuration — "first-class data in SQL", in the PRD's words — and
 * they are append-only. Editing one does not change this record; it writes the next version
 * beside it, so the audit chain's {@code promptVersion} always names something still readable.
 *
 * @param contentType  the type this prompt summarises, for example "Sprint Refinement"
 * @param version      which version it is, counting from 1 within the content type
 * @param instructions the prompt itself, plain text and editable in the UI
 */
@Builder
public record Prompt(
        @NonNull String contentType, int version, @NonNull String instructions) {

    /**
     * The content type whose prompt is used when an item has not been given a more specific one
     * (PRD 7.6: "the fallback prompt for any item that has not been given a more specific
     * type"). A content type in its own right, not a magic value — it is editable and versioned
     * like any other.
     */
    public static final String FALLBACK_CONTENT_TYPE = "General";
}
