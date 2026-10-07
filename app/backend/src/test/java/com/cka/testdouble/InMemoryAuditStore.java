package com.cka.testdouble;

import com.cka.core.AuditEntry;
import com.cka.provider.ProviderHealth;
import com.cka.provider.audit.AuditStore;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class InMemoryAuditStore implements AuditStore {

    private final List<AuditEntry> entries = new ArrayList<>();

    @Override
    public void record(AuditEntry entry) {
        entries.add(entry);
    }

    @Override
    public List<AuditEntry> chain(UUID chainId) {
        return entries.stream().filter(entry -> entry.chainId().equals(chainId)).toList();
    }

    @Override
    public ProviderHealth checkHealth() {
        return ProviderHealth.up("in memory");
    }
}
