package com.cka.provider.capture;

import java.time.Instant;

/**
 * A meeting that was handed on and whose note has never been seen in the knowledge store: the
 * LLM was off, summarising or storing failed, or the event was lost across a restart. Which of
 * those is not recorded, because nothing upstream reports it; how long ago is, and is what lets
 * a person tell one still being summarised from one that never will be.
 */
public record UnstoredCapture(String title, Instant capturedAt) {}
