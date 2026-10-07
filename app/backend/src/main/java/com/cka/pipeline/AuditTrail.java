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
            case ContentCaptured e ->
                AuditEntry.builder()
                        .chainId(e.chainId())
                        .step(AuditStep.CONTENT_CAPTURED)
                        .detail("Captured \"" + e.content().title() + "\" from "
                                + e.content().sourceUri())
                        .build();
            case ContentSummarised e ->
                AuditEntry.builder()
                        .chainId(e.chainId())
                        .step(AuditStep.CONTENT_SUMMARISED)
                        .detail("Summarised by " + e.summary().producedBy().provider() + "/"
                                + e.summary().producedBy().model())
                        .build();
            case KnowledgeStored e ->
                AuditEntry.builder()
                        .chainId(e.chainId())
                        .step(AuditStep.KNOWLEDGE_STORED)
                        .detail("Stored at " + e.knowledge().page().location())
                        .build();
            case ActionProposed e ->
                AuditEntry.builder()
                        .chainId(e.chainId())
                        .step(AuditStep.ACTION_PROPOSED)
                        .detail("Proposed " + e.action().type() + " on "
                                + e.action().workItemId())
                        .build();
            case ActionApplied e ->
                AuditEntry.builder()
                        .chainId(e.chainId())
                        .step(AuditStep.ACTION_APPLIED)
                        .detail("Applied " + e.action().type() + " on "
                                + e.action().workItemId())
                        .build();
            case ActionFailed e ->
                AuditEntry.builder()
                        .chainId(e.chainId())
                        .step(AuditStep.ACTION_FAILED)
                        .detail("Failed " + e.action().type() + " on "
                                + e.action().workItemId() + ": " + e.error())
                        .build();
        };
    }
}
