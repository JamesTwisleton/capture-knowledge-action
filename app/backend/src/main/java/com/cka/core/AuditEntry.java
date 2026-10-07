package com.cka.core;

import java.util.UUID;

/**
 * One step in an action decision chain. The minimum the pipeline needs to trace a chain end
 * to end; T14 widens it with provider, model, confidence, decider and prompt version.
 *
 * @param chainId the action decision chain this step belongs to
 * @param step    which step it records
 * @param detail  a human-readable description of what happened
 */
public record AuditEntry(UUID chainId, AuditStep step, String detail) {
}
