package com.cka.core;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import lombok.Builder;
import lombok.NonNull;

/**
 * One step in an action decision chain, carrying everything PRD 7.8 asks of an audit record:
 * what happened, when, on which chain, by which provider and model with what confidence, using
 * which prompt version, and decided by whom.
 *
 * <p>Four of those apply only to some steps and are {@code null} on the rest — capturing a
 * recording has no action type and nothing to be confident about. Nothing is inferred from a
 * missing one, which is why {@link #confidence()} is a boxed {@code Double}: a primitive would
 * make "nobody scored this" indistinguishable from a real score of zero.
 *
 * <p>Everything structured here is something a consumer <em>queries</em> — a chain, a trust
 * window, an action type. Anything else about the step belongs in {@link #detail()}, which is
 * what the activity view and triage show a person.
 *
 * @param chainId       the action decision chain this step belongs to
 * @param step          which step it records
 * @param recordedAt    when it happened, to the millisecond (see the constructor)
 * @param detail        a human-readable description of what happened
 * @param actionType    the action this step concerns, or {@code null} for the steps before any
 *                      action exists; trust is per action type (PRD 9.2), so it is a column
 * @param producedBy    the provider and model behind the step, or {@code null} where none was
 *                      involved — capture and knowledge storage decide nothing
 * @param confidence    the provider's confidence, from 0 to 1, or {@code null} if it scored
 *                      nothing
 * @param decider       who decided: the system, or the human who accepted or rejected in triage
 * @param promptVersion the version of the summary prompt behind this chain, or {@code null} on
 *                      every step but {@code CONTENT_SUMMARISED} — the one place it is a fact
 *                      rather than a copy. The chain relates it to the actions that followed
 */
@Builder(toBuilder = true)
public record AuditEntry(
        @NonNull UUID chainId,
        @NonNull AuditStep step,
        @NonNull Instant recordedAt,
        @NonNull String detail,
        ActionType actionType,
        Provenance producedBy,
        Double confidence,
        @NonNull Decider decider,
        Integer promptVersion) {

    /**
     * Milliseconds are the audit chain's resolution: the unit it is stored and queried at, and
     * the unit a chain id's own timestamp is in ({@link ChainId}). Truncating here rather than at
     * the storage boundary keeps a recorded entry equal to the entry that was handed over —
     * {@code Instant.now()} is finer than a millisecond on this JDK, so otherwise every caller
     * would have to remember to do this itself.
     */
    public AuditEntry {
        recordedAt = recordedAt.truncatedTo(ChronoUnit.MILLIS);
    }
}
