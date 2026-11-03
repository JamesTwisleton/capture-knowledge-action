package com.cka.provider.audit;

import static org.assertj.core.api.Assertions.assertThat;

import com.cka.core.AuditEntry;
import com.cka.core.AuditStep;
import com.cka.core.ChainId;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcOperations;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * T10 against a real SQLite database, not a mock and not H2: the ticket's "repository tests run
 * against a real SQLite database" is the whole point, since what can differ between SQLite and
 * another engine — the dialect, the migration, the ordering column — is exactly what this layer
 * is made of.
 *
 * <p>The broker provider is switched off with {@code cka.provider.eventbus=none} rather than
 * mocked. Its {@code @ConditionalOnProperty} then simply does not match, so the context holds the
 * persistence layer and nothing that wants a RabbitMQ connection — a persistence test should not
 * fail, or log warnings, because a broker is absent.
 */
@SpringBootTest(properties = "cka.provider.eventbus=none")
@DisplayName("The audit store persists an action decision chain in SQLite")
class SqlAuditStoreTest {

    /**
     * A path two levels deep in a fresh temp directory, so the <em>parent is missing</em> on
     * purpose: SQLite creates the database file but never the directory holding it, and this is
     * what proves {@code SqlitePersistenceConfiguration} fills that gap. A static field, built at
     * class load, because {@link DynamicPropertySource} runs before any instance exists.
     */
    private static final Path DATABASE = temporaryDatabasePath();

    @Autowired
    private AuditStore audit;

    @Autowired
    private JdbcOperations jdbc;

    @DynamicPropertySource
    static void sqliteDatabase(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> "jdbc:sqlite:" + DATABASE);
    }

    private static Path temporaryDatabasePath() {
        try {
            return Files.createTempDirectory("cka-audit-test").resolve("created-on-demand/cka.db");
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Test
    @DisplayName("An entry recorded against a chain is read back from it")
    void anEntryRecordedAgainstAChainIsReadBackFromIt() {
        var chainId = ChainId.next();
        var captured = AuditEntry.builder()
                .chainId(chainId)
                .step(AuditStep.CONTENT_CAPTURED)
                .detail("Captured \"Sprint Refinement\" from memory://recordings/refinement.mp4")
                .build();

        audit.record(captured);

        // Equality across the record: it only holds if the chain id survived the round trip
        // through TEXT and the step through its name rather than an ordinal.
        assertThat(audit.chain(chainId)).containsExactly(captured);
    }

    @Test
    @DisplayName("A chain comes back in the order its steps were recorded")
    void aChainComesBackInTheOrderItsStepsWereRecorded() {
        // The ordering guarantee provider/audit/AGENTS.md requires. Recorded in pipeline order,
        // and the detail strings are deliberately not alphabetical, so a store that sorted by
        // any column other than the ordering one would fail here rather than pass by luck.
        var chainId = ChainId.next();
        var steps = List.of(
                entry(chainId, AuditStep.CONTENT_CAPTURED, "zeroth: captured"),
                entry(chainId, AuditStep.CONTENT_SUMMARISED, "alpha: summarised"),
                entry(chainId, AuditStep.KNOWLEDGE_STORED, "middle: stored"),
                entry(chainId, AuditStep.ACTION_PROPOSED, "beta: proposed"),
                entry(chainId, AuditStep.ACTION_APPLIED, "last: applied"));

        steps.forEach(audit::record);

        assertThat(audit.chain(chainId)).containsExactlyElementsOf(steps);
    }

    @Test
    @DisplayName("A chain holds only its own steps")
    void aChainHoldsOnlyItsOwnSteps() {
        var mine = ChainId.next();
        var other = ChainId.next();
        var ours = entry(mine, AuditStep.KNOWLEDGE_STORED, "ours");

        audit.record(ours);
        audit.record(entry(other, AuditStep.KNOWLEDGE_STORED, "theirs"));

        assertThat(audit.chain(mine)).containsExactly(ours);
    }

    @Test
    @DisplayName("A chain nothing was recorded against is empty, not an error")
    void aChainNothingWasRecordedAgainstIsEmpty() {
        assertThat(audit.chain(ChainId.next())).isEmpty();
    }

    @Test
    @DisplayName("The migration ran, so a later ticket's tables arrive the same way")
    void theMigrationRan() {
        // The acceptance criterion "applies migrations" on startup, asserted on Flyway's own
        // record rather than on the table existing — a table could exist for other reasons.
        var applied = jdbc.queryForList("SELECT version, success FROM flyway_schema_history ORDER BY installed_rank");

        assertThat(applied).hasSize(1);
        assertThat(applied.getFirst()).containsEntry("version", "1");
        assertThat(applied.getFirst()).containsEntry("success", 1);
    }

    @Test
    @DisplayName("The directory for the database file is created when it does not exist")
    void theDirectoryForTheDatabaseFileIsCreated() {
        // DATABASE deliberately names a directory that did not exist when the context started.
        assertThat(DATABASE).exists();
        assertThat(DATABASE.getParent()).isDirectory();
    }

    @Test
    @DisplayName("The health check reports up against a reachable database")
    void theHealthCheckReportsUpAgainstAReachableDatabase() {
        var health = audit.checkHealth();

        assertThat(health.up()).isTrue();
        assertThat(health.detail()).contains("cka.db");
    }

    private static AuditEntry entry(java.util.UUID chainId, AuditStep step, String detail) {
        return AuditEntry.builder().chainId(chainId).step(step).detail(detail).build();
    }
}
