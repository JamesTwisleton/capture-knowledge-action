package com.cka.provider.audit;

import com.cka.core.ActionType;
import com.cka.core.AuditEntry;
import com.cka.core.AuditStep;
import com.cka.provider.Provider;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Persists the action decision chain (PRD 7.8, 9.3). */
public interface AuditStore extends Provider {

    void record(AuditEntry entry);

    /** Every entry recorded for one chain, in the order they were recorded. */
    List<AuditEntry> chain(UUID chainId);

    /**
     * The human accept and reject decisions recorded for one action type since {@code since},
     * most recently decided first, ties broken by the order they were recorded in. Both kinds, because a reject is as much a data point as an accept: this
     * is the trust data PRD 9.2 computes a per-action-type score from over a configurable
     * window, and the accept/reject history the front end shows back.
     *
     * <p>Human by definition, not by filter — the steps it reads are {@link
     * AuditStep#humanDecisions()}, which only a person's decision in triage produces. An action
     * CKA applied by itself says nothing about whether people trust that action type.
     */
    List<AuditEntry> decisions(ActionType type, Instant since);
}
