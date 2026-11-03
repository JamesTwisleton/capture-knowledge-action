package com.cka.pipeline;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.cka.core.ActionType;
import com.cka.core.AuditEntry;
import com.cka.core.AuditStep;
import com.cka.core.CapturedContent;
import com.cka.core.Decider;
import com.cka.core.Knowledge;
import com.cka.core.Mention;
import com.cka.core.PageReference;
import com.cka.core.Provenance;
import com.cka.core.Summary;
import com.cka.core.WorkItem;
import com.cka.core.WorkItemType;
import com.cka.core.event.ActionApplied;
import com.cka.core.event.ActionProposed;
import com.cka.provider.audit.AuditStore;
import com.cka.provider.capture.ContentListener;
import com.cka.provider.decision.DecisionProvider;
import com.cka.provider.knowledge.KnowledgeProvider;
import com.cka.provider.llm.LlmProvider;
import com.cka.provider.workitem.WorkItemProvider;
import com.cka.testsupport.InMemoryEventBus;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.function.Consumer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * T14's acceptance criterion: after a full run, the chain for any applied or rejected action can
 * be traced back to the original recording.
 *
 * <p>So this is a full run — every stage, wired together, with the <em>real</em> SQLite audit
 * store underneath rather than a mock. That is the point of the test: the trace has to survive
 * the round trip through the database, and a mocked store would prove only that the pipeline
 * called it. The providers are mocked, because each is a single call a stage makes and hands on,
 * and none of the five exists yet (T06, T09, T12, T13 bring them).
 *
 * <p>Two recordings run through: one whose proposal a human rejects, one whose proposal they
 * accept and which is then applied. Both are traced, and nothing but the chain is used to do it
 * — the test knows the recordings' names, never their chain ids, which is the only way to prove
 * the trace is the chain's work rather than the test's bookkeeping.
 */
@SpringBootTest(properties = "cka.provider.eventbus=none")
@DisplayName("An applied or rejected action traces back to the recording it came from (T14)")
class ActionDecisionChainTest {

    private static final Path DATABASE = temporaryDatabasePath();
    private static final Instant NOW = Instant.parse("2026-10-07T09:15:00Z");

    private static final Provenance LLM =
            Provenance.builder().provider("openai").model("gpt-test").build();
    private static final Provenance DECIDER =
            Provenance.builder().provider("jev").model("typesafe-1").build();

    private static final List<WorkItem> POOL = List.of(
            WorkItem.builder()
                    .id("CKA-7")
                    .title("Login fails on Safari")
                    .type(WorkItemType.TASK)
                    .providerTypeName("Defect")
                    .build(),
            WorkItem.builder()
                    .id("CKA-9")
                    .title("Payment retries")
                    .type(WorkItemType.TASK)
                    .providerTypeName("Defect")
                    .build());

    @Autowired
    private AuditStore audit;

    private final InMemoryEventBus bus = new InMemoryEventBus();
    private final ContentListener listener = mock(ContentListener.class);
    private final LlmProvider llm = mock(LlmProvider.class);
    private final KnowledgeProvider knowledge = mock(KnowledgeProvider.class);
    private final DecisionProvider decision = mock(DecisionProvider.class);
    private final WorkItemProvider workItems = mock(WorkItemProvider.class);

    private AuditTrail auditTrail;

