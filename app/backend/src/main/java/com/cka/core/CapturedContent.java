package com.cka.core;

import lombok.Builder;
import lombok.NonNull;

/**
 * What a capture provider found: the normalised output of the Capture stage, whatever
 * fed it (PRD 7.1).
 *
 * @param sourceUri       where the content came from, for example the recording's Drive URL
 * @param title           the source's own title, for example the meeting name
 * @param contentType     the content type that selects the summary prompt (PRD 7.6),
 *                        for example "Sprint Refinement"
 * @param transcript      the text to summarise
 * @param platformSummary the platform's own summary, or {@code null} if it supplied none;
 *                        Meet supplies one, and the LLM step uses both together (PRD 8.2)
 */
@Builder
public record CapturedContent(
        @NonNull String sourceUri,
        @NonNull String title,
        @NonNull String contentType,
        @NonNull String transcript,
        String platformSummary) {

    /**
     * The content type of an item whose type could not be worked out — a meeting nobody named,
     * for instance. Deliberately not {@code Prompt.FALLBACK_CONTENT_TYPE}: "General" says a
     * human chose the general prompt, this says nothing was known, and a person reading the
     * activity view or a vault note should be able to tell those apart. It has no prompt of its
     * own, so {@code PromptStore.current} serves the General one for it anyway.
     *
     * <p>Here rather than on whichever provider happens to assign it, because both ends need it:
     * a capture provider decides a type is unknown and the knowledge provider renders it
     * differently, and neither should have to depend on the other to agree on the word.
     */
    public static final String UNKNOWN_CONTENT_TYPE = "Unknown";
}
