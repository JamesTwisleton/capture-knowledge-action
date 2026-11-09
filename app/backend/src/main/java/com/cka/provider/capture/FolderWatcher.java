package com.cka.provider.capture;

import com.cka.core.CapturedContent;
import com.cka.provider.ProviderHealth;
import jakarta.annotation.PreDestroy;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * The folder watcher (T09): looks at a folder on an interval and hands on each new meeting it
 * finds complete, exactly once.
 *
 * <p>Polling rather than push notifications, because a push subscription needs a publicly
 * reachable webhook and the MVP runs on somebody's laptop (PRD 7.2).
 *
 * <p>What it knows about Google Meet is one thing: Meet names a meeting's files alike, with a
 * suffix saying which is which. Grouping by that shared prefix is the whole of the mechanism.
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "cka.provider", name = "capture", havingValue = "google-drive")
public class FolderWatcher implements ContentListener {

    /**
     * The suffixes Meet appends, longest first so that stripping finds the most specific match.
     * Written with an ASCII hyphen and matched against a name whose dashes have been normalised
     * to one — see {@link #normalised}. They are a vendor's naming convention rather than an
     * API, so re-check them against a real recording; the transcript one is the only one that
     * decides whether a meeting is usable at all.
     */
    private static final List<String> TRANSCRIPT_SUFFIXES = List.of(" - Transcript");

    private static final List<String> NOTES_SUFFIXES = List.of(" - Notes by Gemini", " - Notes");

    private static final List<String> RECORDING_SUFFIXES = List.of(" - Recording.mp4", " - Recording");

    /**
     * Where Meet's own timestamp begins, in the two forms a real folder has shown:
     * "Sprint Refinement started 2026/06/27 21:43 BST" and "Meeting Jun 27, 2026 at 21:43 BST".
     * Cutting at the date — rather than at " at ", which leaves the date behind — is what leaves
     * the name somebody actually gave the meeting.
     */
    private static final Pattern MEET_TIMESTAMP =
            Pattern.compile("\\s+(started\\s+)?(\\d{4}/\\d{1,2}/\\d{1,2}|[A-Z][a-z]{2}\\s+\\d{1,2},\\s*\\d{4})");

    /**
     * The word Meet uses when nobody named the meeting. Everything after it is a date, so what
     * is left is a placeholder rather than a type, and calling it one would give every unnamed
     * meeting a type of "Meeting".
     */
    private static final List<String> UNNAMED = List.of("Meeting", "Meet");

    /** What Drive prepends to a duplicated file, and prepends again to a duplicate of that. */
    private static final String COPY_PREFIX = "Copy of ";

    private final RecordingFolder folder;

    /** How long after one poll finishes before the next begins. */
    private final Duration interval;

    /**
     * The interval arrives as a {@link Duration} rather than a number of seconds because Boot
     * converts "30s" for free, which keeps the unit in the value instead of in the property's
     * name, and lets a test ask for 20ms without the production path carrying a unit it never
     * uses.
     */
    public FolderWatcher(
            RecordingFolder folder, CapturedItems captured, @Value("${cka.capture.poll-interval}") Duration interval) {
        this.folder = folder;
        this.captured = captured;
        this.interval = interval;
    }

    /**
     * One thread, named so a stack trace or a thread dump says which timer it is. Created here
     * rather than injected, because a shared pool would make this watcher's pace depend on what
     * else was queued on it.
     */
    private final ScheduledExecutorService polling = Executors.newSingleThreadScheduledExecutor(runnable -> {
        var thread = new Thread(runnable, "cka-folder-watcher");
        thread.setDaemon(true);
        return thread;
    });

    /** What has already been handed on, outliving this process — see {@link CapturedItems}. */
    private final CapturedItems captured;

    private volatile PollingStatus status = new PollingStatus(0, 0, null);

