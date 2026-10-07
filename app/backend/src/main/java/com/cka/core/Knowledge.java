package com.cka.core;

import lombok.Builder;
import lombok.NonNull;

/**
 * Stored, linkable knowledge: the normalised output of the Knowledge stage (PRD 7.1), and
 * what mention detection reads.
 *
 * @param page    where it was stored
 * @param content what was captured, including the transcript
 * @param summary what the LLM provider made of it
 */
@Builder
public record Knowledge(
        @NonNull PageReference page,
        @NonNull CapturedContent content,
        @NonNull Summary summary) {}
