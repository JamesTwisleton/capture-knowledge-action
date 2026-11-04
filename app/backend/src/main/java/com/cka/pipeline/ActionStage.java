package com.cka.pipeline;

import com.cka.core.Action;
import com.cka.core.ActionType;
import com.cka.core.Mention;
import com.cka.core.event.ActionProposed;
import com.cka.core.event.KnowledgeStored;
import com.cka.provider.decision.DecisionProvider;
import com.cka.provider.eventbus.EventBus;
import com.cka.provider.workitem.WorkItemProvider;
import java.util.UUID;
import lombok.RequiredArgsConstructor;

/**
 * Action: knowledge stored in; a proposed comment out for each matched mention, and an audit
 * entry for each unmatched one — recorded, never proposed, never dropped (PRD 9.1).
 *
 * <p>This is the gate chain's skeleton, not the gate chain. Every match is proposed for a
 * human to confirm; T08 adds the mention threshold above which a comment is posted
 * automatically, and the intent, decision and trust checks for mutating actions. T08 also makes
 * that threshold a value read per run rather than a constant, because T23 lets a human change it.
 *
 * <p>An unmatched mention is recorded and goes no further <em>here</em>, which is not the same as
 * going nowhere: PRD 9.2 has a person linking it to the work item it meant or dismissing it, and
 * those decisions are what the mention threshold is later calibrated from. T23 owns that; this
 * stage's job ends at the audit entry.
 */
@RequiredArgsConstructor
public class ActionStage {

    private final DecisionProvider decision;
    private final WorkItemProvider workItems;
    private final EventBus bus;

    /**
     * The audit service, not the store: an unmatched mention is a step of the chain with no event
     * to carry it, and how a step is phrased and what it records is the service's to decide
     * (PRD 9.1).
     */
    private final AuditTrail audit;

    public void start() {
        bus.subscribe(
                KnowledgeStored.class,
                stored -> decision.detectMentions(stored.knowledge(), workItems.candidatePool())
                        .forEach(mention -> handle(stored.chainId(), mention)));
    }

    private void handle(UUID chainId, Mention mention) {
        if (!mention.matched()) {
            audit.mentionUnmatched(chainId, mention);
            return;
        }
        bus.publish(ActionProposed.builder()
                .chainId(chainId)
                .action(Action.builder()
                        .type(ActionType.COMMENT)
                        .workItemId(mention.workItemId())
                        .excerpt(mention.excerpt())
                        .confidence(mention.confidence())
                        .decidedBy(mention.scoredBy())
                        .build())
                .build());
    }
}
