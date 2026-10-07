package com.cka.core;

/**
 * Stored, linkable knowledge: the normalised output of the Knowledge stage (PRD 7.1), and
 * what mention detection reads.
 *
 * @param page    where it was stored
 * @param content what was captured, including the transcript
 * @param summary what the LLM provider made of it
 */
public record Knowledge(PageReference page, CapturedContent content, Summary summary) {
}
