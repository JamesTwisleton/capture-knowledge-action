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
import com.cka.core.WorkItem;
import com.cka.core.event.ActionProposed;
import com.cka.core.event.ContentCaptured;
import com.cka.core.event.ContentSummarised;
import com.cka.core.event.KnowledgeStored;
import com.cka.core.event.PipelineEvent;
import com.cka.provider.audit.AuditStore;
import com.cka.provider.capture.ContentListener;
import com.cka.provider.decision.DecisionProvider;
import com.cka.provider.knowledge.KnowledgeProvider;
import com.cka.provider.llm.LlmProvider;
import com.cka.provider.workitem.WorkItemProvider;
import com.cka.testsupport.InMemoryEventBus;
import com.cka.testsupport.Ticket;
import com.cka.testsupport.TicketMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * T05's acceptance criterion: the core wired with a stand-in for every provider interface,
 * running a fake event through the chain.
 *
 * <p>The providers are mocked, because every one of them is a single call the stage makes and
 * hands on — there is no behaviour to fake, only a value to return. The bus is the exception
 * and is a real in-memory implementation: its job <em>is</em> behaviour (deliver each event to
 * the subscribers whose type matches), and stubbing that would mean writing it in mocks.
 *
 * <p>Everything here travels over that bus. No stage is handed another stage, which is what
 * lets the second test drive the Action stage alone by publishing the event it listens for.
 */
@ExtendWith(MockitoExtension.class)
class PipelineWiringTest {

    private static final Provenance LLM = new Provenance("test-llm", "test-model");
    private static final Provenance DECIDER = new Provenance("test-decision", "test-model");

    private static final CapturedContent REFINEMENT = new CapturedContent(
            "memory://recordings/refinement.mp4",
            "Sprint Refinement 2026-10-07",
            "Sprint Refinement",
            "We agreed the login bug is a duplicate. Someone mentioned the old payments ticket.",
            "Login bug closed as duplicate.");
    private static final Summary SUMMARY =
            new Summary("Decided: the login bug is a duplicate of an older report.", LLM);
    private static final PageReference PAGE =
            new PageReference("Sprint Refinement 2026-10-07.md", "memory://vault/refinement.md");
    private static final Knowledge KNOWLEDGE = new Knowledge(PAGE, REFINEMENT, SUMMARY);

    /** Native tracker items, mapped into the pool the way a real work item provider does. */
    private static final List<WorkItem> POOL = Stream.of(
                    new Ticket("CKA-1", "Authentication", "Epic", null),
                    new Ticket("CKA-7", "Login fails on Safari", "Defect", "CKA-1"))
            .map(new TicketMapper()::toWorkItem)
            .toList();

    private static final Mention LOGIN_BUG_MENTION =
            new Mention("the login bug is a duplicate", "CKA-7", 0.72, DECIDER);
    private static final Mention PAYMENTS_MENTION =
            new Mention("the old payments ticket", null, 0, DECIDER);
    private static final Action PROPOSED_COMMENT =
            new Action(ActionType.COMMENT, "CKA-7", "the login bug is a duplicate", 0.72, DECIDER);

    private final InMemoryEventBus bus = new InMemoryEventBus();

    @Mock
    private ContentListener listener;
    @Mock
    private LlmProvider llm;
    @Mock
    private KnowledgeProvider knowledge;
    @Mock
    private DecisionProvider decision;
    @Mock
    private WorkItemProvider workItems;
    @Mock
    private AuditStore audit;
    @Captor
    private ArgumentCaptor<Consumer<CapturedContent>> onContent;
    @Captor
    private ArgumentCaptor<AuditEntry> auditEntries;

    @Test
    void capturedContentRunsThroughEveryStageAndIsAuditedAsOneChain() {
        when(llm.summarise(REFINEMENT)).thenReturn(SUMMARY);
        when(knowledge.store(REFINEMENT, SUMMARY)).thenReturn(PAGE);
        when(workItems.candidatePool()).thenReturn(POOL);
        when(decision.detectMentions(KNOWLEDGE, POOL)).thenReturn(List.of(LOGIN_BUG_MENTION));

        // The audit trail subscribes first so that, on this synchronous bus, it records each
        // event before the stage reacting to it publishes the next one.
        new AuditTrail(audit, bus).start();
        new CaptureStage(listener, bus).start();
        new SummariseStage(llm, bus).start();
        new KnowledgeStage(knowledge, bus).start();
        new ActionStage(decision, workItems, bus, audit).start();

        // CaptureStage registered a consumer with the listener; a real one would call it on
        // finding a recording, so calling it here is what "a fake event" means for this stage.
        verify(listener).listen(onContent.capture());
        onContent.getValue().accept(REFINEMENT);

        assertThat(bus.published())
                .extracting(PipelineEvent::getClass)
                .containsExactly(ContentCaptured.class, ContentSummarised.class, KnowledgeStored.class,
                        ActionProposed.class);
        var chainId = bus.published().getFirst().chainId();
        assertThat(bus.published()).extracting(PipelineEvent::chainId).containsOnly(chainId);
        assertThat(bus.published().getLast()).isEqualTo(new ActionProposed(chainId, PROPOSED_COMMENT));

        verify(audit, times(4)).record(auditEntries.capture());
        assertThat(auditEntries.getAllValues())
                .extracting(AuditEntry::chainId, AuditEntry::step)
                .containsExactly(
                        tuple(chainId, AuditStep.CONTENT_CAPTURED),
                        tuple(chainId, AuditStep.CONTENT_SUMMARISED),
                        tuple(chainId, AuditStep.KNOWLEDGE_STORED),
                        tuple(chainId, AuditStep.ACTION_PROPOSED));
    }

    @Test
    void theActionStageRunsAloneOnAKnowledgeStoredEventFromAnywhere() {
        when(workItems.candidatePool()).thenReturn(POOL);
        when(decision.detectMentions(KNOWLEDGE, POOL)).thenReturn(List.of(LOGIN_BUG_MENTION));
        new ActionStage(decision, workItems, bus, audit).start();
        var chainId = UUID.randomUUID();

        bus.publish(new KnowledgeStored(chainId, KNOWLEDGE));

        assertThat(bus.published()).last().isEqualTo(new ActionProposed(chainId, PROPOSED_COMMENT));
    }

    @Test
    void aMentionMatchingNoCandidateIsAuditedAndNeverProposed() {
        when(workItems.candidatePool()).thenReturn(POOL);
        when(decision.detectMentions(KNOWLEDGE, POOL)).thenReturn(List.of(PAYMENTS_MENTION));
        new ActionStage(decision, workItems, bus, audit).start();
        var chainId = UUID.randomUUID();

        bus.publish(new KnowledgeStored(chainId, KNOWLEDGE));

        assertThat(bus.published()).noneMatch(ActionProposed.class::isInstance);
        verify(audit).record(auditEntries.capture());
        assertThat(auditEntries.getValue())
                .isEqualTo(new AuditEntry(chainId, AuditStep.MENTION_UNMATCHED,
                        "No candidate matched \"the old payments ticket\""));
    }
}
