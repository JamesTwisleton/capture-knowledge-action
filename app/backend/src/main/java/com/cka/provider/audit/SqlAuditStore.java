package com.cka.provider.audit;

import com.cka.core.ActionType;
import com.cka.core.AuditEntry;
import com.cka.core.AuditStep;
import com.cka.persistence.DatabaseHealth;
import com.cka.provider.ProviderHealth;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * The audit store on SQL (T10): SQLite by default, any SQL database by configuration.
 *
 * <p>Thin on purpose. The mapping is one core record to one row, so there is nothing here but
 * the conversion and the health check — the query itself is a derived method on
 * {@link AuditEntryRepository}, and the schema is a Flyway migration.
 */
@Component
@ConditionalOnProperty(prefix = "cka.provider", name = "audit", havingValue = "sql")
@RequiredArgsConstructor
public class SqlAuditStore implements AuditStore {

    private final AuditEntryRepository entries;
    private final DatabaseHealth database;

    @Override
    public void record(AuditEntry entry) {
        entries.save(AuditEntryRow.of(entry));
    }

    @Override
    public List<AuditEntry> chain(UUID chainId) {
        return entries.findByChainIdOrderByIdAsc(chainId.toString()).stream()
                .map(AuditEntryRow::toEntry)
                .toList();
    }

    @Override
    public List<AuditEntry> decisions(ActionType type, Instant since) {
        return entries
                .findByActionTypeAndStepInAndRecordedAtGreaterThanEqualOrderByRecordedAtDescIdDesc(
                        type, AuditStep.humanDecisions(), since.toEpochMilli())
                .stream()
                .map(AuditEntryRow::toEntry)
                .toList();
    }

    @Override
    public ProviderHealth checkHealth() {
        return database.check();
    }
}
