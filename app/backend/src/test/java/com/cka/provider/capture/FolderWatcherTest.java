package com.cka.provider.capture;

import static org.assertj.core.api.Assertions.assertThat;

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
        public List<String> list() {
            listCalls++;
            if (!reachable) {
                throw new IllegalStateException("folder unreachable");
            }
            return List.copyOf(files.keySet());
        }

        @Override
        public String read(String name) {
            return files.get(name);
        }

        @Override
        public ProviderHealth checkHealth() {
            return reachable ? ProviderHealth.up("fake folder") : ProviderHealth.down("fake folder");
        }
    }

    private final FakeFolder folder = new FakeFolder();
    private final List<CapturedContent> captured = new ArrayList<>();
    private final FolderWatcher watcher = new FolderWatcher(folder, Duration.ofMillis(20));

    private void aMeetingCalled(String name) {
        folder.put(name + " - Recording.mp4", "");
        folder.put(name + " - Transcript", "Priya: #142 is merged, so it can be closed.");
        folder.put(name + " - Notes by Gemini", "The team agreed #142 is done.");
    }

    @Test
    @DisplayName("A meeting's recording, transcript and notes become one captured item")
    void aMeetingsFilesBecomeOneCapturedItem() {
        aMeetingCalled("Sprint Refinement (2026-11-08 14:32 GMT)");

        watcher.pollOnce(captured::add);

        assertThat(captured).hasSize(1);
        var content = captured.getFirst();
        assertThat(content.title()).isEqualTo("Sprint Refinement (2026-11-08 14:32 GMT)");
        assertThat(content.transcript()).isEqualTo("Priya: #142 is merged, so it can be closed.");
        assertThat(content.platformSummary()).isEqualTo("The team agreed #142 is done.");
    }

    @Test
    @DisplayName("The content type is the meeting's own name, so an unknown one gets the fallback prompt")
    void theContentTypeIsTheMeetingName() {
        // PRD 7.6 keys prompts by content type and PromptStore already falls back to General for
        // a type it has never seen — so the watcher names the type and decides nothing. A
        // mapping here would be a second place to add a meeting type, which 7.6 says is a row.
        aMeetingCalled("Sprint Refinement (2026-11-08 14:32 GMT)");

        watcher.pollOnce(captured::add);

        assertThat(captured.getFirst().contentType()).isEqualTo("Sprint Refinement");
    }

    @Test
    @DisplayName("A meeting name containing brackets keeps them in its content type")
    void aMeetingNameContainingBracketsKeepsThem() {
        // Meet's timestamp is the *last* parenthesised group, not the first, and meeting names
        // carry brackets of their own: "Planning (Q4)", "1:1 (Priya)". Taking the first bracket
        // turns "Planning (Q4)" into "Planning" — a different content type, and so a different
        // prompt, decided by where someone happened to put a bracket in a calendar invite.
        aMeetingCalled("Planning (Q4) (2026-11-08 14:32 GMT)");

        watcher.pollOnce(captured::add);

        assertThat(captured.getFirst().contentType()).isEqualTo("Planning (Q4)");
    }

    @Test
    @DisplayName("A meeting named only by its timestamp still has a content type")
    void aMeetingNamedOnlyByItsTimestampStillHasAContentType() {
        // CapturedContent.contentType is @NonNull and an empty one is no better than a null:
        // nothing to key a prompt by and nothing to show a person. The whole title stands in,
        // and PromptStore's fallback handles a type it has never seen.
        aMeetingCalled("(2026-11-08 14:32 GMT)");

        watcher.pollOnce(captured::add);

        assertThat(captured.getFirst().contentType()).isNotBlank();
    }

    @Test
    @DisplayName("A meeting with no transcript is not handed on at all")
    void aMeetingWithNoTranscriptIsNotHandedOn() {
        // The transcript is the thing being summarised. Publishing a recording without one would
        // start an action decision chain the LLM provider cannot do anything with, and the
        // failure would surface two stages away from the folder that caused it.
        folder.put("Sprint Refinement (2026-11-08 14:32 GMT) - Recording.mp4", "");

        watcher.pollOnce(captured::add);

        assertThat(captured).isEmpty();
    }

    @Test
    @DisplayName("A meeting with no notes is still handed on, with no platform summary")
    void aMeetingWithNoNotesIsStillHandedOn() {
        // Notes are Gemini's and may be switched off for the account; the transcript is enough.
        // CapturedContent.platformSummary is nullable for exactly this case.
        folder.put("Stand-up (2026-11-08 09:02 GMT) - Transcript", "Tom: nothing blocking.");

        watcher.pollOnce(captured::add);

        assertThat(captured).hasSize(1);
        assertThat(captured.getFirst().platformSummary()).isNull();
    }

    @Test
    @DisplayName("A meeting already handed on is not handed on again")
    void aMeetingAlreadyHandedOnIsNotHandedOnAgain() {
        // Every poll sees the whole folder, so without this the same recording starts a new
        // action decision chain on every interval and comments on every ticket again.
        aMeetingCalled("Sprint Refinement (2026-11-08 14:32 GMT)");

        watcher.pollOnce(captured::add);
        watcher.pollOnce(captured::add);
        watcher.pollOnce(captured::add);

        assertThat(captured).hasSize(1);
    }

    @Test
    @DisplayName("A meeting that appears after the first poll is handed on when it appears")
    void aMeetingThatAppearsLaterIsHandedOn() {
        aMeetingCalled("Sprint Refinement (2026-11-08 14:32 GMT)");
        watcher.pollOnce(captured::add);

        aMeetingCalled("Retro (2026-11-08 16:00 GMT)");
        watcher.pollOnce(captured::add);

        assertThat(captured).extracting(CapturedContent::contentType).containsExactly("Sprint Refinement", "Retro");
    }

    @Test
    @DisplayName("A transcript that arrives after its recording is waited for, then handed on once")
    void aTranscriptArrivingLateIsWaitedForThenHandedOnOnce() {
        // Meet writes the recording first and the transcript minutes later, so the folder is
        // genuinely incomplete for a while. The incomplete meeting must not be remembered as
        // handed on, or the transcript would never be picked up.
        folder.put("Sprint Refinement (2026-11-08 14:32 GMT) - Recording.mp4", "");
        watcher.pollOnce(captured::add);
        assertThat(captured).isEmpty();

        folder.put("Sprint Refinement (2026-11-08 14:32 GMT) - Transcript", "Priya: #142 is done.");
        watcher.pollOnce(captured::add);
        watcher.pollOnce(captured::add);

        assertThat(captured).hasSize(1);
        assertThat(captured.getFirst().transcript()).isEqualTo("Priya: #142 is done.");
    }

    @Test
    @DisplayName("The source link points at the recording, not the transcript")
    void theSourceLinkPointsAtTheRecording() {
        // PRD: the note links back to the recording, which is what a person wants to open.
        aMeetingCalled("Sprint Refinement (2026-11-08 14:32 GMT)");

        watcher.pollOnce(captured::add);

        assertThat(captured.getFirst().sourceUri()).contains("Recording");
    }

    @Test
    @DisplayName("A folder that cannot be listed leaves the watcher reporting, not throwing")
    void aFolderThatCannotBeListedDoesNotThrow() {
        // A poll runs on a timer with nothing to catch what it throws, and Drive being briefly
        // unreachable is ordinary. The next poll must still happen.
        aMeetingCalled("Sprint Refinement (2026-11-08 14:32 GMT)");
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
        aMeetingCalled("Sprint Refinement (2026-11-08 14:32 GMT)");

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
        aMeetingCalled("Doomed (2026-11-08 10:00 GMT)");
        aMeetingCalled("Fine (2026-11-08 11:00 GMT)");

        watcher.pollOnce(content -> {
            if (content.title().startsWith("Doomed")) {
                throw new IllegalStateException("the bus refused it");
            }
            captured.add(content);
        });

        assertThat(captured).extracting(CapturedContent::contentType).containsExactly("Fine");
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

        aMeetingCalled("Sprint Refinement (2026-11-08 14:32 GMT)");

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
