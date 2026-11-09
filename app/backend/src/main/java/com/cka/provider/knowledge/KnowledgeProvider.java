package com.cka.provider.knowledge;

import com.cka.core.CapturedContent;
import com.cka.core.PageReference;
import com.cka.core.Summary;
import com.cka.provider.Provider;
import java.util.Set;

/** Stores, links and indexes knowledge (PRD 7.2). */
public interface KnowledgeProvider extends Provider {

    /** Writes one page for the content and its summary, and says where it went. */
    PageReference store(CapturedContent content, Summary summary);

    /**
     * The titles this store currently holds a page for.
     *
     * <p>Exists so that deleting a page is a way of asking for it again: something has to be
     * able to notice the deletion, and only the store knows what it still has. Titles rather
     * than page references because the title is what survives a provider's own naming — a vault
     * renames a clashing note and a wiki would slugify it, and neither changes what the meeting
     * was called.
     */
    Set<String> storedTitles();
}
