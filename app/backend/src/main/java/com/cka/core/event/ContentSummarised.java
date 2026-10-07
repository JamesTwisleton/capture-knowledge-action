package com.cka.core.event;

import com.cka.core.CapturedContent;
import com.cka.core.Summary;
import java.util.UUID;
import lombok.Builder;
import lombok.NonNull;

/**
 * Published by the LLM provider once it has summarised; consumed by the knowledge provider.
 * Carries the content forward as well as the summary, so the knowledge provider can store
 * the transcript without reaching back to whoever captured it.
 */
@Builder
public record ContentSummarised(
        @NonNull UUID chainId,
        @NonNull CapturedContent content,
        @NonNull Summary summary) implements PipelineEvent {}
