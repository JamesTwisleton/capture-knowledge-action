package com.cka.provider.prompt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.Mockito.mock;

import com.cka.core.Prompt;
import com.cka.persistence.DatabaseHealth;
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
 * T11 against a real SQLite database, for the reason T10 gives: the migration, the seed and the
 * version ordering are the whole of this layer. The broker is switched off rather than mocked, as
 * in {@code SqlAuditStoreTest} — a prompt test should not need a RabbitMQ connection.
 *
 * <p>Every test here works on its own content type. One database is shared by the class, as one
 * Spring context is, and a prompt's version number counts within its content type — so a test
 * that reused another's type would be asserting on versions another test wrote.
 */
@SpringBootTest(properties = "cka.provider.eventbus=none")
@DisplayName("The prompt store versions prompts by content type in SQLite")
class SqlPromptStoreTest {

    private static final Path DATABASE = temporaryDatabasePath();

    @Autowired
    private PromptStore prompts;

    @DynamicPropertySource
    static void sqliteDatabase(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> "jdbc:sqlite:" + DATABASE);
    }

    private static Path temporaryDatabasePath() {
        try {
            return Files.createTempDirectory("cka-prompt-test").resolve("cka.db");
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Test
    @DisplayName("The Sprint Refinement prompt ships with the application")
    void theSprintRefinementPromptShipsWithTheApplication() {
        // "Seeded with a default Sprint Refinement prompt" — and seeded by the migration, so a
        // fresh database has it before anything runs. The MVP's one meeting type (PRD 7.6).
        var prompt = prompts.current("Sprint Refinement");

        assertThat(prompt.version()).isEqualTo(1);
        assertThat(prompt.instructions()).isNotBlank();
    }

    @Test
    @DisplayName("An item with no type of its own is summarised with the General prompt, which ships too")
    void anItemWithNoTypeOfItsOwnIsSummarisedWithTheGeneralPrompt() {
        // PRD 7.6: an item that has not been given a more specific type is summarised with the
        // general prompt. The mechanism, not a special case — which is what lets a type be
        // assigned later and the summary re-run.
        var prompt = prompts.current("One-to-one");

        assertThat(prompt.contentType()).isEqualTo(Prompt.FALLBACK_CONTENT_TYPE);
        assertThat(prompt.instructions()).isNotBlank();
    }

    @Test
    @DisplayName("Editing a prompt writes the next version, and that is what is summarised with")
    void editingAPromptWritesTheNextVersion() {
        var created = prompts.save("Stand-up", "Who is blocked?");

        var edited = prompts.save("Stand-up", "Who is blocked, and what changed since yesterday?");

        assertThat(created.version()).isEqualTo(1);
        assertThat(edited.version()).isEqualTo(2);
        assertThat(prompts.current("Stand-up")).isEqualTo(edited);
    }

    @Test
    @DisplayName("A previous version is still retrievable after newer ones are written")
    void aPreviousVersionIsStillRetrievableAfterNewerOnesAreWritten() {
        // The acceptance criterion, and the reason the audit chain can record a version number at
        // all: "the version used is recorded against each output" is worth nothing if the version
        // it names has been overwritten. Newest first, because that is the order the editor and
        // the history endpoint read.
        prompts.save("Sprint Planning", "List the committed items.");
        prompts.save("Sprint Planning", "List the committed items and the ones deferred.");
        prompts.save("Sprint Planning", "List the committed items, the ones deferred, and why.");

        assertThat(prompts.versions("Sprint Planning"))
                .extracting(Prompt::version, Prompt::instructions)
                .containsExactly(
                        tuple(3, "List the committed items, the ones deferred, and why."),
                        tuple(2, "List the committed items and the ones deferred."),
                        tuple(1, "List the committed items."));
    }

    @Test
    @DisplayName("A version is numbered within its content type, not across all of them")
    void aVersionIsNumberedWithinItsContentType() {
        // Otherwise the number in the audit chain would depend on what other teams edited, and
        // "version 4 of the refinement prompt" would stop meaning anything. Asserted against a
        // type that already has two versions, so a store numbering globally fails here.
        prompts.save("Retrospective", "Summarise what the team agreed to change.");
        prompts.save("Retrospective", "Summarise what the team agreed to change, and who owns it.");

        var firstOfAnotherType = prompts.save("Design note", "Summarise the decision.");

        assertThat(firstOfAnotherType.version()).isEqualTo(1);
    }

    @Test
    @DisplayName("A content type nobody has written a prompt for has no versions, and no error")
    void aContentTypeNobodyHasWrittenAPromptForHasNoVersions() {
        assertThat(prompts.versions("Nothing has this type")).isEmpty();
    }

    @Test
    @DisplayName("A database with the fallback prompt deleted says so, rather than returning nothing")
    void aDatabaseWithTheFallbackPromptDeletedSaysSo() {
        // The one case the shared database cannot be asked for without wrecking it for the tests
        // above, so this one is the store over an empty repository instead. The message matters:
        // both prompts ship seeded, so an installation missing them has had them deleted, and
        // that is what the exception has to say.
        var empty = new SqlPromptStore(mock(PromptRepository.class), mock(DatabaseHealth.class));

        assertThatIllegalStateException()
                .isThrownBy(() -> empty.current("Sprint Refinement"))
                .withMessageContaining("Sprint Refinement")
                .withMessageContaining(Prompt.FALLBACK_CONTENT_TYPE);
    }
}
