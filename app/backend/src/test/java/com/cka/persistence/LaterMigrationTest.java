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
 * one — which every ticket that has touched the schema has done.
 *
 * <p>Driven through Flyway's own API rather than a Spring context, because the point is to
 * migrate the same database twice from two different sets of migrations, which one application
 * startup cannot express.
 */
@DisplayName("Later tickets can add migrations to a database already in use")
class LaterMigrationTest {

    private static final String SHIPPED = "classpath:db/migration";

    /**
     * The migration a later ticket will add, standing in for one without inventing a table that
     * ticket has not designed. A column rather than a table because that is the harder case: an
     * empty new table cannot disagree with rows that already exist, and a column has to be given
     * to them.
     */
    private static final String LATER_MIGRATION = "ALTER TABLE audit_entry ADD COLUMN reviewed_by TEXT;\n";

    @Test
    @DisplayName("A migration added later applies to a database that already holds data")
    void aMigrationAddedLaterAppliesToADatabaseThatAlreadyHoldsData() throws Exception {
        var url = "jdbc:sqlite:" + temporaryDatabase();

        // The schema as it ships today, with a row written against it — an empty database would
        // not prove much, since the risk in a later migration is what it does to existing rows.
        var shipped = Flyway.configure()
                .dataSource(url, null, null)
                .locations(SHIPPED)
                .load();
        shipped.migrate();
        try (var connection = DriverManager.getConnection(url);
                var insert = connection.prepareStatement("""
                        INSERT INTO audit_entry (chain_id, step, recorded_at, detail, decider_kind)
                        VALUES ('chain-1', 'KNOWLEDGE_STORED', 1759828500000, 'before', 'SYSTEM')""")) {
            insert.executeUpdate();
        }

        var later = Flyway.configure()
                .dataSource(url, null, null)
                .locations(SHIPPED, "filesystem:" + aMigrationAfter(shipped))
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

    /**
     * Writes {@link #LATER_MIGRATION} to a temp directory, versioned one sub-version past the
     * database's current one, and returns the directory for Flyway to read as a location.
     *
     * <p>The version is computed rather than written down, because Flyway refuses two migrations
     * claiming the same version and a number chosen in advance is a number a real migration can
     * eventually reach. Extending the current version — {@code 3} becomes {@code 3.1} — is
     * always ordered after everything applied and before the next whole version, so there is no
     * number for a ticket to collide with and nothing here to renumber when one lands.
     */
    private static Path aMigrationAfter(Flyway shipped) throws Exception {
        var current = shipped.info().current();
        assertThat(current)
                .as("the shipped migrations, which this test applies before asking what version they reached")
                .isNotNull();

        var location = Files.createTempDirectory("cka-later-migration");
        Files.writeString(
                location.resolve("V" + current.getVersion() + ".1__later_ticket_column.sql"), LATER_MIGRATION);
        return location;
    }

    private static Path temporaryDatabase() throws Exception {
        return Files.createTempDirectory("cka-migration-test").resolve("cka.db");
    }
}
