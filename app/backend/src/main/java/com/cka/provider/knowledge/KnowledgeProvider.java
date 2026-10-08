package com.cka.provider.knowledge;

import com.cka.core.CapturedContent;
import com.cka.core.PageReference;
import com.cka.core.Summary;
import com.cka.provider.Provider;

/** Stores, links and indexes knowledge (PRD 7.2). */
public interface KnowledgeProvider extends Provider {

    /** Writes one page for the content and its summary, and says where it went. */
    PageReference store(CapturedContent content, Summary summary);
}
