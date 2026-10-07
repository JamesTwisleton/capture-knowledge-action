package com.cka.core;

import java.util.UUID;
import lombok.Builder;
import lombok.NonNull;

/**
 * One step in an action decision chain. The minimum the pipeline needs to trace a chain end
 * to end; T14 widens it with provider, model, confidence, decider and prompt version.
 *
 * @param chainId the action decision chain this step belongs to
 * @param step    which step it records
 * @param detail  a human-readable description of what happened
 */
@Builder
public record AuditEntry(
        @NonNull UUID chainId,
        @NonNull AuditStep step,
        @NonNull String detail) {}
