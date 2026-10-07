package com.cka.provider.workitem;

import com.cka.core.WorkItem;

/**
 * Converts a work item provider's native concept to the core's generic {@link WorkItem} and
 * back (PRD 7.9). A named extension point: every work item provider ships one, and adding a
 * provider means writing its mapper.
 *
 * @param <N> the provider's native type, for example a GitHub issue
 */
public interface WorkItemMapper<N> {

    WorkItem toWorkItem(N nativeItem);

    N toNative(WorkItem workItem);
}
