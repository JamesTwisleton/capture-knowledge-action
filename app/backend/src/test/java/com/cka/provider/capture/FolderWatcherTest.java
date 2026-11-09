package com.cka.provider.capture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import com.cka.core.CapturedContent;
import com.cka.provider.ProviderHealth;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * T09's first pass: the watcher's own logic, over a stand-in folder. What it decides — which
 * files belong to one meeting, which meetings are ready to hand on, which it has already handed
 * on, and what the content type is — is all testable without a Google account, and is where the
 * bugs live. Reaching Drive is the second pass.
 *
 * <p>A hand-written {@link RecordingFolder} rather than a mock: the folder has behaviour worth
 * getting right (files appear between polls, and reading one returns its text), and stubbing
 * that would mean writing the implementation in Mockito.
 */
@DisplayName("The folder watcher turns Meet's files into captured content, once each")
class FolderWatcherTest {

    /** A folder whose contents a test can change between polls. */
    private static final class FakeFolder implements RecordingFolder {
        private final Map<String, String> files = new LinkedHashMap<>();
        private boolean reachable = true;
        private int listCalls;

        void put(String name, String content) {
            files.put(name, content);
        }

        @Override
        public List<FolderFile> list() {
            listCalls++;
            if (!reachable) {
                throw new IllegalStateException("folder unreachable");
            }
            return files.keySet().stream()
                    .map(name -> new FolderFile(name, name, "https://drive.test/" + name, "text/plain"))
                    .toList();
        }

        @Override
        public String read(FolderFile file) {
            return files.get(file.name());
        }

        @Override
        public ProviderHealth checkHealth() {
            return reachable ? ProviderHealth.up("fake folder") : ProviderHealth.down("fake folder");
        }
    }

    /** The simplest thing that honours the contract: remembered titles, in a set. */
    private static final class RememberedInMemory implements CapturedItems {
        private final java.util.Set<String> titles = new java.util.HashSet<>();
        private boolean unreachable;

        @Override
        public boolean alreadyCaptured(String title) {
            if (unreachable) {
                throw new IllegalStateException("database is locked");
            }
            return titles.contains(title);
        }

        @Override
        public void remember(String title) {
            titles.add(title);
        }

        @Override
        public java.util.Set<String> allTitles() {
            return java.util.Set.copyOf(titles);
        }

        @Override
        public void forget(String title) {
            titles.remove(title);
        }

        @Override
        public void confirmStored(String title) {}

        @Override
        public java.util.Set<String> confirmedStored() {
            return java.util.Set.of();
        }

        // A person's, from the API; nothing under test calls them.
        @Override
        public List<UnstoredCapture> unstored() {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean retry(String title) {
            throw new UnsupportedOperationException();
        }
    }

    private final FakeFolder folder = new FakeFolder();
    private final RememberedInMemory remembered = new RememberedInMemory();
    private final List<CapturedContent> captured = new ArrayList<>();
    private final FolderWatcher watcher = new FolderWatcher(folder, remembered, Duration.ofMillis(20));

    private void aMeetingCalled(String name) {
        folder.put(name + " - Recording.mp4", "");
        folder.put(name + " - Transcript", "Priya: #142 is merged, so it can be closed.");
        folder.put(name + " - Notes by Gemini", "The team agreed #142 is done.");
    }

    @Test
    @DisplayName("A meeting's recording, transcript and notes become one captured item")
    void aMeetingsFilesBecomeOneCapturedItem() {
        aMeetingCalled("Sprint Refinement started 2026/11/08 14:32 GMT");

        watcher.pollOnce(captured::add);

        assertThat(captured).hasSize(1);
        var content = captured.getFirst();
        assertThat(content.title()).isEqualTo("Sprint Refinement started 2026/11/08 14:32 GMT");
        assertThat(content.transcript()).isEqualTo("Priya: #142 is merged, so it can be closed.");
        assertThat(content.platformSummary()).isEqualTo("The team agreed #142 is done.");
    }

    @Test
    @DisplayName("The content type is the meeting's own name, so an unknown one gets the fallback prompt")
    void theContentTypeIsTheMeetingName() {
        // PRD 7.6 keys prompts by content type and PromptStore already falls back to General for
        // a type it has never seen — so the watcher names the type and decides nothing. A
        // mapping here would be a second place to add a meeting type, which 7.6 says is a row.
        aMeetingCalled("Sprint Refinement started 2026/11/08 14:32 GMT");

        watcher.pollOnce(captured::add);

        assertThat(captured.getFirst().contentType()).isEqualTo("Sprint Refinement");
    }

