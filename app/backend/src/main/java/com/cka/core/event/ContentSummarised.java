package com.cka.core.event;

import com.cka.core.CapturedContent;
import com.cka.core.Summary;

import java.util.UUID;

/**
 * Published by the LLM provider once it has summarised; consumed by the knowledge provider.
 * Carries the content forward as well as the summary, so the knowledge provider can store
 * the transcript without reaching back to whoever captured it.
 */
public record ContentSummarised(UUID chainId, CapturedContent content, Summary summary) implements PipelineEvent {
}