    /**
     * Starts looking, and keeps looking until {@link #stop()}. One thread, because a second one
     * would mean two polls walking the same folder and racing to hand on the same meeting —
     * the capture record would stop them publishing twice but only by luck of ordering, and the
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
     * Stops looking. Called by Spring on shutdown, and by a test that does not want a thread
     * outliving it.
     */
    @PreDestroy
    public void stop() {
        polling.shutdownNow();
        try {
            // Briefly, for a poll already in flight, so the next context's watcher does not
            // start while this one is still handing a meeting on. A Drive read is not
            // interruptible, so this can expire first; the primary key absorbs what overlaps.
            polling.awaitTermination(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
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
            // Nothing to iterate, so the poll is over.
            log.warn("Could not read the recordings folder; will try again next poll: {}", e.getMessage());
            status = new PollingStatus(status.polls() + 1, status.captured(), Instant.now());
            return;
        }

        var found = 0;
        for (var meeting : meetings.values()) {
            if (!meeting.couldHaveSpeech()) {
                continue;
            }
            // Per meeting, not around the loop. The consumer is the bus and a publish can fail;
            // one failure must not skip every meeting after it, which — since the failing one
            // keeps its place at the front of every subsequent poll — would stop the folder
            // making progress at all rather than lose one recording.
            //
            // The capture record is read inside it too, because that is a database query: one
            // thrown out of this method cancels the timer, and the watcher stops for good
            // without saying so.
            try {
                if (captured.alreadyCaptured(meeting.title())) {
                    continue;
                }
                var speech = speechOf(meeting);
                if (speech == null) {
                    // Either the transcript file exists but is still being written, or the notes
                    // document has no transcript tab in it yet. Not remembered either way, so
                    // the next poll tries again.
                    continue;
                }
                onContent.accept(contentFor(meeting, speech));
                // After the consumer returns, so a meeting that failed to be handed on is tried
                // again next poll. It retries forever if it always fails, which is the right way
                // round: a recording nobody ever processes is worse than a log line every poll.
                captured.remember(meeting.title());
                found++;
            } catch (Exception e) {
                log.warn("Could not hand on \"{}\"; will try again next poll: {}", meeting.title(), e.getMessage());
            }
        }
        status = new PollingStatus(status.polls() + 1, status.captured() + found, Instant.now());
    }

    /**
     * What was said, and the platform's own summary of it, from whichever of Meet's two shapes
     * this folder happens to use.
     *
     * <p>The first is a {@code – Transcript} file beside the notes. The second is the one a real
     * folder turned out to hold: <em>no</em> transcript file at all, because Gemini writes one
     * document with a "Meeting records" tab and a "Transcript" tab, which Drive's text export
     * flattens into a single stream — so the notes document is the transcript as well. A watcher
     * that only knew the first shape would wait for a file that is never coming.
     *
     * <p>Null when neither yields speech, which the caller treats as "not ready yet" rather than
     * as an error: a transcript file is created before it is written, and a notes document gains
     * its transcript tab after the meeting ends.
     */
    private Speech speechOf(Meeting meeting) {
        if (meeting.transcript() != null) {
            var transcript = GeminiNotes.cleaned(folder.read(meeting.transcript()));
            if (!transcript.isBlank()) {
                // The notes parsed even here, because Gemini's document carries the transcript as
                // a second tab — read whole, the platform's summary would be the transcript again.
                return new Speech(
                        transcript,
                        meeting.notes() == null
                                ? null
                                : GeminiNotes.parse(folder.read(meeting.notes()))
                                        .summary());
            }
        }
        if (meeting.notes() != null) {
            var notes = GeminiNotes.parse(folder.read(meeting.notes()));
            if (notes.hasTranscript()) {
                return new Speech(notes.transcript(), notes.summary());
            }
        }
        return null;
    }

    /**
     * A meeting's words, and whatever the platform made of them. A blank summary is none at all,
     * so the note does not gain an empty "platform's own summary" section for a notes document
     * that could not be read or held nothing above its transcript.
     */
    private record Speech(String transcript, String summary) {
        Speech {
            if (summary != null && summary.isBlank()) {
                summary = null;
            }
        }
    }

    private CapturedContent contentFor(Meeting meeting, Speech speech) {
        // The recording's own link where there is one, because that is what a person opens from
        // the note; the transcript's where Meet produced no recording.
        var source = meeting.anyFile();
        return CapturedContent.builder()
                .sourceUri(source.uri())
                .title(meeting.title())
                .contentType(contentTypeOf(meeting.title()))
                .transcript(speech.transcript())
                .platformSummary(speech.summary())
                .build();
    }

    /**
     * The content type is the meeting's own name, and nothing here maps it to anything. PRD 7.6
     * keys prompts by content type and lets a team add one by adding a row, and
     * {@code PromptStore.current} already falls back to the General prompt for a type it has
     * never seen — so a table of known meeting names here would be a second place to add a
     * meeting type, and the one that silently wins.
     *
     * <p>The name is the title with Meet's timestamp removed: "Sprint Refinement started
     * 2026/11/08 14:32 GMT" is one meeting, but the type it shares with every other refinement is
     * "Sprint Refinement". Brackets are left alone — "Planning (Q4)" is a name, not a timestamp.
     *
     * <p>A title with no name left in it is {@link CapturedContent#UNKNOWN_CONTENT_TYPE} rather
     * than empty, since {@code contentType} is {@code @NonNull} and an empty one gives a prompt
     * nothing to key on.
     */
    private String contentTypeOf(String title) {
        // Drive's own prefix when somebody duplicates a file, not part of what the meeting was
        // called — and without removing it the placeholder check below sees "Copy of Meeting"
        // rather than "Meeting", so a nameless meeting quietly acquires a content type.
        var name = title.trim();
        while (name.startsWith(COPY_PREFIX)) {
            name = name.substring(COPY_PREFIX.length()).trim();
        }
        var timestamp = MEET_TIMESTAMP.matcher(name);
        if (timestamp.find()) {
            name = name.substring(0, timestamp.start()).trim();
        }
        return name.isEmpty() || UNNAMED.contains(name) ? CapturedContent.UNKNOWN_CONTENT_TYPE : name;
    }

    /**
     * Groups the folder's files into meetings by the title they share. Insertion-ordered, so a
     * poll hands meetings on in the order the folder lists them — which keeps a test readable and
     * costs nothing, while promising nothing about chronology that the folder does not.
     */
    private Map<String, Meeting> meetingsIn(List<FolderFile> files) {
        Map<String, Meeting> meetings = new LinkedHashMap<>();
        for (var file : files) {
            classify(meetings, file, TRANSCRIPT_SUFFIXES, Meeting::withTranscript);
            classify(meetings, file, NOTES_SUFFIXES, Meeting::withNotes);
            classify(meetings, file, RECORDING_SUFFIXES, Meeting::withRecording);
        }
        return meetings;
    }

    /**
     * Dashes, flattened to one kind. Meet separates a meeting's name from the file's role with an
     * <em>en</em> dash — "Meeting started 2026/06/27 21:43 BST – Notes by Gemini", U+2013 — and
     * matching an ASCII hyphen against that finds nothing at all, silently, because a file that
     * matches no suffix is simply not part of any meeting. Normalising rather than writing the
     * en dash into the constants keeps them readable and accepts either, which matters because
     * the character is a vendor's choice and nothing promises it will not change again.
     *
     * <p>One character for one, so an index into the normalised name is an index into the
     * original, and the title keeps the dash the folder actually used.
     */
    private String normalised(String name) {
        return name.replace('\u2013', '-').replace('\u2014', '-');
    }

    private void classify(
            Map<String, Meeting> meetings,
            FolderFile file,
            List<String> suffixes,
            BiFunction<Meeting, FolderFile, Meeting> with) {
        var comparable = normalised(file.name());
        for (var suffix : suffixes) {
            if (comparable.endsWith(suffix)) {
                var title = file.name().substring(0, file.name().length() - suffix.length());
                var meeting = meetings.computeIfAbsent(title, t -> new Meeting(t, null, null, null));
                meetings.put(title, with.apply(meeting, file));
                return;
            }
        }
    }

    /** One meeting's files, as far as the folder has them. */
    private record Meeting(String title, FolderFile transcript, FolderFile notes, FolderFile recording) {

        Meeting withTranscript(FolderFile file) {
            return new Meeting(title, file, notes, recording);
        }

        Meeting withNotes(FolderFile file) {
            return new Meeting(title, transcript, file, recording);
        }

        Meeting withRecording(FolderFile file) {
            return new Meeting(title, transcript, notes, file);
        }

        /** Whether there is anything here that might yield speech, before reading any of it. */
        boolean couldHaveSpeech() {
            return transcript != null || notes != null;
        }

        /**
         * What the note links back to: the recording if there is one, since that is what a
         * person wants to open, and otherwise whichever document the meeting does have.
         */
        FolderFile anyFile() {
            return recording != null ? recording : (transcript != null ? transcript : notes);
        }
    }
}
