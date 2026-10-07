package com.cka.pipeline;

import com.cka.core.Action;
import com.cka.core.ActionType;
import com.cka.core.AuditEntry;
import com.cka.core.AuditStep;
import com.cka.core.Mention;
import com.cka.core.event.ActionProposed;
import com.cka.core.event.KnowledgeStored;
import com.cka.provider.audit.AuditStore;
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
 * automatically, and the intent, decision and trust checks for mutating actions.
 */
@RequiredArgsConstructor
public class ActionStage {

    private final DecisionProvider decision;
    private final WorkItemProvider workItems;
    private final EventBus bus;
    private final AuditStore audit;

    public void start() {
        bus.subscribe(
                KnowledgeStored.class,
                stored -> decision.detectMentions(stored.knowledge(), workItems.candidatePool())
                        .forEach(mention -> handle(stored.chainId(), mention)));
    }

    private void handle(UUID chainId, Mention mention) {
        if (!mention.matched()) {
            audit.record(AuditEntry.builder()
                    .chainId(chainId)
                    .step(AuditStep.MENTION_UNMATCHED)
                    .detail("No candidate matched \"" + mention.excerpt() + "\"")
                    .build());
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
