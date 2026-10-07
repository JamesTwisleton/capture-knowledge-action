package com.cka.core;

/**
 * The steps of the action decision chain (PRD 7.8) that an {@link AuditEntry} can record.
 * One per pipeline event, plus the unmatched mention, which has no event because nothing
 * downstream acts on it — it is recorded and surfaced instead.
 */
public enum AuditStep {
    CONTENT_CAPTURED,
    CONTENT_SUMMARISED,
    KNOWLEDGE_STORED,
    MENTION_UNMATCHED,
    ACTION_PROPOSED,
    ACTION_APPLIED,
    ACTION_FAILED
}
