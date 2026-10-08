package com.cka.provider.workitem;

import com.cka.core.WorkItem;
import com.cka.provider.Provider;
import java.util.List;

/**
 * Queries and acts on work items in a tracker (PRD 7.2). Each operation is its own method so
 * that a team can override how one is done without replacing the provider (PRD 10).
 */
public interface WorkItemProvider extends Provider {

    /** The work items mention detection classifies against (PRD 7.5). */
    List<WorkItem> candidatePool();

    void comment(String workItemId, String text);

    void close(String workItemId);
}
