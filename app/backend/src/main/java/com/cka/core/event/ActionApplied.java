package com.cka.core.event;

import com.cka.core.Action;

import java.util.UUID;

/** An action the work item provider has carried out. Terminal in the pipeline (PRD 7.1). */
public record ActionApplied(UUID chainId, Action action) implements PipelineEvent {
}