    @DynamicPropertySource
    static void sqliteDatabase(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> "jdbc:sqlite:" + DATABASE);
    }

    private static Path temporaryDatabasePath() {
        try {
            return Files.createTempDirectory("cka-chain-test").resolve("cka.db");
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @BeforeEach
    void wireThePipeline() {
        auditTrail = new AuditTrail(audit, bus, Clock.fixed(NOW, ZoneOffset.UTC));
        // The audit service subscribes first so that, on this synchronous bus, it records each
        // event before the stage reacting to it publishes the next one.
        auditTrail.start();
        new CaptureStage(listener, bus).start();
        new SummariseStage(llm, bus).start();
        new KnowledgeStage(knowledge, bus).start();
        new ActionStage(decision, workItems, bus, auditTrail).start();
        when(workItems.candidatePool()).thenReturn(POOL);
    }

    @Test
    @DisplayName("A rejected action traces back through its chain to the recording")
    void aRejectedActionTracesBackThroughItsChainToTheRecording() {
        var refinement = recording("refinement.mp4", "Sprint Refinement 2026-10-07");
        // Two mentions: the one that matches CKA-7 and becomes a proposal, and one that matches
        // nothing in the pool — the chain has to hold both (PRD 9.1).
        var proposal =
                run(refinement, matched("the login bug is a duplicate", "CKA-7"), unmatched("the old wiki page"));
        var rejectedAt = Instant.parse("2026-10-07T10:00:00Z");

        new AuditTrail(audit, bus, Clock.fixed(rejectedAt, ZoneOffset.UTC))
                .rejected(proposal.chainId(), proposal.action(), "james");

        // The trace, starting from the one thing a triage view has: the decision itself.
        var rejection = decision(AuditStep.ACTION_REJECTED, rejectedAt);
        assertThat(rejection.decider()).isEqualTo(Decider.human("james"));

        var chain = audit.chain(rejection.chainId());
        // A subsequence for the spine, because that order is causal — each step is what makes
        // the next one happen. Where the unmatched mention falls among the proposals is the
        // decision provider's iteration order and nothing the pipeline promises, so it is
        // asserted as present rather than as positioned.
        assertThat(chain)
                .extracting(AuditEntry::step)
                .containsSubsequence(
                        AuditStep.CONTENT_CAPTURED,
                        AuditStep.CONTENT_SUMMARISED,
                        AuditStep.KNOWLEDGE_STORED,
                        AuditStep.ACTION_PROPOSED,
                        AuditStep.ACTION_REJECTED)
                .contains(AuditStep.MENTION_UNMATCHED);
        // "Traced back to the original recording": the first step names the file it came from.
        assertThat(chain.getFirst().detail()).contains("memory://recordings/refinement.mp4");
    }

    @Test
    @DisplayName("An applied action traces back the same way, through the accept that allowed it")
    void anAppliedActionTracesBackTheSameWay() {
        var standup = recording("standup.mp4", "Standup 2026-10-08");
        var proposal = run(standup, matched("close the payment retries ticket", "CKA-9"));
        var acceptedAt = Instant.parse("2026-10-08T11:00:00Z");
        var trail = new AuditTrail(audit, bus, Clock.fixed(acceptedAt, ZoneOffset.UTC));

        trail.accepted(proposal.chainId(), proposal.action(), "james");
        // What a work item provider publishes once the accepted action has been carried out.
        bus.publish(ActionApplied.builder()
                .chainId(proposal.chainId())
                .action(proposal.action())
                .build());

        var acceptance = decision(AuditStep.ACTION_ACCEPTED, acceptedAt);
        var chain = audit.chain(acceptance.chainId());
        assertThat(chain)
                .extracting(AuditEntry::step, AuditEntry::decider)
                .containsExactly(
                        tuple(AuditStep.CONTENT_CAPTURED, Decider.system()),
                        tuple(AuditStep.CONTENT_SUMMARISED, Decider.system()),
                        tuple(AuditStep.KNOWLEDGE_STORED, Decider.system()),
                        tuple(AuditStep.ACTION_PROPOSED, Decider.system()),
                        tuple(AuditStep.ACTION_ACCEPTED, Decider.human("james")),
                        // Applied by CKA, on the human's authority recorded in the step above.
                        tuple(AuditStep.ACTION_APPLIED, Decider.system()));
        assertThat(chain.getFirst().detail()).contains("memory://recordings/standup.mp4");
    }

    @Test
    @DisplayName("Each recording gets its own chain, so a trace cannot cross between them")
    void eachRecordingGetsItsOwnChain() {
        var first = run(recording("one.mp4", "One"), matched("the login bug", "CKA-7"));
        var second = run(recording("two.mp4", "Two"), matched("the login bug", "CKA-7"));

        assertThat(first.chainId()).isNotEqualTo(second.chainId());
        assertThat(audit.chain(first.chainId()))
                .allSatisfy(entry -> assertThat(entry.detail()).doesNotContain("two.mp4"));
    }

    /**
     * The decision history, narrowed to the kind this test made — which is how a view reads it
     * too, and all the test is allowed to know. One database is shared by the whole class, as
     * one Spring context is, so a query that deliberately crosses chains sees the other tests'
     * decisions; reaching for a chain id the test kept would make the trace its own bookkeeping
     * rather than the chain's work.
     */
    private AuditEntry decision(AuditStep step, Instant since) {
        return audit.decisions(ActionType.COMMENT, since).stream()
                .filter(entry -> entry.step() == step)
                .findFirst()
                .orElseThrow(() -> new AssertionError("no " + step + " in the history since " + since));
    }

    /**
     * One recording through every stage, start to finish, returning the proposal it produced —
     * which is what a human would be shown in triage.
     */
    private ActionProposed run(CapturedContent content, Mention... mentions) {
        var summary = Summary.builder()
                .text("Decided: " + content.title())
                .producedBy(LLM)
                .build();
        var page = PageReference.builder()
                .id(content.title() + ".md")
                .location("memory://vault/" + content.title() + ".md")
                .build();
        when(llm.summarise(content)).thenReturn(summary);
        when(knowledge.store(content, summary)).thenReturn(page);
        when(decision.detectMentions(
                        Knowledge.builder()
                                .page(page)
                                .content(content)
                                .summary(summary)
                                .build(),
                        POOL))
                .thenReturn(List.of(mentions));

        // CaptureStage registered a consumer with the listener; a real folder watcher would call
        // it on finding a recording, so calling it here is what "a full run" starts with.
        ArgumentCaptor<Consumer<CapturedContent>> onContent = ArgumentCaptor.captor();
        verify(listener).listen(onContent.capture());
        onContent.getValue().accept(content);

        return bus.published().stream()
                .filter(ActionProposed.class::isInstance)
                .map(ActionProposed.class::cast)
                .filter(proposed -> proposed.action().excerpt().equals(mentions[0].excerpt()))
                .reduce((earlier, later) -> later)
                .orElseThrow(() -> new AssertionError("no proposal was published for " + content.title()));
    }

    private static CapturedContent recording(String file, String title) {
        return CapturedContent.builder()
                .sourceUri("memory://recordings/" + file)
                .title(title)
                .contentType("Sprint Refinement")
                .transcript("We agreed something about the login bug.")
                .build();
    }

    private static Mention matched(String excerpt, String workItemId) {
        return Mention.builder()
                .excerpt(excerpt)
                .workItemId(workItemId)
                .confidence(0.72)
                .scoredBy(DECIDER)
                .build();
    }

    private static Mention unmatched(String excerpt) {
        return Mention.builder()
                .excerpt(excerpt)
                .confidence(0)
                .scoredBy(DECIDER)
                .build();
    }
}
