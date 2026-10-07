package com.cka.testsupport;

import com.cka.core.WorkItem;
import com.cka.core.WorkItemType;
import com.cka.provider.workitem.WorkItemMapper;

/** Maps {@link Ticket}s the way a real provider's mapper would: by the tracker's own labels. */
public class TicketMapper implements WorkItemMapper<Ticket> {

    @Override
    public WorkItem toWorkItem(Ticket ticket) {
        var type = switch (ticket.kind()) {
            case "Epic" -> WorkItemType.EPIC;
            case "Defect" -> WorkItemType.BUG;
            case "Task" -> WorkItemType.TASK;
            default -> WorkItemType.OTHER;
        };
        return new WorkItem(ticket.key(), ticket.headline(), null, type, ticket.kind(), ticket.epicKey());
    }

    @Override
    public Ticket toNative(WorkItem workItem) {
        // The provider's own label is the round trip's source of truth, not the generic type.
        return new Ticket(workItem.id(), workItem.title(), workItem.providerTypeName(), workItem.parentId());
    }
}
