package com.cka.persistence;

import com.cka.provider.ProviderHealth;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.jdbc.autoconfigure.DataSourceProperties;
import org.springframework.jdbc.core.JdbcOperations;
import org.springframework.stereotype.Component;

/**
 * Is the database reachable? Shared by every store built on this layer, because the answer is
 * about the connection rather than about any one table — two stores on one datasource asking it
 * separately would be the same query written twice.
 */
@Component
@RequiredArgsConstructor
public class DatabaseHealth {

    private final JdbcOperations jdbc;

    /**
     * The datasource's own configuration, taken as a collaborator rather than read out of
     * {@code spring.datasource.url} with a {@code @Value} parameter. That would mean writing
     * this constructor out by hand, because Lombok copies an annotation onto a generated
     * constructor's parameters only when a {@code lombok.config} lists it as copyable — and a
     * repository-wide config file to annotate one parameter is the worse trade.
     */
    private final DataSourceProperties database;

    /**
     * A query rather than a connection test, because for SQLite those differ: a file that is
     * missing, unreadable or not a database at all opens without complaint and fails on first
     * use. {@code SELECT 1} is the cheapest statement that actually reaches the file.
     *
     * <p>The URL comes from configuration rather than the connection's metadata, so a failure
     * can still say which database could not be reached.
     */
    public ProviderHealth check() {
        try {
            jdbc.queryForObject("SELECT 1", Integer.class);
            return ProviderHealth.up("connected to " + database.getUrl());
        } catch (Exception e) {
            return ProviderHealth.down("cannot query " + database.getUrl() + ": " + e.getMessage());
        }
    }
}
