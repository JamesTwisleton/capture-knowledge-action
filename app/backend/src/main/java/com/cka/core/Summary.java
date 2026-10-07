package com.cka.core;

/**
 * The LLM provider's summary of some captured content.
 *
 * @param text       the summary, in the shape its content type's prompt asked for
 * @param producedBy the LLM provider and model that wrote it
 */
public record Summary(String text, Provenance producedBy) {
}
