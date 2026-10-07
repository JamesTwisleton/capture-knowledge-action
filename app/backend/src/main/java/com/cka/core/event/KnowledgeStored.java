package com.cka.core.event;

import com.cka.core.Knowledge;

import java.util.UUID;

/** Published by the knowledge provider once the write completes; consumed by the Action stage. */
public record KnowledgeStored(UUID chainId, Knowledge knowledge) implements PipelineEvent {
}
