package com.cka.pipeline;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

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
@DisplayName("The core runs a fake event through every stage (T05)")
class PipelineWiringTest {

    private static final Provenance LLM =
            Provenance.builder().provider("test-llm").model("test-model").build();
    private static final Provenance DECIDER =
            Provenance.builder().provider("test-decision").model("test-model").build();

    private static final CapturedContent REFINEMENT = CapturedContent.builder()
            .sourceUri("memory://recordings/refinement.mp4")
            .title("Sprint Refinement 2026-10-07")
            .contentType("Sprint Refinement")
            .transcript("We agreed the login bug is a duplicate. Someone mentioned the old payments ticket.")
            .platformSummary("Login bug closed as duplicate.")
            .build();
    private static final Summary SUMMARY = Summary.builder()
            .text("Decided: the login bug is a duplicate of an older report.")
            .producedBy(LLM)
            .build();
    private static final PageReference PAGE = PageReference.builder()
            .id("Sprint Refinement 2026-10-07.md")
            .location("memory://vault/refinement.md")
            .build();
    private static final Knowledge KNOWLEDGE =
            Knowledge.builder().page(PAGE).content(REFINEMENT).summary(SUMMARY).build();

    /** Native tracker items, mapped into the pool the way a real work item provider does. */
    private static final List<WorkItem> POOL = Stream.of(
                    Ticket.builder()
                            .key("CKA-1")
                            .headline("Authentication")
                            .kind("Epic")
                            .build(),
                    Ticket.builder()
                            .key("CKA-7")
                            .headline("Login fails on Safari")
                            .kind("Defect")
                            .epicKey("CKA-1")
                            .build())
            .map(new TicketMapper()::toWorkItem)
            .toList();

    private static final Mention LOGIN_BUG_MENTION = Mention.builder()
            .excerpt("the login bug is a duplicate")
            .workItemId("CKA-7")
            .confidence(0.72)
            .scoredBy(DECIDER)
            .build();
    private static final Mention PAYMENTS_MENTION = Mention.builder()
            .excerpt("the old payments ticket")
            .confidence(0)
            .scoredBy(DECIDER)
            .build();
    private static final Action PROPOSED_COMMENT = Action.builder()
            .type(ActionType.COMMENT)
            .workItemId("CKA-7")
            .excerpt("the login bug is a duplicate")
            .confidence(0.72)
            .decidedBy(DECIDER)
            .build();

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
    @DisplayName("Captured content runs through every stage and is audited as one chain")
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
                .hasExactlyElementsOfTypes(
                        ContentCaptured.class, ContentSummarised.class, KnowledgeStored.class, ActionProposed.class);
        var chainId = bus.published().getFirst().chainId();
        assertThat(bus.published()).extracting(PipelineEvent::chainId).containsOnly(chainId);
        assertThat(bus.published().getLast())
                .isEqualTo(ActionProposed.builder()
                        .chainId(chainId)
                        .action(PROPOSED_COMMENT)
                        .build());

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
    @DisplayName("The Action stage runs alone, on a knowledge-stored event from anywhere")
    void theActionStageRunsAloneOnAKnowledgeStoredEventFromAnywhere() {
        when(workItems.candidatePool()).thenReturn(POOL);
        when(decision.detectMentions(KNOWLEDGE, POOL)).thenReturn(List.of(LOGIN_BUG_MENTION));
        new ActionStage(decision, workItems, bus, audit).start();
        var chainId = UUID.randomUUID();

        bus.publish(
                KnowledgeStored.builder().chainId(chainId).knowledge(KNOWLEDGE).build());

        assertThat(bus.published())
                .last()
                .isEqualTo(ActionProposed.builder()
                        .chainId(chainId)
                        .action(PROPOSED_COMMENT)
                        .build());
    }

    @Test
    @DisplayName("A mention matching no candidate is audited, and never proposed")
    void aMentionMatchingNoCandidateIsAuditedAndNeverProposed() {
        when(workItems.candidatePool()).thenReturn(POOL);
        when(decision.detectMentions(KNOWLEDGE, POOL)).thenReturn(List.of(PAYMENTS_MENTION));
        new ActionStage(decision, workItems, bus, audit).start();
        var chainId = UUID.randomUUID();

        bus.publish(
                KnowledgeStored.builder().chainId(chainId).knowledge(KNOWLEDGE).build());

        assertThat(bus.published()).noneMatch(ActionProposed.class::isInstance);
        verify(audit).record(auditEntries.capture());
        assertThat(auditEntries.getValue())
                .isEqualTo(AuditEntry.builder()
                        .chainId(chainId)
                        .step(AuditStep.MENTION_UNMATCHED)
                        .detail("No candidate matched \"the old payments ticket\"")
                        .build());
    }
}
