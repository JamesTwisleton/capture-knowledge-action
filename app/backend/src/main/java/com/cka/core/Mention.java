package com.cka.core;

import lombok.Builder;
import lombok.NonNull;

/**
 * One reference to a work item that the decision provider found in some knowledge.
 *
 * <p>A mention either matches a real candidate in the pool, with a confidence, or matches
 * nothing at all — an <em>unmatched mention</em>, which is recorded rather than dropped and
 * never becomes an action (PRD 9.1).
 *
 * @param excerpt    the passage the mention was found in, shown in triage
 * @param workItemId the matched candidate's id, or {@code null} if it matched no candidate
 * @param confidence the decision provider's confidence in the match, from 0 to 1;
 *                   meaningless for an unmatched mention
 * @param scoredBy   the decision provider and model that scored it
 */
@Builder
public record Mention(
        @NonNull String excerpt,
        String workItemId,
        double confidence,
        @NonNull Provenance scoredBy) {

    public boolean matched() {
        return workItemId != null;
    }
}
