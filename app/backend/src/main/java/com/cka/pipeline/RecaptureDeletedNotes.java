package com.cka.pipeline;

import com.cka.provider.capture.CapturedItems;
import com.cka.provider.knowledge.KnowledgeProvider;
import java.time.Duration;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Deleting a note asks for the meeting again.
 *
 * <p>The vault is a folder of files whose whole point is that a person can open and edit it, so
 * deleting a note is the obvious way to say "do that one again" — and without this it is the one
 * edit that does nothing, because the capture record still says the meeting was handled. That
 * record and the vault would then disagree, with the invisible one winning.
 *
 * <p>Here rather than inside either provider. The capture record knows nothing about where a
 * note went and the vault knows nothing about what was captured; reconciling them is a policy
 * about the pipeline as a whole, which is what this package is for. Neither provider gains a
 * dependency on the other.
 *
 * <p><strong>One direction only.</strong> A note whose record is missing is left alone — that is
 * a note somebody wrote themselves, or one left behind by a capture record cleared some other
 * way, and deleting it would be CKA tidying away a person's file on a guess.
 */
@Slf4j
@RequiredArgsConstructor
public class RecaptureDeletedNotes {

    private final CapturedItems captured;
    private final KnowledgeProvider knowledge;
    private final Duration interval;

    private final ScheduledExecutorService checking = Executors.newSingleThreadScheduledExecutor(runnable -> {
        var thread = new Thread(runnable, "cka-recapture-deleted");
        thread.setDaemon(true);
        return thread;
    });

    /**
     * On its own schedule rather than inside the watcher's poll, so that neither has to know the
     * other exists and a slow vault scan cannot delay a poll.
     */
    public void start() {
        checking.scheduleWithFixedDelay(this::reconcileOnce, 0, interval.toMillis(), TimeUnit.MILLISECONDS);
        log.info("Deleting a note will capture its meeting again");
    }

    /**
     * Stops looking. Not {@code @PreDestroy}: this object is built with {@code new} by
     * {@code PipelineConfiguration} rather than being a bean, so Spring never calls it — the
     * caller ties it to the context's close instead. A thread outliving its context polls a
     * connection pool that has shut down, once an interval, forever, and Spring DevTools closes
     * a context on every class change.
     */
    public void stop() {
        checking.shutdownNow();
        try {
            // A reconcile in flight would otherwise go on forgetting rows while the next
            // context's watcher is already reading them.
            checking.awaitTermination(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Forgets every captured meeting whose note was in the store and no longer is.
     *
     * <p><strong>Was</strong>, and that word is the whole of it. A meeting is remembered as
     * captured the moment it is handed on, and its note is written only after summarising — so
     * for tens of seconds every new meeting is captured with no note. Reading that as a deletion
     * forgot the meeting, the next poll captured it again, and that wrote a second note while
     * the cycle repeated: a vault filling with timestamped copies of one meeting, each paid for.
     * So a note has to be seen before its absence means anything, and the sighting is recorded
     * beside the capture so it survives the restarts DevTools performs. The cost is a note
     * deleted within one interval of being written, before it was ever seen, which is not
     * noticed — the cheap direction to be wrong in.
     *
     * <p>Nothing thrown, and nothing forgotten when the store cannot be read: an exception from
     * {@code storedTitles} means "I do not know what I have", and treating that as "I have
     * nothing" would clear every record of capture and re-summarise the entire folder — the most
     * expensive possible reading of a temporary failure.
     */
    void reconcileOnce() {
        try {
            var titles = captured.allTitles();
            if (titles.isEmpty()) {
                // Nothing to reconcile, so the store is not read — and a vault that does not
                // exist yet, before the first note, is not reported as a failure every interval.
                return;
            }
            var stored = knowledge.storedTitles();
            var confirmed = captured.confirmedStored();
            for (var title : titles) {
                if (stored.contains(title)) {
                    if (!confirmed.contains(title)) {
                        captured.confirmStored(title);
                    }
                } else if (confirmed.contains(title)) {
                    captured.forget(title);
                    log.info("\"{}\" is no longer in the knowledge store, so it will be captured again", title);
                }
            }
        } catch (Exception e) {
            // The root cause, not the wrapper: "Failed to obtain JDBC Connection" says nothing
            // about why, and the sentence underneath it usually does.
            // A cause can be an Error, so the walk is over Throwable.
            Throwable cause = e;
            while (cause.getCause() != null) {
                cause = cause.getCause();
            }
            log.warn(
                    "Could not reconcile the knowledge store against what was captured: {}",
                    cause == e ? e.getMessage() : e.getMessage() + " — " + cause.getMessage());
        }
    }
}
