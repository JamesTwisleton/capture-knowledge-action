package com.cka.provider.audit;

import com.cka.core.AuditEntry;
import com.cka.provider.ProviderHealth;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.jdbc.autoconfigure.DataSourceProperties;
import org.springframework.jdbc.core.JdbcOperations;
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
    private final JdbcOperations jdbc;

    /**
     * The datasource's own configuration, taken as a collaborator rather than read out of
     * {@code spring.datasource.url} with a {@code @Value} parameter. That would mean writing
     * this constructor out by hand, because Lombok copies an annotation onto a generated
     * constructor's parameters only when a {@code lombok.config} lists it as copyable — and a
     * repository-wide config file to annotate one parameter is the worse trade.
     */
    private final DataSourceProperties database;

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

    /**
     * A query rather than a connection test, because for SQLite those differ: a file that is
     * missing, unreadable or not a database at all opens without complaint and fails on first
     * use. {@code SELECT 1} is the cheapest statement that actually reaches the file.
     *
     * <p>The URL comes from configuration rather than the connection's metadata, so a failure can
     * still say which database could not be reached.
     */
    @Override
    public ProviderHealth checkHealth() {
        try {
            jdbc.queryForObject("SELECT 1", Integer.class);
            return ProviderHealth.up("connected to " + database.getUrl());
        } catch (Exception e) {
            return ProviderHealth.down("cannot query " + database.getUrl() + ": " + e.getMessage());
        }
    }
}
