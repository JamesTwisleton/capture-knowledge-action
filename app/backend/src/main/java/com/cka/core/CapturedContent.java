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
        String platformSummary) {}
