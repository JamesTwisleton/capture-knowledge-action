package com.cka.testdouble;

import com.cka.core.WorkItem;
import com.cka.provider.ProviderHealth;
import com.cka.provider.workitem.WorkItemProvider;

import java.util.ArrayList;
import java.util.List;

/**
 * A fixed set of native {@link Ticket}s, served as the candidate pool through a
 * {@link TicketMapper} — the way a real provider maps its tracker's items — plus a record of
 * every comment and close asked of it.
 */
public class InMemoryWorkItemProvider implements WorkItemProvider {

    private final TicketMapper mapper = new TicketMapper();
    private final List<Ticket> tickets;
    private final List<String> calls = new ArrayList<>();

    public InMemoryWorkItemProvider(Ticket... tickets) {
        this.tickets = List.of(tickets);
    }

    @Override
    public List<WorkItem> candidatePool() {
        return tickets.stream().map(mapper::toWorkItem).toList();
    }

    @Override
    public void comment(String workItemId, String text) {
        calls.add("comment " + workItemId + ": " + text);
    }

    @Override
    public void close(String workItemId) {
        calls.add("close " + workItemId);
    }

    /** Every mutating call made, in order, as "comment ID: text" or "close ID". */
    public List<String> calls() {
        return List.copyOf(calls);
    }

    @Override
    public ProviderHealth checkHealth() {
        return ProviderHealth.up("in memory");
    }
}
