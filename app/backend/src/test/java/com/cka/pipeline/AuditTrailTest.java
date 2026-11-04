package com.cka.pipeline;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import com.cka.core.Action;
import com.cka.core.ActionType;
import com.cka.core.AuditEntry;
import com.cka.core.AuditStep;
import com.cka.core.CapturedContent;
import com.cka.core.ChainId;
import com.cka.core.Decider;
import com.cka.core.Knowledge;
import com.cka.core.Mention;
import com.cka.core.PageReference;
import com.cka.core.Provenance;
import com.cka.core.Summary;
import com.cka.core.event.ActionApplied;
import com.cka.core.event.ActionFailed;
import com.cka.core.event.ActionProposed;
import com.cka.core.event.ContentCaptured;
import com.cka.core.event.ContentSummarised;
import com.cka.core.event.KnowledgeStored;
import com.cka.provider.audit.AuditStore;
import com.cka.testsupport.InMemoryEventBus;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The audit service (T14): what each step of the action decision chain is recorded as.
 *
 * <p>The store is a hand-written list rather than a Mockito mock, because every assertion here
 * is about the entries that came out of it and a captor per test reads worse than reading the
 * list. It is the exception the testsupport conventions allow — real behaviour (keep what you
 * are given, in order) that a stub would have to reimplement — and it is two lines, local to
 * this test, so it does not belong in {@code testsupport} either.
 */
@DisplayName("The audit service records each step of the action decision chain (T14)")
class AuditTrailTest {

    private static final Instant NOW = Instant.parse("2026-10-07T09:15:00Z");
    private static final Provenance LLM =
            Provenance.builder().provider("openai").model("gpt-test").build();
    private static final Provenance JEV =
            Provenance.builder().provider("jev").model("typesafe-1").build();

    private static final CapturedContent REFINEMENT = CapturedContent.builder()
            .sourceUri("memory://recordings/refinement.mp4")
            .title("Sprint Refinement 2026-10-07")
            .contentType("Sprint Refinement")
            .transcript("We agreed the login bug is a duplicate.")
            .build();
    private static final Summary SUMMARY = Summary.builder()
            .text("Decided: the login bug is a duplicate.")
            .producedBy(LLM)
            .promptVersion(3)
            .build();
    private static final Knowledge KNOWLEDGE = Knowledge.builder()
            .page(PageReference.builder()
                    .id("refinement.md")
                    .location("memory://vault/refinement.md")
                    .build())
            .content(REFINEMENT)
            .summary(SUMMARY)
            .build();
    private static final Action CLOSE_THE_LOGIN_BUG = Action.builder()
            .type(ActionType.CLOSE)
            .workItemId("CKA-7")
            .excerpt("close CKA-7, it is a duplicate")
            .confidence(0.81)
            .decidedBy(JEV)
            .build();

    private final List<AuditEntry> recorded = new ArrayList<>();
    private final AuditStore store = new RecordingStore(recorded);
    private final InMemoryEventBus bus = new InMemoryEventBus();
    private final AuditTrail audit = new AuditTrail(store, bus, Clock.fixed(NOW, ZoneOffset.UTC));

    @Test
    @DisplayName("Every pipeline event becomes the step it belongs to, in the chain it belongs to")
    void everyPipelineEventBecomesTheStepItBelongsTo() {
        var chainId = ChainId.next();
        audit.start();

        bus.publish(
                ContentCaptured.builder().chainId(chainId).content(REFINEMENT).build());
        bus.publish(ContentSummarised.builder()
                .chainId(chainId)
                .content(REFINEMENT)
                .summary(SUMMARY)
                .build());
        bus.publish(
                KnowledgeStored.builder().chainId(chainId).knowledge(KNOWLEDGE).build());
        bus.publish(ActionProposed.builder()
                .chainId(chainId)
                .action(CLOSE_THE_LOGIN_BUG)
                .build());
        bus.publish(ActionApplied.builder()
                .chainId(chainId)
                .action(CLOSE_THE_LOGIN_BUG)
                .build());
        bus.publish(ActionFailed.builder()
                .chainId(chainId)
                .action(CLOSE_THE_LOGIN_BUG)
                .error("GitHub rejected the request: 403")
                .build());

        // The sealed event set, so this list failing to compile is the point at which a new
        // event has been added without deciding what it means for the chain.
        assertThat(recorded)
                .extracting(AuditEntry::chainId, AuditEntry::step)
                .containsExactly(
                        tuple(chainId, AuditStep.CONTENT_CAPTURED),
                        tuple(chainId, AuditStep.CONTENT_SUMMARISED),
                        tuple(chainId, AuditStep.KNOWLEDGE_STORED),
                        tuple(chainId, AuditStep.ACTION_PROPOSED),
                        tuple(chainId, AuditStep.ACTION_APPLIED),
                        tuple(chainId, AuditStep.ACTION_FAILED));
    }

    @Test
    @DisplayName("Every entry is stamped with the time it was recorded, and who decided")
    void everyEntryIsStampedWithTheTimeItWasRecordedAndWhoDecided() {
        // "What happened, when ... decided by whom" (PRD 7.8) is required of every record, not
        // of the ones that happen to have a provider behind them.
        var chainId = ChainId.next();
        audit.start();

        bus.publish(
                ContentCaptured.builder().chainId(chainId).content(REFINEMENT).build());
        audit.mentionUnmatched(chainId, unmatchedMention());
        audit.accepted(chainId, CLOSE_THE_LOGIN_BUG, "james");

        assertThat(recorded).extracting(AuditEntry::recordedAt).containsOnly(NOW);
        assertThat(recorded).extracting(AuditEntry::decider).doesNotContainNull();
    }

