package com.cka.core.event;

import com.cka.core.CapturedContent;

import java.util.UUID;

/** Published by the capture provider's content listener; consumed by the LLM provider. */
public record ContentCaptured(UUID chainId, CapturedContent content) implements PipelineEvent {
}
