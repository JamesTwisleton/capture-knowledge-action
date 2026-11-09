package com.cka.provider.capture;

import java.util.Set;

/**
 * What has already been handed on, so a recording is captured once and not once per poll.
 *
 * <p>{@code capture/AGENTS.md} makes "exactly once" the listener's own job, and the reason is
 * concrete: a re-delivered recording starts a second action decision chain, pays for a second
 * summary, and comments on every work item again. It outlives the process, because DevTools
 * restarts the backend on every class change.
 *
 * <p>An interface rather than a repository used directly, because the watcher should not know
 * whether this is a table, a file or a set — and because a test wants a trivial one.
 */
public interface CapturedItems {

    /** Whether this meeting has been handed on before. */
    boolean alreadyCaptured(String title);

    /**
     * Records that it has been, now.
     *
     * <p>Called after the consumer returns rather than before, so a meeting that failed to be
     * handed on is tried again next poll. Recording one twice must therefore be harmless: across a
     * DevTools restart the old context's poll and the new one's can both read a title as new
     * before either writes it, and the second write must not fail the poll it is in.
     */
    void remember(String title);

    /** Everything remembered, so something else can work out what is no longer wanted. */
    Set<String> allTitles();

    /**
     * Records that this meeting's note has been seen in the knowledge store. Kept apart from
     * {@link #remember} because the two happen at different times — a meeting is remembered when
     * it is handed on, and its note exists only once summarising has finished — and a note that
     * has not been written yet must not read as one that was deleted. Doing nothing for a title
     * that is not remembered, or already confirmed, is correct for the same reason as
     * {@link #forget}.
     */
    void confirmStored(String title);

    /** The remembered meetings whose note has been seen in the store at least once. */
    Set<String> confirmedStored();

    /**
     * Drops one, so the next poll treats the meeting as new again. Doing nothing for a title
     * that was never remembered is correct rather than an error: the caller is expressing what
     * should be true afterwards, not asserting what was true before.
     */
    void forget(String title);
}
