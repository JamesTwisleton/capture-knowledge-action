package com.cka.provider.capture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Against a real SQLite database, for the reason T10 gives: the migration and the primary key
 * are the whole of this, and they are what differ between one SQL database and another.
 *
 * <p>Each test uses its own title. One database file is shared by the class, as one Spring
 * context is, so a test reusing another's title would be asserting on a row another test wrote.
 */
@SpringBootTest(properties = "cka.provider.eventbus=none")
@DisplayName("Captured meetings are remembered across restarts in SQLite")
class SqlCapturedItemsTest {

    private static final Path DATABASE = temporaryDatabasePath();

    @Autowired
    private CapturedItems captured;

    @DynamicPropertySource
    static void sqliteDatabase(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> "jdbc:sqlite:" + DATABASE);
    }

    private static Path temporaryDatabasePath() {
        try {
            return Files.createTempDirectory("cka-captured-test").resolve("cka.db");
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Test
    @DisplayName("A meeting that has not been captured is not reported as captured")
    void aMeetingNotCapturedIsNotReported() {
        assertThat(captured.alreadyCaptured("Never seen")).isFalse();
    }

    @Test
    @DisplayName("A remembered meeting is reported as captured")
    void aRememberedMeetingIsReported() {
        captured.remember("Sprint Refinement 2026-11-08");

        assertThat(captured.alreadyCaptured("Sprint Refinement 2026-11-08")).isTrue();
    }

    @Test
    @DisplayName("Remembering the same meeting twice is not an error")
    void rememberingTwiceIsNotAnError() {
        // Two polls can both read a title as new before either writes it. The primary key turns
        // the second write into a failure, and that failure must not take the poll down with
        // it — the meeting has still been captured exactly once, which is the point.
        //
        // Not a theoretical case to guard: SQLite reports the violation with a null SQL state,
        // so Spring raises UncategorizedSQLException rather than DuplicateKeyException, and a
        // catch written against the obvious exception misses it entirely.
        captured.remember("Stand-up 2026-11-08");

        assertThatCode(() -> captured.remember("Stand-up 2026-11-08")).doesNotThrowAnyException();
        assertThat(captured.alreadyCaptured("Stand-up 2026-11-08")).isTrue();
    }

    @Test
    @DisplayName("Titles are matched exactly, so two meetings named alike stay separate")
    void titlesAreMatchedExactly() {
        captured.remember("Retro 2026-11-08");

        assertThat(captured.alreadyCaptured("Retro 2026-11-09")).isFalse();
        assertThat(captured.alreadyCaptured("retro 2026-11-08")).isFalse();
    }

    @Test
    @DisplayName("A meeting is not confirmed as stored until it is, and forgetting it clears that")
    void confirmingStoredIsRecordedAndForgotten() {
        captured.remember("Planning 2026-11-08");
        assertThat(captured.confirmedStored()).doesNotContain("Planning 2026-11-08");

        captured.confirmStored("Planning 2026-11-08");
        assertThat(captured.confirmedStored()).contains("Planning 2026-11-08");

        captured.forget("Planning 2026-11-08");
        captured.remember("Planning 2026-11-08");
        assertThat(captured.confirmedStored()).doesNotContain("Planning 2026-11-08");
    }

    @Test
    @DisplayName("Confirming a meeting that was never captured records nothing")
    void confirmingAnUncapturedMeetingRecordsNothing() {
        captured.confirmStored("Never captured 2026-11-08");

        assertThat(captured.confirmedStored()).doesNotContain("Never captured 2026-11-08");
        assertThat(captured.alreadyCaptured("Never captured 2026-11-08")).isFalse();
    }
}
