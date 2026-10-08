package com.cka.core;

import lombok.Builder;
import lombok.NonNull;

/**
 * The LLM provider's summary of some captured content.
 *
 * @param text       the summary, in the shape its content type's prompt asked for
 * @param producedBy the LLM provider and model that wrote it
 */
@Builder
public record Summary(@NonNull String text, @NonNull Provenance producedBy) {}
