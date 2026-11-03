package com.cka.core;

import lombok.Builder;
import lombok.NonNull;

/**
 * The LLM provider's summary of some captured content.
 *
 * <p>Required, not optional: PRD 7.6 asks that "the version used is recorded against each
 * output", and {@link Prompt} guarantees there is always one to record — a content type with no
 * prompt of its own is summarised with the fallback. A summary that cannot say which prompt
 * produced it leaves a hole in the audit chain that nothing can fill in afterwards.
 *
 * @param text          the summary, in the shape its content type's prompt asked for
 * @param producedBy    the LLM provider and model that wrote it
 * @param promptVersion the version of that content type's prompt it was written with
 */
@Builder
public record Summary(
        @NonNull String text,
        @NonNull Provenance producedBy,
        @NonNull Integer promptVersion) {}
