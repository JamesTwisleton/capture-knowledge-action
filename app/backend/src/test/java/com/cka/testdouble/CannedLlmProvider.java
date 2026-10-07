package com.cka.testdouble;

import com.cka.core.CapturedContent;
import com.cka.core.Provenance;
import com.cka.core.Summary;
import com.cka.provider.ProviderHealth;
import com.cka.provider.llm.LlmProvider;

/** Summarises anything as "Summary of" its title, and says so in its provenance. */
public class CannedLlmProvider implements LlmProvider {

    public static final Provenance PROVENANCE = new Provenance("canned-llm", "canned-model");

    @Override
    public Summary summarise(CapturedContent content) {
        return new Summary("Summary of " + content.title(), PROVENANCE);
    }

    @Override
    public ProviderHealth checkHealth() {
        return ProviderHealth.up("canned");
    }
}
