package com.cka.testdouble;

import com.cka.core.Knowledge;
import com.cka.core.Mention;
import com.cka.core.WorkItem;
import com.cka.provider.ProviderHealth;
import com.cka.provider.decision.DecisionProvider;

import java.util.List;

/**
 * Returns whatever mentions it was constructed with, whatever the knowledge — but refuses to
 * "match" an item that is not in the candidate pool it is given, the one promise every real
 * decision provider must keep (PRD 8.2).
 */
public class ScriptedDecisionProvider implements DecisionProvider {

    private final List<Mention> mentions;

    public ScriptedDecisionProvider(Mention... mentions) {
        this.mentions = List.of(mentions);
    }

    @Override
    public List<Mention> detectMentions(Knowledge knowledge, List<WorkItem> candidatePool) {
        var poolIds = candidatePool.stream().map(WorkItem::id).toList();
        mentions.stream()
                .filter(Mention::matched)
                .filter(mention -> !poolIds.contains(mention.workItemId()))
                .findAny()
                .ifPresent(mention -> {
                    throw new IllegalStateException("scripted a match outside the pool: " + mention);
                });
        return mentions;
    }

    @Override
    public ProviderHealth checkHealth() {
        return ProviderHealth.up("scripted");
    }
}
