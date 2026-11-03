package com.cka.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.DriverManager;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * T10's second acceptance criterion: "a migration added by a later ticket applies cleanly to an
 * existing database". The one claim this layer makes that nothing else can check — the first
 * criterion is covered by the app starting at all, and {@code SqlAuditStoreTest} asserts that
 * the shipped ones ran, but neither says anything about what happens when the next ticket adds
 * one — T14's V2 was exactly that case, and T11's is the next.
 *
 * <p>Driven through Flyway's own API rather than a Spring context, because the point is to
 * migrate the same database twice from two different sets of migrations, which one application
 * startup cannot express.
 */
@DisplayName("Later tickets can add migrations to a database already in use")
class LaterMigrationTest {

    private static final String SHIPPED = "classpath:db/migration";
    private static final String WITH_A_LATER_ONE = "classpath:db/migration-later";

    @Test
    @DisplayName("A migration added later applies to a database that already holds data")
    void aMigrationAddedLaterAppliesToADatabaseThatAlreadyHoldsData() throws Exception {
        var url = "jdbc:sqlite:" + temporaryDatabase();

        // The schema as it ships today, with a row written against it — an empty database would
        // not prove much, since the risk in a later migration is what it does to existing rows.
        Flyway.configure().dataSource(url, null, null).locations(SHIPPED).load().migrate();
        try (var connection = DriverManager.getConnection(url);
                var insert = connection.prepareStatement("""
                        INSERT INTO audit_entry (chain_id, step, recorded_at, detail, decider_kind)
                        VALUES ('chain-1', 'KNOWLEDGE_STORED', 1759828500000, 'before', 'SYSTEM')""")) {
            insert.executeUpdate();
        }

        var later = Flyway.configure()
                .dataSource(url, null, null)
                .locations(SHIPPED, WITH_A_LATER_ONE)
                .load()
                .migrate();

        // Only the new one ran: the shipped migrations are already recorded and checksummed, so
        // Flyway leaves them be.
        assertThat(later.migrationsExecuted).isEqualTo(1);
        assertThat(later.success).isTrue();

        try (var connection = DriverManager.getConnection(url);
                var statement = connection.createStatement();
                var rows = statement.executeQuery("SELECT detail, reviewed_by FROM audit_entry")) {
            assertThat(rows.next()).as("the row written before the migration").isTrue();
            assertThat(rows.getString("detail")).isEqualTo("before");
            assertThat(rows.getString("reviewed_by"))
                    .as("a column a later ticket added")
                    .isNull();
            assertThat(rows.next()).isFalse();
        }
    }

    private static Path temporaryDatabase() throws Exception {
        return Files.createTempDirectory("cka-migration-test").resolve("cka.db");
    }
}
