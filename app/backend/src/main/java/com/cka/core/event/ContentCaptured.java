package com.cka.core.event;

import com.cka.core.CapturedContent;
import java.util.UUID;
import lombok.Builder;
import lombok.NonNull;

/** Published by the capture provider's content listener; consumed by the LLM provider. */
@Builder
public record ContentCaptured(
        @NonNull UUID chainId, @NonNull CapturedContent content) implements PipelineEvent {}
