package com.cka.core.event;

import com.cka.core.Action;
import java.util.UUID;
import lombok.Builder;
import lombok.NonNull;

/** An action awaiting a human's accept or reject in triage. Terminal in the pipeline (PRD 7.1). */
@Builder
public record ActionProposed(@NonNull UUID chainId, @NonNull Action action) implements PipelineEvent {}
