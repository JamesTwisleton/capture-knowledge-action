package com.cka.core;

import lombok.Builder;
import lombok.NonNull;

/**
 * Something the Action stage proposes, applies or fails to apply to a work item.
 *
 * <p>A {@link ActionType#COMMENT} proposed in triage is a <em>proposed comment</em>; any
 * other type proposed there is a <em>proposed action</em>. They share this shape, and triage
 * tells them apart by type (PRD 9.1).
 *
 * @param type       what the action does
 * @param workItemId the item it acts on
 * @param excerpt    the passage it came from, shown in triage and posted with comments
 * @param confidence the decision provider's confidence, from 0 to 1
 * @param decidedBy  the decision provider and model that produced it
 */
@Builder
public record Action(
        @NonNull ActionType type,
        @NonNull String workItemId,
        @NonNull String excerpt,
        double confidence,
        @NonNull Provenance decidedBy) {}
