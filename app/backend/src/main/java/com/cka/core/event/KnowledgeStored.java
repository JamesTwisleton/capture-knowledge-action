package com.cka.core.event;

import com.cka.core.Knowledge;
import java.util.UUID;
import lombok.Builder;
import lombok.NonNull;

/** Published by the knowledge provider once the write completes; consumed by the Action stage. */
@Builder
public record KnowledgeStored(
        @NonNull UUID chainId, @NonNull Knowledge knowledge) implements PipelineEvent {}
