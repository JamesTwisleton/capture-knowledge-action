package com.cka.core;

import java.util.Set;

/**
 * The steps of the action decision chain (PRD 7.8) that an {@link AuditEntry} can record.
 * One per pipeline event, plus the three that have no event because no <em>stage</em> is waiting
 * on them — an unmatched mention, and a human's accept or reject in triage, which is the final
 * approval and not a request to anyone else (PRD 9.1).
 *
 * <p>Not the whole chain as the PRD now describes it. An unmatched mention can be <em>linked</em>
 * to the work item it meant or <em>dismissed</em> as nothing, and a human changing an action
 * threshold is itself an audited decision — three steps this enum does not have, because T23
 * builds them and the constants arrive with the code and the tests that write them. Until then
 * `MENTION_UNMATCHED` is the end of that branch here, which is a statement about what is built
 * rather than about what the chain is (PRD 9.2).
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
     * The steps a human decides <em>about an action</em>, and so the ones the trust ramp counts
     * (PRD 9.2) and the front end shows as accept/reject history. An action CKA applied by itself
     * is {@link #ACTION_APPLIED} and is deliberately not one of these: it is not evidence of what
     * a person thinks of that action type.
     *
     * <p><strong>Do not widen this set with mention decisions.</strong> PRD 9.2 now describes two
     * kinds of learning from two different sets of decisions: the trust score, which decides
     * <em>who approves</em> an action type and reads exactly these steps, and calibration, which
     * decides <em>where a threshold sits</em> and reads a human's verdict on mention detection.
     * They are both "human decisions", which is the trap — adding a linked unmatched mention here
     * would move a trust score on evidence about something else entirely, and nothing would fail.
     * T23 gets its own set.
     */
    public static Set<AuditStep> humanDecisions() {
        return HUMAN_DECISIONS;
    }
}
