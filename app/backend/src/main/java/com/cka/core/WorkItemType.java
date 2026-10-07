package com.cka.core;

/**
 * The generic kind of a work item, which the core can reason about. A provider's own label
 * ("Epic", "Sub-issue", "Milestone") is kept separately as
 * {@link WorkItem#providerTypeName()} so that mapping to this never loses it (PRD 7.9).
 *
 * <p>When a provider's concept fits none of these comfortably, map it to {@link #OTHER} and
 * let the provider type name carry the meaning, rather than forcing a false equivalence.
 */
public enum WorkItemType {
    EPIC,
    STORY,
    TASK,
    BUG,
    OTHER
}
