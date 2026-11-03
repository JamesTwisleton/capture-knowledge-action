package com.cka.core;

import java.util.Set;

/**
 * The steps of the action decision chain (PRD 7.8) that an {@link AuditEntry} can record.
 * One per pipeline event, plus the three that have no event because nothing downstream acts on
 * them — an unmatched mention, and a human's accept or reject in triage, which is the final
 * approval and not a request to anyone else (PRD 9.1).
 */
public enum AuditStep {
    CONTENT_CAPTURED,
    CONTENT_SUMMARISED,
    KNOWLEDGE_STORED,
    MENTION_UNMATCHED,
    ACTION_PROPOSED,
    ACTION_ACCEPTED,
    ACTION_REJECTED,
    ACTION_APPLIED,
    ACTION_FAILED;

    /**
     * Static, not an instance member: initialised after the constants, which is what lets it
     * name them.
     */
    private static final Set<AuditStep> HUMAN_DECISIONS = Set.of(ACTION_ACCEPTED, ACTION_REJECTED);

    /**
     * The steps a human decides, and so the ones the trust ramp counts (PRD 9.2) and the front
     * end shows as accept/reject history. An action CKA applied by itself is {@link
     * #ACTION_APPLIED} and is deliberately not one of these: it is not evidence of what a person
     * thinks of that action type.
     */
    public static Set<AuditStep> humanDecisions() {
        return HUMAN_DECISIONS;
    }
}