    @Test
    @DisplayName("A meeting name containing brackets keeps them in its content type")
    void aMeetingNameContainingBracketsKeepsThem() {
        // Meeting names carry brackets of their own: "Planning (Q4)", "1:1 (Priya)". Cutting at
        // a bracket — which an earlier version did, for a timestamp format Meet never writes —
        // turns "Planning (Q4)" into "Planning": a different content type, and so a different
        // prompt, decided by where someone happened to put a bracket in a calendar invite.
        aMeetingCalled("Planning (Q4) started 2026/11/08 14:32 GMT");

        watcher.pollOnce(captured::add);

        assertThat(captured.getFirst().contentType()).isEqualTo("Planning (Q4)");
    }

    @Test
    @DisplayName("Meet's en dash is recognised as the separator, not just a hyphen")
    void meetsEnDashIsRecognised() {
        // Taken from a real folder: Meet writes "… BST – Notes by Gemini" with U+2013, not the
        // ASCII hyphen. Matching only the hyphen finds nothing and says nothing — a file that
        // matches no suffix is simply not part of any meeting — so the whole watcher would have
        // sat polling a folder full of recordings and captured none of them.
        folder.put("Meeting started 2026/06/27 21:43 BST \u2013 Transcript", "Priya: #142 is done.");
        folder.put("Meeting started 2026/06/27 21:43 BST \u2013 Notes by Gemini", "The team agreed.");

        watcher.pollOnce(captured::add);

        assertThat(captured).hasSize(1);
        assertThat(captured.getFirst().transcript()).isEqualTo("Priya: #142 is done.");
        assertThat(captured.getFirst().platformSummary()).isEqualTo("The team agreed.");
        // The title keeps the dash the folder actually used, rather than the one matching used.
        assertThat(captured.getFirst().title()).isEqualTo("Meeting started 2026/06/27 21:43 BST");
    }

    @Test
    @DisplayName("Meet's own timestamp forms are stripped, leaving the name somebody gave it")
    void meetsTimestampFormsAreStripped() {
        // Both taken from a real folder: Meet writes "<name> started <date> <tz>" and
        // "<name> <Mon D, YYYY> at <time> <tz>". Neither is the bracketed form the code first
        // assumed, so without this every meeting's type was the whole filename — unique per
        // meeting, and so never matching a stored prompt.
        folder.put("Sprint Refinement started 2026/06/27 21:43 BST \u2013 Transcript", "Priya: done.");
        folder.put("Retro Jun 27, 2026 at 21:43 BST \u2013 Transcript", "Tom: fine.");

        watcher.pollOnce(captured::add);

        assertThat(captured).extracting(CapturedContent::contentType).containsExactly("Sprint Refinement", "Retro");
    }

    @Test
    @DisplayName("A meeting nobody named is Unknown, not \"Meeting\"")
    void aMeetingNobodyNamedIsUnknown() {
        // "Meeting Jun 27, 2026 at 21:43 BST" is what Meet calls an unnamed meeting, so the
        // word left after stripping the date is Meet's placeholder rather than a type. Calling
        // it one would give every unnamed meeting the same meaningless type.
        folder.put("Meeting Jun 27, 2026 at 21:43 BST \u2013 Transcript", "Priya: hello.");

        watcher.pollOnce(captured::add);

        assertThat(captured.getFirst().contentType()).isEqualTo(CapturedContent.UNKNOWN_CONTENT_TYPE);
    }

    @Test
    @DisplayName("A notes document with a transcript tab is the transcript, with no transcript file")
    void aNotesDocumentWithATranscriptTabIsTheTranscript() {
        // The shape a real folder turned out to have: Gemini writes one document with a
        // "Meeting records" tab and a "Transcript" tab, and Drive's export flattens both. There
        // is no transcript file to wait for, and a watcher that waits for one waits forever.
        folder.put("Sprint Refinement started 2026/06/27 21:43 BST \u2013 Notes by Gemini", """
                Jun 27, 2026
                Meeting records Transcript
                Summary
                The team agreed #142 is a duplicate.
                Details
                Jun 27, 2026
                00:00:00
                Priya Raman: One four two is merged.
                """);

        watcher.pollOnce(captured::add);

        assertThat(captured).hasSize(1);
        assertThat(captured.getFirst().transcript()).startsWith("00:00:00").contains("Priya Raman:");
        assertThat(captured.getFirst().platformSummary())
                .contains("The team agreed #142 is a duplicate.")
                .doesNotContain("Priya Raman:");
        assertThat(captured.getFirst().contentType()).isEqualTo("Sprint Refinement");
    }

    @Test
    @DisplayName("A notes document with no transcript tab yet is waited for, not handed on")
    void aNotesDocumentWithNoTranscriptTabIsWaitedFor() {
        folder.put("Stand-up started 2026/06/27 09:02 BST \u2013 Notes by Gemini", "Summary\nNothing blocking.");

        watcher.pollOnce(captured::add);

        assertThat(captured).isEmpty();
    }

