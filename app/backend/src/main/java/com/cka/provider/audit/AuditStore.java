package com.cka.provider.audit;

import com.cka.core.AuditEntry;
import com.cka.provider.Provider;
import java.util.List;
import java.util.UUID;

/** Persists the action decision chain (PRD 7.8, 9.3). */
public interface AuditStore extends Provider {

    void record(AuditEntry entry);

    /** Every entry recorded for one chain, in the order they were recorded. */
    List<AuditEntry> chain(UUID chainId);
}
