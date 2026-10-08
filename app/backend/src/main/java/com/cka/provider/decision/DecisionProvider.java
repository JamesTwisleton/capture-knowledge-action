package com.cka.provider.decision;

import com.cka.core.Knowledge;
import com.cka.core.Mention;
import com.cka.core.WorkItem;
import com.cka.provider.Provider;
import java.util.List;

/**
 * Calibrated classification and scoring (PRD 8.1): Jev, or an LLM-backed fallback in the
 * same shape.
 */
public interface DecisionProvider extends Provider {

    /**
     * Finds the work items mentioned in some knowledge, scoring each against the real
     * candidate pool. There is no separate id-extraction step in front of this (PRD 8.2), so
     * a mention can only match an item that is actually in the pool; a reference that matches
     * none comes back as an unmatched {@link Mention}, never dropped.
     */
    List<Mention> detectMentions(Knowledge knowledge, List<WorkItem> candidatePool);
}
