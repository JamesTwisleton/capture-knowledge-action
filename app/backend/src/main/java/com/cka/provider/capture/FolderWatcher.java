package com.cka.provider.capture;

import com.cka.core.CapturedContent;
import com.cka.provider.ProviderHealth;
import jakarta.annotation.PreDestroy;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * The folder watcher (T09): looks at a folder on an interval and hands on each new meeting it
 * finds complete, exactly once.
 *
 * <p>Polling rather than push notifications, because a push subscription needs a publicly
 * reachable webhook and the MVP runs on somebody's laptop (PRD 7.2).
 *
 * <p>What it knows about Google Meet is one thing: Meet names a meeting's files alike, with a
 * suffix saying which is which. Grouping by that shared prefix is the whole of the mechanism,
 * and it is why {@link RecordingFolder} deals in names.
 */
@Slf4j
@RequiredArgsConstructor
public class FolderWatcher implements ContentListener {

    /**
     * The suffixes Meet appends, longest first so that stripping finds the most specific match.
     * Re-check these against a real recording before trusting them — they are a vendor's naming
     * convention, not an API, and the transcript one is the only one that matters for a meeting
     * to be usable at all.
     */
    private static final List<String> TRANSCRIPT_SUFFIXES = List.of(" - Transcript");

    private static final List<String> NOTES_SUFFIXES = List.of(" - Notes by Gemini", " - Notes");

    private static final List<String> RECORDING_SUFFIXES = List.of(" - Recording.mp4", " - Recording");

    private final RecordingFolder folder;

    /** How long after one poll finishes before the next begins. */
    private final Duration interval;

    /**
     * One thread, named so a stack trace or a thread dump says which timer it is. Created here
     * rather than injected: nothing else schedules anything yet, and a shared pool would make
     * this watcher's pace depend on what else was queued on it.
     */
    private final ScheduledExecutorService polling = Executors.newSingleThreadScheduledExecutor(runnable -> {
        var thread = new Thread(runnable, "cka-folder-watcher");
        thread.setDaemon(true);
        return thread;
    });

    /**
     * The meetings already handed on, by title. Concurrent because {@link #checkHealth} and the
     * activity log read from whatever thread asks while the poll timer writes.
     *
     * <p>In memory, so a restart re-hands-on everything still in the folder. That is a real
     * limitation and T19's reset is the only thing that currently clears a vault, so it is
     * recorded in AGENTS.md rather than solved here — persisting it belongs with whatever gives
     * the pipeline its idempotency story, not with the thing that reads a directory.
     */
    private final Set<String> handedOn = ConcurrentHashMap.newKeySet();

    private volatile PollingStatus status = new PollingStatus(0, 0, null);

    /**
     * Starts looking, and keeps looking until {@link #stop()}. One thread, because a second one
     * would mean two polls walking the same folder and racing to hand on the same meeting —
     * {@link #handedOn} would stop them publishing twice but only by luck of ordering, and the
     * folder gains nothing from being read twice at once.
     *
     * <p>{@code scheduleWithFixedDelay}, not {@code scheduleAtFixedRate}: the delay is measured
     * from the end of the previous poll, so a slow folder spaces its own polls out instead of
     * queueing up a backlog the executor then runs back to back.
     */
    @Override
    public void listen(Consumer<CapturedContent> onContent) {
        polling.scheduleWithFixedDelay(() -> pollOnce(onContent), 0, interval.toMillis(), TimeUnit.MILLISECONDS);
        log.info("Watching the recordings folder every {}s", interval.toSeconds());
    }

    /**
     * Stops looking. Called by Spring on shutdown once T09's second pass makes this a bean, and
     * by a test that does not want a thread outliving it.
     */
    @PreDestroy
    public void stop() {
        polling.shutdownNow();
    }

    @Override
    public ProviderHealth checkHealth() {
        return folder.checkHealth();
    }

    /** What this watcher has been doing, for the activity log (T16). */
    public PollingStatus status() {
        return status;
    }

