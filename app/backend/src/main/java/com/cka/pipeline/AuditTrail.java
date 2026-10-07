package com.cka.pipeline;

import com.cka.core.AuditEntry;
import com.cka.core.AuditStep;
import com.cka.core.event.ActionApplied;
import com.cka.core.event.ActionFailed;
import com.cka.core.event.ActionProposed;
import com.cka.core.event.ContentCaptured;
import com.cka.core.event.ContentSummarised;
import com.cka.core.event.KnowledgeStored;
import com.cka.core.event.PipelineEvent;
import com.cka.provider.audit.AuditStore;
import com.cka.provider.eventbus.EventBus;
import lombok.RequiredArgsConstructor;

/**
 * Records every pipeline event in the audit store, as one step of its action decision chain.
 * A sink on the bus, not a stage: nothing waits for it, and no stage knows it is there.
 *
 * <p>Steps with no event — an unmatched mention — are written directly by the stage that
 * finds them. Whether the audit service stays a bus subscriber is T14's call (PRD 7.1).
 */
@RequiredArgsConstructor
public class AuditTrail {

    private final AuditStore audit;
    private final EventBus bus;

    public void start() {
        bus.subscribe(PipelineEvent.class, event -> audit.record(entryFor(event)));
    }

    private static AuditEntry entryFor(PipelineEvent event) {
        return switch (event) {
            case ContentCaptured e -> new AuditEntry(e.chainId(), AuditStep.CONTENT_CAPTURED,
                    "Captured \"" + e.content().title() + "\" from " + e.content().sourceUri());
            case ContentSummarised e -> new AuditEntry(e.chainId(), AuditStep.CONTENT_SUMMARISED,
                    "Summarised by " + e.summary().producedBy().provider() + "/" + e.summary().producedBy().model());
            case KnowledgeStored e -> new AuditEntry(e.chainId(), AuditStep.KNOWLEDGE_STORED,
                    "Stored at " + e.knowledge().page().location());
            case ActionProposed e -> new AuditEntry(e.chainId(), AuditStep.ACTION_PROPOSED,
                    "Proposed " + e.action().type() + " on " + e.action().workItemId());
            case ActionApplied e -> new AuditEntry(e.chainId(), AuditStep.ACTION_APPLIED,
                    "Applied " + e.action().type() + " on " + e.action().workItemId());
            case ActionFailed e -> new AuditEntry(e.chainId(), AuditStep.ACTION_FAILED,
                    "Failed " + e.action().type() + " on " + e.action().workItemId() + ": " + e.error());
        };
    }
}
