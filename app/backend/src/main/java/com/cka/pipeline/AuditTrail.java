package com.cka.pipeline;

import com.cka.core.Action;
import com.cka.core.AuditEntry;
import com.cka.core.AuditStep;
import com.cka.core.Decider;
import com.cka.core.Mention;
import com.cka.core.event.ActionApplied;
import com.cka.core.event.ActionFailed;
import com.cka.core.event.ActionProposed;
import com.cka.core.event.ContentCaptured;
import com.cka.core.event.ContentSummarised;
import com.cka.core.event.KnowledgeStored;
import com.cka.core.event.PipelineEvent;
import com.cka.provider.audit.AuditStore;
import com.cka.provider.eventbus.EventBus;
import java.time.Clock;
import java.util.UUID;
import lombok.RequiredArgsConstructor;

/**
 * The audit service (PRD 9.1): the one thing that writes the action decision chain, and the only
 * place that decides how a step is phrased and what it carries.
 *
 * <p>Most of the chain arrives as events, so it is a sink on the bus — nothing waits for it, and
 * no stage knows it is there. Three steps have no event, because no stage is waiting on them: an
 * unmatched mention, and a human's accept or reject in triage, which is the final approval rather
 * than a request to anyone else. "No event" is not the same as "nothing acts on it" — a person
 * can link or dismiss an unmatched mention (PRD 9.2), and T23 adds the methods for that beside
 * these ones. Those are the methods below, and a caller reaches for one
 * instead of building an entry: that is T14's answer to the question T10 left open. Writing the
 * store directly was fine while an entry was three fields; now that it is nine, two writers
 * would be two vocabularies, and the one that forgot the action type would silently cost the
 * trust ramp its data (PRD 9.2).
 *
 * <p>Note what is <em>not</em> here. An action CKA applied by itself is {@link
 * AuditStep#ACTION_APPLIED} with the system as its decider; a human's accept is its own entry
 * before it. The chain holds both, in order, rather than one entry trying to say both things.
 */
@RequiredArgsConstructor
public class AuditTrail {

    private final AuditStore audit;
    private final EventBus bus;

    /** Injected rather than {@code Instant.now()} so a test can pin the chain to one instant. */
    private final Clock clock;

    public void start() {
        bus.subscribe(PipelineEvent.class, event -> audit.record(entryFor(event)));
    }

    /**
     * A mention that matched no candidate in the pool: recorded, never proposed, never dropped
     * (PRD 9.1). It carries the provider that found it but no confidence — a score against no
     * candidate is meaningless — and no action type, because there is no action.
     */
    public void mentionUnmatched(UUID chainId, Mention mention) {
        audit.record(entry(chainId, AuditStep.MENTION_UNMATCHED)
                .detail("No candidate matched \"" + mention.excerpt() + "\"")
                .producedBy(mention.scoredBy())
                .build());
    }

    /** A human accepting a proposal in triage, which is the final approval (PRD 9.1). */
    public void accepted(UUID chainId, Action action, String human) {
        audit.record(decision(chainId, AuditStep.ACTION_ACCEPTED, "Accepted", action, human));
    }

    /** A human rejecting one. Recorded as fully as an accept: the trust ramp counts both. */
    public void rejected(UUID chainId, Action action, String human) {
        audit.record(decision(chainId, AuditStep.ACTION_REJECTED, "Rejected", action, human));
    }

    private AuditEntry entryFor(PipelineEvent event) {
        return switch (event) {
            case ContentCaptured e ->
                entry(e.chainId(), AuditStep.CONTENT_CAPTURED)
                        .detail("Captured \"" + e.content().title() + "\" from "
                                + e.content().sourceUri())
                        .build();
            case ContentSummarised e ->
                entry(e.chainId(), AuditStep.CONTENT_SUMMARISED)
                        .detail("Summarised by " + e.summary().producedBy().provider() + "/"
                                + e.summary().producedBy().model())
                        .producedBy(e.summary().producedBy())
                        // The prompt version belongs on this step and nowhere else in the chain:
                        // it is the summary's own fact, and an action several steps later reads
                        // it from here rather than carrying a copy (PRD 7.8).
                        .promptVersion(e.summary().promptVersion())
                        .build();
            case KnowledgeStored e ->
                entry(e.chainId(), AuditStep.KNOWLEDGE_STORED)
                        .detail("Stored at " + e.knowledge().page().location())
                        .build();
            case ActionProposed e ->
                about(e.chainId(), AuditStep.ACTION_PROPOSED, e.action())
                        .detail(describe("Proposed", e.action()))
                        .build();
            case ActionApplied e ->
                about(e.chainId(), AuditStep.ACTION_APPLIED, e.action())
                        .detail(describe("Applied", e.action()))
                        .build();
            case ActionFailed e ->
                about(e.chainId(), AuditStep.ACTION_FAILED, e.action())
                        .detail(describe("Failed", e.action()) + ": " + e.error())
                        .build();
        };
    }

    /**
     * What every entry shares: its chain, its step, the moment it happened, and the system as its
     * decider. The two steps a human decides override that last one; each caller adds the detail.
     */
    private AuditEntry.AuditEntryBuilder entry(UUID chainId, AuditStep step) {
        return AuditEntry.builder()
                .chainId(chainId)
                .step(step)
                .recordedAt(clock.instant())
                .decider(Decider.system());
    }

    /** An entry about an action, carrying what the PRD names together: type, confidence, scorer. */
    private AuditEntry.AuditEntryBuilder about(UUID chainId, AuditStep step, Action action) {
        return entry(chainId, step)
                .actionType(action.type())
                .producedBy(action.decidedBy())
                .confidence(action.confidence());
    }

    /**
     * A human's accept or reject. It keeps the confidence and the provenance of the action they
     * judged, which is what makes the history answerable: "we proposed this at 0.81 and they said
     * no" is the data the trust ramp and the J5 suggestion are built on.
     */
    private AuditEntry decision(UUID chainId, AuditStep step, String verb, Action action, String human) {
        return about(chainId, step, action)
                .detail(describe(verb, action))
                .decider(Decider.human(human))
                .build();
    }

    private static String describe(String verb, Action action) {
        return verb + " " + action.type() + " on " + action.workItemId();
    }
}