    /**
     * One look at the folder. Hands on every meeting that is complete and has not been handed on
     * before.
     *
     * <p>Nothing thrown. A poll runs on a timer with nothing above it to catch anything, and a
     * folder being briefly unreachable is ordinary rather than exceptional — so a failed look is
     * logged and the next one still happens. The alternative is a timer that dies the first time
     * a network blips and a pipeline that goes quiet without saying so.
     */
    void pollOnce(Consumer<CapturedContent> onContent) {
        Map<String, Meeting> meetings;
        try {
            meetings = meetingsIn(folder.list());
        } catch (Exception e) {
            // Nothing to iterate, so the poll is over. Logged rather than thrown: this runs on a
            // timer with nothing above it to catch anything, and a folder being briefly
            // unreachable is ordinary — a timer that dies on the first network blip takes the
            // pipeline quiet with it, and says so only once.
            log.warn("Could not read the recordings folder; will try again next poll: {}", e.getMessage());
            status = new PollingStatus(status.polls() + 1, status.captured(), Instant.now());
            return;
        }

        var found = 0;
        for (var meeting : meetings.values()) {
            if (handedOn.contains(meeting.title()) || meeting.transcript() == null) {
                continue;
            }
            // Per meeting, not around the loop. The consumer is the bus and a publish can fail;
            // one failure must not skip every meeting after it, which — since the failing one
            // keeps its place at the front of every subsequent poll — would stop the folder
            // making progress at all rather than lose one recording.
            try {
                var transcript = folder.read(meeting.transcript());
                if (transcript == null || transcript.isBlank()) {
                    // Meet creates the transcript file before it has finished writing it. Not
                    // remembered, so the next poll tries again.
                    continue;
                }
                onContent.accept(contentFor(meeting, transcript));
                // After the consumer returns, so a meeting that failed to be handed on is tried
                // again next poll. It retries forever if it always fails, which is the right way
                // round: a recording nobody ever processes is worse than a log line every poll.
                handedOn.add(meeting.title());
                found++;
            } catch (Exception e) {
                log.warn("Could not hand on \"{}\"; will try again next poll: {}", meeting.title(), e.getMessage());
            }
        }
        status = new PollingStatus(status.polls() + 1, status.captured() + found, Instant.now());
    }

    /**
     * The content type is the meeting's own name, and nothing here maps it to anything. PRD 7.6
     * keys prompts by content type and lets a team add one by adding a row, and
     * {@code PromptStore.current} already falls back to the General prompt for a type it has
     * never seen — so a table of known meeting names here would be a second place to add a
     * meeting type, and the one that silently wins.
     *
     * <p>The name is the title with Meet's parenthesised timestamp removed: "Sprint Refinement
     * (2026-11-08 14:32 GMT)" is one meeting, but the type it shares with every other refinement
     * is "Sprint Refinement".
     */
    private CapturedContent contentFor(Meeting meeting, String transcript) {
        return CapturedContent.builder()
                .sourceUri(meeting.recording() == null ? meeting.transcript() : meeting.recording())
                .title(meeting.title())
                .contentType(contentTypeOf(meeting.title()))
                .transcript(transcript)
                .platformSummary(meeting.notes() == null ? null : folder.read(meeting.notes()))
                .build();
    }

    /**
     * {@code lastIndexOf}, because Meet's timestamp is the last parenthesised group and a meeting
     * name carries brackets of its own: "Planning (Q4)", "1:1 (Priya)". Taking the first would
     * make the content type — and so the prompt — depend on where somebody put a bracket in a
     * calendar invite.
     *
     * <p>Falls back to the whole title rather than returning nothing, since
     * {@code CapturedContent.contentType} is {@code @NonNull} and an empty one is no better than
     * a null: nothing to key a prompt by, and nothing to show a person.
     */
    private String contentTypeOf(String title) {
        var stamp = title.lastIndexOf(" (");
        var name = (stamp < 0 ? title : title.substring(0, stamp)).trim();
        return name.isEmpty() ? title.trim() : name;
    }

    /**
     * Groups the folder's files into meetings by the title they share. Insertion-ordered, so a
     * poll hands meetings on in the order the folder lists them — which keeps a test readable and
     * costs nothing, while promising nothing about chronology that the folder does not.
     */
    private Map<String, Meeting> meetingsIn(List<String> names) {
        Map<String, Meeting> meetings = new LinkedHashMap<>();
        for (var name : names) {
            classify(meetings, name, TRANSCRIPT_SUFFIXES, Meeting::withTranscript);
            classify(meetings, name, NOTES_SUFFIXES, Meeting::withNotes);
            classify(meetings, name, RECORDING_SUFFIXES, Meeting::withRecording);
        }
        return meetings;
    }

    private void classify(
            Map<String, Meeting> meetings,
            String name,
            List<String> suffixes,
            BiFunction<Meeting, String, Meeting> with) {
        for (var suffix : suffixes) {
            if (name.endsWith(suffix)) {
                var title = name.substring(0, name.length() - suffix.length());
                var meeting = meetings.computeIfAbsent(title, t -> new Meeting(t, null, null, null));
                meetings.put(title, with.apply(meeting, name));
                return;
            }
        }
    }

    /** One meeting's files, as far as the folder has them. */
    private record Meeting(String title, String transcript, String notes, String recording) {

        Meeting withTranscript(String file) {
            return new Meeting(title, file, notes, recording);
        }

        Meeting withNotes(String file) {
            return new Meeting(title, transcript, file, recording);
        }

        Meeting withRecording(String file) {
            return new Meeting(title, transcript, notes, file);
        }
    }
}
