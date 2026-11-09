package com.cka.pipeline;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.cka.provider.capture.CapturedItems;
import com.cka.provider.knowledge.KnowledgeProvider;
import java.time.Duration;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Deleting a note from the knowledge store asks for the meeting again")
class RecaptureDeletedNotesTest {

    private final Set<String> remembered = new HashSet<>();
    private final Set<String> confirmed = new HashSet<>();
    private final Set<String> inTheStore = new HashSet<>();

    private final CapturedItems captured = new CapturedItems() {
        @Override
        public boolean alreadyCaptured(String title) {
            return remembered.contains(title);
        }

        @Override
        public void remember(String title) {
            remembered.add(title);
        }

        @Override
        public Set<String> allTitles() {
            return Set.copyOf(remembered);
        }

        @Override
        public void forget(String title) {
            remembered.remove(title);
            confirmed.remove(title);
        }

        @Override
        public void confirmStored(String title) {
            if (remembered.contains(title)) {
                confirmed.add(title);
            }
        }

        @Override
        public Set<String> confirmedStored() {
            return Set.copyOf(confirmed);
        }
    };

    private final KnowledgeProvider knowledge = mock(KnowledgeProvider.class);

    {
        when(knowledge.storedTitles()).thenAnswer(_ -> Set.copyOf(inTheStore));
    }

    private final RecaptureDeletedNotes reconciler =
            new RecaptureDeletedNotes(captured, knowledge, Duration.ofMinutes(1));

    @Test
    @DisplayName("A meeting whose note has been deleted is forgotten, so the next poll captures it")
    void aMeetingWhoseNoteWasDeletedIsForgotten() {
        remembered.addAll(Set.of("Sprint Refinement", "Retro"));
        inTheStore.addAll(Set.of("Sprint Refinement", "Retro"));
        reconciler.reconcileOnce();

        inTheStore.remove("Sprint Refinement");
        reconciler.reconcileOnce();

        assertThat(remembered).containsExactly("Retro");
    }

    @Test
    @DisplayName("A meeting whose note has not been written yet is not mistaken for a deleted one")
    void aMeetingWhoseNoteIsNotWrittenYetIsKept() {
        // The failure this prevents filled a real vault: a meeting is remembered when it is
        // handed on and its note appears only after summarising, so a reconcile in between saw
        // no note, forgot the meeting, and the next poll captured it again — another summary,
        // another timestamped note, on every poll.
        remembered.add("Sprint Refinement");

        reconciler.reconcileOnce();
        reconciler.reconcileOnce();

        assertThat(remembered).containsExactly("Sprint Refinement");
    }

    @Test
    @DisplayName("Stopping it stops the checking, so no thread outlives its context")
    void stoppingItStopsTheChecking() throws InterruptedException {
        // The failure this prevents was visible in a running stack: the reconciler is built with
        // new rather than being a bean, so when DevTools closed the context on a class change
        // the old thread kept polling a connection pool that had shut down — "Failed to obtain
        // JDBC Connection", once an interval, forever, and another thread on the next change.
        var fast = new RecaptureDeletedNotes(captured, knowledge, Duration.ofMillis(20));
        remembered.add("Sprint Refinement");
        confirmed.add("Sprint Refinement");

        fast.start();
        Thread.sleep(150);
        assertThat(remembered).as("it is running").isEmpty();

        fast.stop();
        remembered.add("Retro");
        confirmed.add("Retro");
        Thread.sleep(150);

        assertThat(remembered).as("nothing reconciled after stop").containsExactly("Retro");
    }

    @Test
    @DisplayName("A store that cannot be read forgets nothing")
    void aStoreThatCannotBeReadForgetsNothing() {
        // "I cannot tell you what I have" must not be read as "I have nothing": that would clear
        // every record of capture and re-summarise the whole folder, at a real cost per meeting,
        // because a vault was briefly unreadable.
        remembered.addAll(Set.of("Sprint Refinement", "Retro"));
        confirmed.addAll(Set.of("Sprint Refinement", "Retro"));
        when(knowledge.storedTitles()).thenThrow(new IllegalStateException("the vault is unreachable"));

        reconciler.reconcileOnce();

        assertThat(remembered).containsExactlyInAnyOrder("Sprint Refinement", "Retro");
    }

    @Test
    @DisplayName("With nothing captured the store is not read, so a vault not yet created is no failure")
    void withNothingCapturedTheStoreIsNotRead() {
        reconciler.reconcileOnce();

        verify(knowledge, never()).storedTitles();
    }
}
