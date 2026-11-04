package com.cka.provider.audit;

import static java.util.Objects.requireNonNull;
import static org.assertj.core.api.Assertions.assertThat;

import com.cka.core.ActionType;
import com.cka.core.AuditEntry;
import com.cka.core.AuditStep;
import com.cka.core.ChainId;
import com.cka.core.Decider;
import com.cka.core.Provenance;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.jdbc.core.JdbcOperations;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * The audit store against a real SQLite database, not a mock and not H2: T10's "repository tests
 * run against a real SQLite database" is the whole point, since what can differ between SQLite
 * and another engine — the dialect, the migration, the ordering column — is exactly what this
 * layer is made of. T14 widens it to the whole of PRD 9.3, so the round trip now has to carry
 * provider, model, confidence, decider and prompt version as well.
 *
 * <p>The broker provider is switched off with {@code cka.provider.eventbus=none} rather than
 * mocked. Its {@code @ConditionalOnProperty} then simply does not match, so the context holds the
 * persistence layer and nothing that wants a RabbitMQ connection — a persistence test should not
 * fail, or log warnings, because a broker is absent.
 *
 * <p>One database file is shared by every test here, as one Spring context is. Tests that query
 * across chains — the decision history below — therefore pick their own far-apart years and ask
 * only about those, rather than asserting on whatever else the class has written.
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

    private static final Instant RECORDED_AT = Instant.parse("2026-10-07T09:15:00Z");
    private static final Provenance JEV =
            Provenance.builder().provider("jev").model("typesafe-1").build();

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
                .recordedAt(RECORDED_AT)
                .detail("Captured \"Sprint Refinement\" from memory://recordings/refinement.mp4")
                .decider(Decider.system())
                .build();

        audit.record(captured);

        // Equality across the record: it only holds if the chain id survived the round trip
        // through TEXT and the step through its name rather than an ordinal.
        assertThat(audit.chain(chainId)).containsExactly(captured);
    }

    @Test
    @DisplayName("Everything PRD 9.3 requires of an entry survives the round trip")
    void everythingTheAuditRecordRequiresSurvivesTheRoundTrip() {
        // The widest entry the pipeline writes: a human rejecting a proposed action carries all
        // five of the fields T14 added at once, so equality here is the whole of 9.3 at once.
        var chainId = ChainId.next();
        var rejected = AuditEntry.builder()
                .chainId(chainId)
                .step(AuditStep.ACTION_REJECTED)
                .recordedAt(RECORDED_AT)
                .detail("Rejected CLOSE on CKA-7")
                .actionType(ActionType.CLOSE)
                .producedBy(JEV)
                .confidence(0.81)
                .decider(Decider.human("james"))
                .promptVersion(3)
                .build();

        audit.record(rejected);

        assertThat(audit.chain(chainId)).containsExactly(rejected);
    }

    @Test
    @DisplayName("Fields that do not apply to a step come back absent, not guessed at")
    void fieldsThatDoNotApplyToAStepComeBackAbsent() {
        // Capturing a recording has no action, no model and nothing to be confident about. The
        // columns exist for every step, so "absent" has to survive the round trip as null rather
        // than as a zero that reads like a real score of 0.0.
        var chainId = ChainId.next();
        audit.record(entry(chainId, AuditStep.CONTENT_CAPTURED, "Captured \"Sprint Refinement\""));

        assertThat(audit.chain(chainId).getFirst()).satisfies(entry -> {
            assertThat(entry.actionType()).isNull();
            assertThat(entry.producedBy()).isNull();
            assertThat(entry.confidence()).isNull();
            assertThat(entry.promptVersion()).isNull();
        });
    }

    @Test
    @DisplayName("A chain comes back in the order its steps were recorded")
    void aChainComesBackInTheOrderItsStepsWereRecorded() {
        // The ordering guarantee provider/audit/AGENTS.md requires. Recorded in pipeline order,
        // and the detail strings are deliberately not alphabetical, so a store that sorted by
        // any column other than the ordering one would fail here rather than pass by luck. They
        // all share one timestamp for the same reason: a store sorting by time would tie on
        // every row and give the database's own order back, which is what the column prevents.
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
    @DisplayName("An instant finer than a millisecond is recorded to the millisecond")
    void anInstantFinerThanAMillisecondIsRecordedToTheMillisecond() {
        // The column holds epoch milliseconds, so this is the one way the round trip could
        // quietly not be one. AuditEntry truncates on the way in, which is what makes a recorded
        // entry equal to the entry that was handed over — Instant.now() is finer than a
        // millisecond on this JDK, so without it every caller would hit this.
        var chainId = ChainId.next();
        var entry = entry(chainId, AuditStep.ACTION_APPLIED, "applied").toBuilder()
                .recordedAt(Instant.parse("2026-10-07T09:15:00.123456789Z"))
                .build();

        audit.record(entry);

        assertThat(audit.chain(chainId).getFirst().recordedAt())
                .isEqualTo(Instant.parse("2026-10-07T09:15:00.123Z"))
                .isEqualTo(entry.recordedAt());
    }

    @Test
    @DisplayName("Human accept and reject decisions come back for their action type, newest first")
    void humanAcceptAndRejectDecisionsComeBackForTheirActionType() {
        // The trust data PRD 9.2 computes from, and the accept/reject history the front end
        // shows. Both kinds are kept: a reject is as much a data point as an accept.
        var window = Instant.parse("2031-01-01T00:00:00Z");
        var accepted = decision(AuditStep.ACTION_ACCEPTED, ActionType.CLOSE, window.plusSeconds(60));
        var rejected = decision(AuditStep.ACTION_REJECTED, ActionType.CLOSE, window.plusSeconds(120));

        audit.record(accepted);
        audit.record(rejected);

        assertThat(audit.decisions(ActionType.CLOSE, window)).containsExactly(rejected, accepted);
    }

    @Test
    @DisplayName("Two decisions in the same millisecond keep the order they were recorded in")
    void twoDecisionsInTheSameMillisecondKeepTheOrderTheyWereRecordedIn() {
        // The history is ordered by time, so that the index can serve the sort — which leaves
        // the tie a timestamp cannot break. The ordering column breaks it, the same way it does
        // within a chain, rather than the result depending on what the database returns first.
        var window = Instant.parse("2035-01-01T00:00:00Z");
        var earlier = decision(AuditStep.ACTION_ACCEPTED, ActionType.CLOSE, window);
        var later = decision(AuditStep.ACTION_REJECTED, ActionType.CLOSE, window);

        audit.record(earlier);
        audit.record(later);

        assertThat(audit.decisions(ActionType.CLOSE, window)).containsExactly(later, earlier);
    }

    @Test
    @DisplayName("A decision on another action type is not counted against this one")
    void aDecisionOnAnotherActionTypeIsNotCountedAgainstThisOne() {
        // Trust is per action type, never global (PRD 9.2) — accepting a comment says nothing
        // about whether closing an item should be trusted.
        var window = Instant.parse("2032-01-01T00:00:00Z");
        var comment = decision(AuditStep.ACTION_ACCEPTED, ActionType.COMMENT, window.plusSeconds(60));
        audit.record(comment);
        audit.record(decision(AuditStep.ACTION_ACCEPTED, ActionType.CLOSE, window.plusSeconds(60)));

        assertThat(audit.decisions(ActionType.COMMENT, window)).containsExactly(comment);
    }

    @Test
    @DisplayName("A decision older than the window asked for is left out of it")
    void aDecisionOlderThanTheWindowAskedForIsLeftOutOfIt() {
        // The window is configurable (PRD 9.2), so "since" has to be a real bound rather than
        // decoration — a team's trust score should not be held down by a month-old mistake.
        var window = Instant.parse("2033-01-01T00:00:00Z");
        var inside = decision(AuditStep.ACTION_ACCEPTED, ActionType.CLOSE, window);
        audit.record(decision(AuditStep.ACTION_REJECTED, ActionType.CLOSE, window.minusMillis(1)));
        audit.record(inside);

        assertThat(audit.decisions(ActionType.CLOSE, window)).containsExactly(inside);
    }

    @Test
    @DisplayName("A step that is not a human decision stays out of the decision history")
    void aStepThatIsNotAHumanDecisionStaysOutOfTheDecisionHistory() {
        // An action CKA applied by itself is not evidence about human trust in it, however much
        // it looks like one — it carries an action type and a confidence just the same.
        var window = Instant.parse("2034-01-01T00:00:00Z");
        audit.record(decision(AuditStep.ACTION_APPLIED, ActionType.CLOSE, window.plusSeconds(60)).toBuilder()
                .decider(Decider.system())
                .build());

        assertThat(audit.decisions(ActionType.CLOSE, window)).isEmpty();
    }

    @Test
    @DisplayName("Every shipped migration ran, so a later ticket's tables arrive the same way")
    void theMigrationsRan() throws IOException {
        // The acceptance criterion "applies migrations" on startup, asserted on Flyway's own
        // record rather than on the tables existing — a table could exist for other reasons.
        //
        // What the versions should be is read off the migration files rather than written out
        // here: the claim is that Flyway applied all of them and left none behind, and a listed
        // set of version numbers stops making that claim the moment a ticket adds one — it only
        // repeats what the previous ticket saw, until somebody edits it back into agreement.
        var applied = jdbc.queryForList("SELECT version, success FROM flyway_schema_history ORDER BY installed_rank");

        assertThat(applied)
                .extracting(row -> row.get("version"))
                .containsExactlyInAnyOrderElementsOf(shippedMigrationVersions());
        assertThat(applied).extracting(row -> row.get("success")).containsOnly(1);
    }

    /**
     * The versions of the migrations on the classpath, taken from their filenames — {@code
     * V2__audit_entry_decision_chain.sql} is version {@code "2"}, in the form
     * {@code flyway_schema_history} stores it, which spells a dotted version with an underscore.
     */
    private static List<String> shippedMigrationVersions() throws IOException {
        var files = new PathMatchingResourcePatternResolver().getResources("classpath:db/migration/V*__*.sql");

        assertThat(files).as("the shipped migrations, which cannot be none").isNotEmpty();
        return Stream.of(files)
                .map(file -> requireNonNull(file.getFilename())
                        .substring(1)
                        .split("__")[0]
                        .replace('_', '.'))
                .toList();
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

    private static AuditEntry entry(UUID chainId, AuditStep step, String detail) {
        return AuditEntry.builder()
                .chainId(chainId)
                .step(step)
                .recordedAt(RECORDED_AT)
                .detail(detail)
                .decider(Decider.system())
                .build();
    }

    /** A human's accept or reject in triage, which is what the decision history is made of. */
    private static AuditEntry decision(AuditStep step, ActionType type, Instant at) {
        return AuditEntry.builder()
                .chainId(ChainId.next())
                .step(step)
                .recordedAt(at.truncatedTo(ChronoUnit.MILLIS))
                .detail(step + " " + type)
                .actionType(type)
                .producedBy(JEV)
                .confidence(0.42)
                .decider(Decider.human("james"))
                .build();
    }
}
