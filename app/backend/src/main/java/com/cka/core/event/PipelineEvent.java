package com.cka.core.event;

import java.util.UUID;

/**
 * The event payload: everything that travels on the event bus is one of these (PRD 7.1).
 *
 * <p>Sealed, so the set of events is the PRD's set and no other, and a {@code switch} over
 * them is checked for exhaustiveness by the compiler — add an event here and every switch
 * that must handle it stops compiling until it does.
 *
 * <p>Every event carries the {@link #chainId()} of the action decision chain it belongs to,
 * minted when content is captured and copied forward by each stage, so any action can be
 * traced back to the recording it came from (PRD 7.8).
 */
public sealed interface PipelineEvent
        permits ContentCaptured, ContentSummarised, KnowledgeStored, ActionProposed, ActionApplied, ActionFailed {

    UUID chainId();
}
