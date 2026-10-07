package com.cka.core.event;

import com.cka.core.Action;

import java.util.UUID;

/**
 * An action the work item provider could not carry out. Terminal in the pipeline (PRD 7.1).
 *
 * @param error what went wrong, in words a person can act on
 */
public record ActionFailed(UUID chainId, Action action, String error) implements PipelineEvent {
}
