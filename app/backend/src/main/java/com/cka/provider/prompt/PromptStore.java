package com.cka.provider.prompt;

import com.cka.core.Prompt;
import com.cka.provider.Provider;
import java.util.List;

/** Stores the summary prompts, versioned, keyed by content type (PRD 7.6). */
public interface PromptStore extends Provider {

    /**
     * The version to summarise with right now: the newest one for {@code contentType}, or the
     * newest {@link Prompt#FALLBACK_CONTENT_TYPE} prompt if that type has none of its own.
     *
     * <p>Returns a prompt rather than an {@code Optional} because the Summarise stage has
     * nothing to do without one — a captured item with no usable prompt is a broken
     * installation, not a case to branch on.
     */
    Prompt current(String contentType);

    /**
     * Writes the next version of a content type's prompt, and returns it. Creating a prompt and
     * editing one are the same operation: the first write is version 1, and every write after it
     * appends rather than replaces, which is what makes a version recorded against a summary
     * still retrievable later.
     */
    Prompt save(String contentType, String instructions);

    /** Every version of one content type's prompt, newest first. Empty if it has none. */
    List<Prompt> versions(String contentType);
}
