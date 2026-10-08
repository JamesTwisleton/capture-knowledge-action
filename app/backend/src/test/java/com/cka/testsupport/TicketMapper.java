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
        return WorkItem.builder()
                .id(ticket.key())
                .title(ticket.headline())
                .type(type)
                .providerTypeName(ticket.kind())
                .parentId(ticket.epicKey())
                .build();
    }

    @Override
    public Ticket toNative(WorkItem workItem) {
        // The provider's own label is the round trip's source of truth, not the generic type.
        return Ticket.builder()
                .key(workItem.id())
                .headline(workItem.title())
                .kind(workItem.providerTypeName())
                .epicKey(workItem.parentId())
                .build();
    }
}