    @Test
    @DisplayName("Drive's \"Copy of\" prefix is not part of the meeting's name")
    void drivesCopyOfPrefixIsNotPartOfTheName() {
        // Seen in a real folder: duplicating a file in Drive renames it "Copy of …", and
        // duplicating that gives "Copy of Copy of …". Left in, a nameless meeting reads as
        // "Copy of Meeting" and so misses the placeholder check entirely.
        folder.put("Copy of Meeting started 2026/06/27 21:43 BST \u2013 Transcript", "Priya: hello.");
        folder.put("Copy of Copy of Retro started 2026/06/27 22:00 BST \u2013 Transcript", "Tom: fine.");

        watcher.pollOnce(captured::add);

        assertThat(captured)
                .extracting(CapturedContent::contentType)
                .containsExactly(CapturedContent.UNKNOWN_CONTENT_TYPE, "Retro");
    }

    @Test
    @DisplayName("A meeting with no transcript is not handed on at all")
    void aMeetingWithNoTranscriptIsNotHandedOn() {
        // The transcript is the thing being summarised. Publishing a recording without one would
        // start an action decision chain the LLM provider cannot do anything with, and the
        // failure would surface two stages away from the folder that caused it.
        folder.put("Sprint Refinement started 2026/11/08 14:32 GMT - Recording.mp4", "");

        watcher.pollOnce(captured::add);

        assertThat(captured).isEmpty();
    }

    @Test
    @DisplayName("A meeting with no notes is still handed on, with no platform summary")
    void aMeetingWithNoNotesIsStillHandedOn() {
        // Notes are Gemini's and may be switched off for the account; the transcript is enough.
        // CapturedContent.platformSummary is nullable for exactly this case.
        folder.put("Stand-up started 2026/11/08 09:02 GMT - Transcript", "Tom: nothing blocking.");

        watcher.pollOnce(captured::add);

        assertThat(captured).hasSize(1);
        assertThat(captured.getFirst().platformSummary()).isNull();
    }

    @Test
    @DisplayName("A meeting already handed on is not handed on again")
    void aMeetingAlreadyHandedOnIsNotHandedOnAgain() {
        // Every poll sees the whole folder, so without this the same recording starts a new
        // action decision chain on every interval and comments on every ticket again.
        aMeetingCalled("Sprint Refinement started 2026/11/08 14:32 GMT");

        watcher.pollOnce(captured::add);
        watcher.pollOnce(captured::add);
        watcher.pollOnce(captured::add);

        assertThat(captured).hasSize(1);
    }

    @Test
    @DisplayName("A meeting that appears after the first poll is handed on when it appears")
    void aMeetingThatAppearsLaterIsHandedOn() {
        aMeetingCalled("Sprint Refinement started 2026/11/08 14:32 GMT");
        watcher.pollOnce(captured::add);

        aMeetingCalled("Retro started 2026/11/08 16:00 GMT");
        watcher.pollOnce(captured::add);

        assertThat(captured).extracting(CapturedContent::contentType).containsExactly("Sprint Refinement", "Retro");
    }

    @Test
    @DisplayName("A transcript that arrives after its recording is waited for, then handed on once")
    void aTranscriptArrivingLateIsWaitedForThenHandedOnOnce() {
        // Meet writes the recording first and the transcript minutes later, so the folder is
        // genuinely incomplete for a while. The incomplete meeting must not be remembered as
        // handed on, or the transcript would never be picked up.
        folder.put("Sprint Refinement started 2026/11/08 14:32 GMT - Recording.mp4", "");
        watcher.pollOnce(captured::add);
        assertThat(captured).isEmpty();

        folder.put("Sprint Refinement started 2026/11/08 14:32 GMT - Transcript", "Priya: #142 is done.");
        watcher.pollOnce(captured::add);
        watcher.pollOnce(captured::add);

        assertThat(captured).hasSize(1);
        assertThat(captured.getFirst().transcript()).isEqualTo("Priya: #142 is done.");
    }

    @Test
    @DisplayName("The source link points at the recording, not the transcript")
    void theSourceLinkPointsAtTheRecording() {
        // PRD: the note links back to the recording, which is what a person wants to open.
        aMeetingCalled("Sprint Refinement started 2026/11/08 14:32 GMT");

        watcher.pollOnce(captured::add);

        assertThat(captured.getFirst().sourceUri())
                .as("a link a person can open, not a filename")
                .startsWith("https://")
                .contains("Recording");
    }