    @Test
    @DisplayName("A summarised step records the provider, the model and the prompt version")
    void aSummarisedStepRecordsTheProviderTheModelAndThePromptVersion() {
        var chainId = ChainId.next();
        audit.start();

        bus.publish(ContentSummarised.builder()
                .chainId(chainId)
                .content(REFINEMENT)
                .summary(SUMMARY)
                .build());

        assertThat(recorded.getFirst().producedBy()).isEqualTo(LLM);
        assertThat(recorded.getFirst().decider()).isEqualTo(Decider.system());
        // PRD 7.8's "using which prompt version". This is the only step that records one, and
        // the only place in the chain where it is a fact rather than a copy.
        assertThat(recorded.getFirst().promptVersion()).isEqualTo(3);
    }

    @Test
    @DisplayName("An action step records its type, its confidence and who scored it")
    void anActionStepRecordsItsTypeItsConfidenceAndWhoScoredIt() {
        // Without the action type there is no per-action-type trust data (PRD 9.2), and without
        // the confidence and the provenance the triage view cannot say why something was
        // proposed — the three the PRD names together.
        var chainId = ChainId.next();
        audit.start();

        bus.publish(ActionProposed.builder()
                .chainId(chainId)
                .action(CLOSE_THE_LOGIN_BUG)
                .build());

        assertThat(recorded.getFirst())
                .extracting(AuditEntry::actionType, AuditEntry::confidence, AuditEntry::producedBy)
                .containsExactly(ActionType.CLOSE, 0.81, JEV);
    }

    @Test
    @DisplayName("A failed action records the error in words a person can act on")
    void aFailedActionRecordsTheError() {
        var chainId = ChainId.next();
        audit.start();

        bus.publish(ActionFailed.builder()
                .chainId(chainId)
                .action(CLOSE_THE_LOGIN_BUG)
                .error("GitHub rejected the request: 403")
                .build());

        assertThat(recorded.getFirst().detail()).contains("GitHub rejected the request: 403");
    }

    @Test
    @DisplayName("An unmatched mention is recorded, with the provider that found it")
    void anUnmatchedMentionIsRecordedWithTheProviderThatFoundIt() {
        // It has no event, because nothing downstream acts on it (PRD 9.1) — the stage that
        // finds one calls the audit service, which is why that is a method and not a subscriber.
        var chainId = ChainId.next();

        audit.mentionUnmatched(chainId, unmatchedMention());

        assertThat(recorded.getFirst())
                .extracting(
                        AuditEntry::step,
                        AuditEntry::actionType,
                        AuditEntry::producedBy,
                        AuditEntry::confidence,
                        AuditEntry::decider)
                .containsExactly(AuditStep.MENTION_UNMATCHED, null, JEV, null, Decider.system());
        assertThat(recorded.getFirst().detail()).contains("the old payments ticket");
    }

    @Test
    @DisplayName("A human accepting in triage is recorded against them, by action type")
    void aHumanAcceptingInTriageIsRecordedAgainstThem() {
        var chainId = ChainId.next();

        audit.accepted(chainId, CLOSE_THE_LOGIN_BUG, "james");

        assertThat(recorded.getFirst())
                .extracting(AuditEntry::step, AuditEntry::actionType, AuditEntry::decider, AuditEntry::confidence)
                .containsExactly(AuditStep.ACTION_ACCEPTED, ActionType.CLOSE, Decider.human("james"), 0.81);
    }

    @Test
    @DisplayName("A human rejecting in triage is recorded just as fully as an accept")
    void aHumanRejectingInTriageIsRecordedJustAsFullyAsAnAccept() {
        // PRD 9.2 computes trust from both, so a reject that was dropped — or recorded without
        // its action type — would quietly bias the score upwards.
        var chainId = ChainId.next();

        audit.rejected(chainId, CLOSE_THE_LOGIN_BUG, "james");

        assertThat(recorded.getFirst())
                .extracting(AuditEntry::step, AuditEntry::actionType, AuditEntry::decider, AuditEntry::confidence)
                .containsExactly(AuditStep.ACTION_REJECTED, ActionType.CLOSE, Decider.human("james"), 0.81);
    }

    private static Mention unmatchedMention() {
        return Mention.builder()
                .excerpt("the old payments ticket")
                .confidence(0)
                .scoredBy(JEV)
                .build();
    }

    /** Keeps what it is given, in order, and answers nothing else. */
    private record RecordingStore(List<AuditEntry> recorded) implements AuditStore {

        @Override
        public void record(AuditEntry entry) {
            recorded.add(entry);
        }

        @Override
        public List<AuditEntry> chain(UUID chainId) {
            return recorded.stream()
                    .filter(entry -> entry.chainId().equals(chainId))
                    .toList();
        }

        @Override
        public List<AuditEntry> decisions(ActionType type, Instant since) {
            throw new UnsupportedOperationException("not what this test is about");
        }

        @Override
        public com.cka.provider.ProviderHealth checkHealth() {
            return com.cka.provider.ProviderHealth.up("in memory");
        }
    }
}
