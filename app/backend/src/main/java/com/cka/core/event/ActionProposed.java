package com.cka.core.event;

import com.cka.core.Action;

import java.util.UUID;

/** An action awaiting a human's accept or reject in triage. Terminal in the pipeline (PRD 7.1). */
public record ActionProposed(UUID chainId, Action action) implements PipelineEvent {
}