    @Test
    @DisplayName("A folder that cannot be listed leaves the watcher reporting, not throwing")
    void aFolderThatCannotBeListedDoesNotThrow() {
        // A poll runs on a timer with nothing to catch what it throws, and Drive being briefly
        // unreachable is ordinary. The next poll must still happen.
        aMeetingCalled("Sprint Refinement started 2026/11/08 14:32 GMT");
        folder.reachable = false;

        watcher.pollOnce(captured::add);

        assertThat(captured).isEmpty();
        folder.reachable = true;
        watcher.pollOnce(captured::add);
        assertThat(captured).hasSize(1);
    }

    @Test
    @DisplayName("Polling status says when it last looked and what it has found")
    void pollingStatusSaysWhatItHasFound() {
        // T16's activity log shows that CKA is polling and has found something, which is the
        // difference between "working" and "idle" to someone watching the screen.
        aMeetingCalled("Sprint Refinement started 2026/11/08 14:32 GMT");

        watcher.pollOnce(captured::add);

        assertThat(watcher.status().polls()).isEqualTo(1);
        assertThat(watcher.status().captured()).isEqualTo(1);
        assertThat(watcher.status().lastPolledAt()).isNotNull();
    }

    @Test
    @DisplayName("One meeting that cannot be handed on does not starve the others")
    void oneBadMeetingDoesNotStarveTheOthers() {
        // The consumer is the bus, and a publish can fail. If one failure aborts the poll, every
        // meeting after it in iteration order is skipped — and skipped again on the next poll,
        // and the next, because the failing one is still there and still first. A folder would
        // silently stop making progress with nothing but a log line to say so.
        aMeetingCalled("Doomed started 2026/11/08 10:00 GMT");
        aMeetingCalled("Fine started 2026/11/08 11:00 GMT");

        watcher.pollOnce(content -> {
            if (content.title().startsWith("Doomed")) {
                throw new IllegalStateException("the bus refused it");
            }
            captured.add(content);
        });

        assertThat(captured).extracting(CapturedContent::contentType).containsExactly("Fine");
    }

    @Test
    @DisplayName("A capture record that cannot be read leaves the watcher reporting, not throwing")
    void aCaptureRecordThatCannotBeReadDoesNotThrow() {
        // The record is a database query, and anything thrown out of a poll cancels the timer:
        // the watcher would stop for good on the first locked database, and say nothing.
        aMeetingCalled("Sprint Refinement started 2026/11/08 14:32 GMT");
        remembered.unreachable = true;

        assertThatCode(() -> watcher.pollOnce(captured::add)).doesNotThrowAnyException();

        remembered.unreachable = false;
        watcher.pollOnce(captured::add);
        assertThat(captured).hasSize(1);
    }

    @Test
    @DisplayName("Beside a transcript file, the notes' own transcript tab is not the platform summary")
    void theNotesTranscriptTabIsNotThePlatformSummary() {
        folder.put("Retro - Transcript", "Priya: #142 is merged.");
        folder.put(
                "Retro - Notes by Gemini",
                "Summary\nThe team agreed #142 is done.\n\n00:00:00\nPriya: #142 is merged.");

        watcher.pollOnce(captured::add);

        assertThat(captured.getFirst().platformSummary()).isEqualTo("Summary\nThe team agreed #142 is done.");
    }

    @Test
    @DisplayName("A transcript file holding only Drive's byte-order mark is not yet speech")
    void aTranscriptOfOnlyAByteOrderMarkIsWaitedFor() {
        folder.put("Retro - Transcript", "\uFEFF");

        watcher.pollOnce(captured::add);

        assertThat(captured).isEmpty();
    }

    @Test
    @DisplayName("Listening polls repeatedly, and stops when told to")
    void listeningPollsRepeatedlyAndStops() throws InterruptedException {
        // The interval is 20ms here, so this is the schedule itself under test rather than a
        // guess about timing: a meeting added *after* listening starts has to be picked up, which
        // only a repeating poll does, and a one-shot schedule would pass every other assertion
        // in this class.
        var arrived = new CountDownLatch(1);
        watcher.listen(content -> {
            captured.add(content);
            arrived.countDown();
        });

        aMeetingCalled("Sprint Refinement started 2026/11/08 14:32 GMT");

        assertThat(arrived.await(5, TimeUnit.SECONDS))
                .as("a meeting added after listening began")
                .isTrue();
        assertThat(folder.listCalls)
                .as("the folder is looked at more than once")
                .isGreaterThan(1);

        watcher.stop();
        var pollsWhenStopped = folder.listCalls;
        Thread.sleep(200);
        assertThat(folder.listCalls)
                .as("no further polls after stop, so a shutdown leaves no thread behind")
                .isEqualTo(pollsWhenStopped);
    }

    @Test
    @DisplayName("Health is the folder's own answer")
    void healthIsTheFoldersOwnAnswer() {
        assertThat(watcher.checkHealth().up()).isTrue();

        folder.reachable = false;

        assertThat(watcher.checkHealth().up()).isFalse();
    }
}
