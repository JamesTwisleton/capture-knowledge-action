package com.cka.testsupport;

import lombok.Builder;
import lombok.NonNull;

/**
 * A stand-in for a tracker's native work item — deliberately not shaped like
 * {@link com.cka.core.WorkItem}, so {@link TicketMapper} has real converting to do.
 *
 * @param key       the tracker's id, for example "CKA-7"
 * @param headline  the title
 * @param kind      the tracker's own type label, for example "Epic" or "Defect"
 * @param epicKey   the key of the epic it sits under, or {@code null}
 */
@Builder
public record Ticket(
        @NonNull String key,
        @NonNull String headline,
        @NonNull String kind,
        String epicKey) {}
