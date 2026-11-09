package com.cka.provider.capture;

import java.time.Instant;

/**
 * What the folder watcher has been doing, for the activity log (T16) to show that CKA is polling
 * and has found something — the difference between "working" and "idle" to someone watching.
 *
 * <p>On the implementation and not on {@code ContentListener}: a webhook receiver has no polls to
 * count, and T09 is the only listener that does.
 *
 * @param polls        how many times the folder has been looked at
 * @param captured     how many meetings have been handed on
 * @param lastPolledAt when the folder was last looked at, or {@code null} before the first poll
 */
public record PollingStatus(int polls, int captured, Instant lastPolledAt) {}
