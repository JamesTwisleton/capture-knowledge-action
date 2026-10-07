package com.cka.testdouble;

import com.cka.core.CapturedContent;
import com.cka.core.PageReference;
import com.cka.core.Summary;
import com.cka.provider.ProviderHealth;
import com.cka.provider.knowledge.KnowledgeProvider;

import java.util.LinkedHashMap;
import java.util.Map;

/** Keeps each stored summary in memory, keyed by a page id derived from the content's title. */
public class InMemoryKnowledgeProvider implements KnowledgeProvider {

    private final Map<PageReference, Summary> pages = new LinkedHashMap<>();

    @Override
    public PageReference store(CapturedContent content, Summary summary) {
        var page = new PageReference(content.title() + ".md", "memory://" + content.title() + ".md");
        pages.put(page, summary);
        return page;
    }

    public Map<PageReference, Summary> pages() {
        return Map.copyOf(pages);
    }

    @Override
    public ProviderHealth checkHealth() {
        return ProviderHealth.up("in memory");
    }
}
