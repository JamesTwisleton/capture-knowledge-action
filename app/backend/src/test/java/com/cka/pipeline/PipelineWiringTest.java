package com.cka.pipeline;

import com.cka.core.Action;
import com.cka.core.ActionType;
import com.cka.core.AuditEntry;
import com.cka.core.AuditStep;
import com.cka.core.CapturedContent;
import com.cka.core.Knowledge;
import com.cka.core.Mention;
import com.cka.core.PageReference;
import com.cka.core.Provenance;
import com.cka.core.Summary;
import com.cka.core.event.ActionProposed;
import com.cka.core.event.ContentCaptured;
import com.cka.core.event.ContentSummarised;
import com.cka.core.event.KnowledgeStored;
import com.cka.core.event.PipelineEvent;
import com.cka.testdouble.CannedLlmProvider;
import com.cka.testdouble.InMemoryAuditStore;
import com.cka.testdouble.InMemoryEventBus;
import com.cka.testdouble.InMemoryKnowledgeProvider;
import com.cka.testdouble.InMemoryWorkItemProvider;
import com.cka.testdouble.ManualContentListener;
import com.cka.testdouble.ScriptedDecisionProvider;
import com.cka.testdouble.Ticket;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * T05's acceptance criterion: the core can be wired with test doubles for every interface and
 * run a fake event through the chain.
 *
 * <p>Everything here goes through the event bus. No stage is handed another stage, so the
 * second test can drive the Action stage on its own by publishing the event it listens for.
 */
class PipelineWiringTest {

    private static final Provenance DECIDER = new Provenance("scripted-decision", "scripted-model");

    private static final CapturedContent REFINEMENT = new CapturedContent(
            "memory://recordings/refinement.mp4",
            "Sprint Refinement 2026-10-07",
            "Sprint Refinement",
            "We agreed the login bug is a duplicate. Someone mentioned the old payments ticket.",
            "Login bug closed as duplicate.");

    private static final Ticket EPIC = new Ticket("CKA-1", "Authentication", "Epic", null);
    private static final Ticket LOGIN_BUG = new Ticket("CKA-7", "Login fails on Safari", "Defect", "CKA-1");

    private static final Mention LOGIN_BUG_MENTION =
            new Mention("the login bug is a duplicate", "CKA-7", 0.72, DECIDER);
    private static final Mention PAYMENTS_MENTION =
            new Mention("the old payments ticket", null, 0, DECIDER);

    private final InMemoryEventBus bus = new InMemoryEventBus();
    private final InMemoryAuditStore audit = new InMemoryAuditStore();
    private final InMemoryKnowledgeProvider knowledge = new InMemoryKnowledgeProvider();
    private final InMemoryWorkItemProvider workItems = new InMemoryWorkItemProvider(EPIC, LOGIN_BUG);
    private final ScriptedDecisionProvider decision =
            new ScriptedDecisionProvider(LOGIN_BUG_MENTION, PAYMENTS_MENTION);

    @Test
    void capturedContentRunsThroughEveryStageAndIsAuditedAsOneChain() {
        var listener = new ManualContentListener();
        // The audit trail starts first so that, on this synchronous bus, it records each event
        // before the stage that reacts to it publishes the next one.
        new AuditTrail(audit, bus).start();
        new CaptureStage(listener, bus).start();
        new SummariseStage(new CannedLlmProvider(), bus).start();
        new KnowledgeStage(knowledge, bus).start();
        new ActionStage(decision, workItems, bus, audit).start();

        listener.find(REFINEMENT);

        assertThat(bus.published())
                .extracting(PipelineEvent::getClass)
                .containsExactly(ContentCaptured.class, ContentSummarised.class, KnowledgeStored.class,
                        ActionProposed.class);
        var chainId = bus.published().getFirst().chainId();
        assertThat(bus.published()).extracting(PipelineEvent::chainId).containsOnly(chainId);

        var summary = new Summary("Summary of Sprint Refinement 2026-10-07", CannedLlmProvider.PROVENANCE);
        assertThat(knowledge.pages()).containsValue(summary);

        var proposed = (ActionProposed) bus.published().getLast();
        assertThat(proposed.action())
                .isEqualTo(new Action(ActionType.COMMENT, "CKA-7", "the login bug is a duplicate", 0.72, DECIDER));

        assertThat(audit.chain(chainId))
                .extracting(AuditEntry::step)
                .containsExactly(AuditStep.CONTENT_CAPTURED, AuditStep.CONTENT_SUMMARISED,
                        AuditStep.KNOWLEDGE_STORED, AuditStep.ACTION_PROPOSED, AuditStep.MENTION_UNMATCHED);
        assertThat(audit.chain(chainId).getLast().detail()).contains("the old payments ticket");
    }

    @Test
    void theActionStageRunsAloneOnAKnowledgeStoredEventFromAnywhere() {
        new ActionStage(decision, workItems, bus, audit).start();
        var chainId = UUID.randomUUID();
        var page = new PageReference("refinement.md", "memory://refinement.md");
        var summary = new Summary("Login bug closed as duplicate.", CannedLlmProvider.PROVENANCE);

        bus.publish(new KnowledgeStored(chainId, new Knowledge(page, REFINEMENT, summary)));

        assertThat(bus.published()).last()
                .isEqualTo(new ActionProposed(chainId,
                        new Action(ActionType.COMMENT, "CKA-7", "the login bug is a duplicate", 0.72, DECIDER)));
    }
}
