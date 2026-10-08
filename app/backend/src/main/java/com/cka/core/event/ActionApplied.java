package com.cka.core.event;

import com.cka.core.Action;
import java.util.UUID;
import lombok.Builder;
import lombok.NonNull;

/** An action the work item provider has carried out. Terminal in the pipeline (PRD 7.1). */
@Builder
public record ActionApplied(@NonNull UUID chainId, @NonNull Action action) implements PipelineEvent {}
