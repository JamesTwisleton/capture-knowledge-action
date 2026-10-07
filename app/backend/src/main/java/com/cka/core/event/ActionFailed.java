package com.cka.core.event;

import com.cka.core.Action;
import java.util.UUID;
import lombok.Builder;
import lombok.NonNull;

/**
 * An action the work item provider could not carry out. Terminal in the pipeline (PRD 7.1).
 *
 * @param error what went wrong, in words a person can act on
 */
@Builder
public record ActionFailed(
        @NonNull UUID chainId,
        @NonNull Action action,
        @NonNull String error) implements PipelineEvent {}
