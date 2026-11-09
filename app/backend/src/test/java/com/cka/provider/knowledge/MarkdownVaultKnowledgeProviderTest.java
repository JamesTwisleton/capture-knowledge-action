package com.cka.provider.knowledge;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.cka.core.CapturedContent;
import com.cka.core.Provenance;
import com.cka.core.Summary;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * T06 against a real directory. There is nothing to mock: the whole of this provider is what it
 * writes and where, so a stubbed filesystem would assert the test's own idea of the answer.
 */
@DisplayName("The Markdown vault writes one readable note per captured item")
class MarkdownVaultKnowledgeProviderTest {

    /** Pinned, so a disambiguating suffix is a value a test can name. */
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-08T14:32:05Z"), ZoneOffset.UTC);

    @TempDir
    Path vault;

    private static final Summary SUMMARY = Summary.builder()
            .text("- #142 — agreed done, to be closed.")
            .producedBy(Provenance.builder()
                    .provider("anthropic")
                    .model("claude-sonnet-5")
                    .build())
            .promptVersion(3)
            .build();

    private static CapturedContent content(String title) {
        return CapturedContent.builder()
                .sourceUri("https://drive.google.com/file/d/abc123")
                .title(title)
                .contentType("Sprint Refinement")
                .transcript("Priya: #142 is merged and verified, so it can be closed.")
                .platformSummary("The team agreed #142 is done.")
                .build();
    }

    private MarkdownVaultKnowledgeProvider provider() {
        return new MarkdownVaultKnowledgeProvider(vault.toString(), CLOCK);
    }

    private String noteAt(String vaultRelativeId) throws IOException {
        return Files.readString(vault.resolve(vaultRelativeId));
    }

    @Test
    @DisplayName("The note is named after the item and lands in the vault")
    void theNoteIsNamedAfterTheItem() {
        var page = provider().store(content("Sprint Refinement 2026-10-07"), SUMMARY);

        assertThat(page.id()).isEqualTo("Sprint Refinement 2026-10-07.md");
        assertThat(vault.resolve(page.id())).isRegularFile();
    }

    @Test
    @DisplayName("The reference points at something that opens, not an internal key")
    void theReferencePointsAtSomethingThatOpens() {
        // PageReference.location is what a person clicks in T16's stored knowledge view, so it
        // has to be openable rather than the vault-relative id repeated.
        var page = provider().store(content("Sprint Refinement 2026-10-07"), SUMMARY);

        assertThat(page.location()).startsWith("file:").endsWith(".md").contains("Sprint%20Refinement");
    }

    @Test
    @DisplayName("The note carries the transcript and the summary, both labelled")
    void theNoteCarriesTheTranscriptAndTheSummary() throws IOException {
        var page = provider().store(content("Sprint Refinement 2026-10-07"), SUMMARY);

        assertThat(noteAt(page.id()))
                .contains("- #142 — agreed done, to be closed.")
                .contains("Priya: #142 is merged and verified, so it can be closed.")
                .contains("\n# Summary\n")
                .contains("\n# Transcript\n");
    }

    @Test
    @DisplayName("A stored note's title reads back exactly as it was written")
    void aStoredTitleReadsBackExactly() throws IOException {
        // The reconciler compares these against the capture record character for character, so
        // a title that does not survive render's quoting reads as a deleted note.
        var awkward = "Planning: \"Q4\" \\ backlog";
        provider().store(content(awkward), SUMMARY);
        Files.writeString(vault.resolve("My own note.md"), "No front matter here.");

        assertThat(provider().storedTitles()).containsExactly(awkward);
    }

    @Test
    @DisplayName("A note Obsidian has edited, or that was filed into a folder, is still found")
    void aNoteObsidianEditedOrFiledIsStillFound() throws IOException {
        // Editing any property in Obsidian's panel rewrites the front matter, title unquoted, and
        // filing a note into a folder moves it. Missing either would read as a deleted note, and
        // the meeting would be summarised again.
        Files.createDirectories(vault.resolve("Refinements"));
        Files.writeString(
                vault.resolve("Refinements/Edited.md"), "---\ntitle: Sprint Refinement\ntags:\n  - q4\n---\n\nBody.");
        Files.createDirectories(vault.resolve(".trash"));
        Files.writeString(vault.resolve(".trash/Gone.md"), "---\ntitle: \"Retro\"\n---\n");

        assertThat(provider().storedTitles()).containsExactly("Sprint Refinement");
    }

    @Test
    @DisplayName("A note whose front matter cannot be read throws rather than reading as deleted")
    void unreadableFrontMatterThrows() throws IOException {
        Files.writeString(vault.resolve("Broken.md"), "---\ntitle: [unclosed\n---\n");

        assertThatThrownBy(() -> provider().storedTitles())
                .isInstanceOf(UncheckedIOException.class)
                .hasMessageContaining("Broken.md");
    }

    @Test
    @DisplayName("A vault that is not there throws rather than reporting no notes")
    void aMissingVaultThrows() {
        // "No notes" would have the reconciler forget every meeting and re-summarise the folder.
        var missing =
                new MarkdownVaultKnowledgeProvider(vault.resolve("not-mounted").toString(), CLOCK);

        assertThatThrownBy(missing::storedTitles).isInstanceOf(UncheckedIOException.class);
    }

    @Test
    @DisplayName("The note links back to the recording it came from")
    void theNoteLinksBackToTheRecording() throws IOException {
        var page = provider().store(content("Sprint Refinement 2026-10-07"), SUMMARY);

        assertThat(noteAt(page.id())).contains("https://drive.google.com/file/d/abc123");
    }

    @Test
    @DisplayName("Front matter records the content type, the model and the prompt version")
    void frontMatterRecordsProvenance() throws IOException {
        // Obsidian reads YAML front matter as a note's properties, so this is what makes the
        // vault searchable by type and traceable to the model and prompt that produced it —
        // the same facts T14 records in the audit chain, readable without the application.
        var note = noteAt(provider()
                .store(content("Sprint Refinement 2026-10-07"), SUMMARY)
                .id());

        assertThat(note).startsWith("---\n");
        assertThat(note).containsSubsequence("---", "content-type:", "Sprint Refinement", "---");
        assertThat(note).contains("anthropic").contains("claude-sonnet-5").contains("prompt-version: 3");
    }

    @Test
    @DisplayName("The content type is a wiki link, so Obsidian groups notes by it")
    void theContentTypeIsAWikiLink() throws IOException {
        var note = noteAt(provider()
                .store(content("Sprint Refinement 2026-10-07"), SUMMARY)
                .id());

        assertThat(note).contains("Capture type: [[Sprint Refinement]]");
    }

    @Test
    @DisplayName("An undetermined type says so, and is not a wiki link")
    void anUndeterminedTypeSaysSo() throws IOException {
        // [[Unknown]] would give Obsidian a page collecting every note CKA could not identify,
        // which is a worse thing to have than no page. The line says which prompt ran instead.
        var undetermined = CapturedContent.builder()
                .sourceUri("https://drive.google.test/x")
                .title("Copy of Meeting started 2026/06/27 21:43 BST")
                .contentType(CapturedContent.UNKNOWN_CONTENT_TYPE)
                .transcript("Priya: hello.")
                .build();

        var note = noteAt(provider().store(undetermined, SUMMARY).id());

        assertThat(note).contains("Capture type: unknown, used default summary prompt");
        assertThat(note).doesNotContain("[[Unknown]]");
    }

    @Test
    @DisplayName("The note says who summarised it, not only the front matter")
    void theNoteSaysWhoSummarisedIt() throws IOException {
        // The front matter is Obsidian's property panel and the search index; this is for the
        // person reading the page, who should not have to open a drawer to learn which model
        // wrote what they are reading.
        var note =
                noteAt(provider().store(content("Sprint Refinement"), SUMMARY).id());

        assertThat(note).contains("Summarised by anthropic/claude-sonnet-5, prompt v3");
    }

    @Test
    @DisplayName("A summary's own headings are pushed below the section heading above them")
    void aSummarysOwnHeadingsArePushedBelow() throws IOException {
        // An LLM writes whatever heading levels it likes, and in practice starts at "#". Left
        // alone, its first heading renders *larger* than the "Summary" heading above it and the
        // page reads as three documents pasted together.
        var withHeadings = Summary.builder()
                .text("# Meeting Summary\n\n## What was decided\n\n- A thing.\n\n### Detail\n\nMore.")
                .producedBy(SUMMARY.producedBy())
                .promptVersion(1)
                .build();

        var note = noteAt(
                provider().store(content("Sprint Refinement"), withHeadings).id());

        assertThat(note).contains("\n# Summary\n");
        assertThat(note).contains("\n## Meeting Summary\n");
        assertThat(note).contains("\n### What was decided\n");
        assertThat(note).contains("\n#### Detail\n");
        // The model's structure survives the shift; only its depth changes.
        assertThat(note).doesNotContain("\n# Meeting Summary");
    }

    @Test
    @DisplayName("A comment inside a code block in a summary is not mistaken for a heading")
    void aCommentInACodeBlockIsNotAHeading() throws IOException {
        var withCode = Summary.builder()
                .text("# Decisions\n\n```bash\n# install deps\nnpm ci\n```")
                .producedBy(SUMMARY.producedBy())
                .promptVersion(1)
                .build();

        var note =
                noteAt(provider().store(content("Sprint Refinement"), withCode).id());

        assertThat(note).contains("## Decisions").contains("\n# install deps\n");
    }

    @Test
    @DisplayName("A summary that already starts below the section heading is left alone")
    void aSummaryThatAlreadyStartsBelowIsLeftAlone() throws IOException {
        var alreadyDeep = Summary.builder()
                .text("## Decisions\n\n- A thing.")
                .producedBy(SUMMARY.producedBy())
                .promptVersion(1)
                .build();

        var note = noteAt(
                provider().store(content("Sprint Refinement"), alreadyDeep).id());

        assertThat(note).contains("## Decisions").doesNotContain("### Decisions");
    }

    @Test
    @DisplayName("A title that would be an illegal filename is still written")
    void aTitleThatWouldBeAnIllegalFilenameIsStillWritten() {
        // Meeting titles are whatever someone typed into a calendar invite. A slash is a
        // directory separator, and the rest are rejected outright by Windows and by some
        // network filesystems — none of that should cost the team a captured meeting.
        var page = provider().store(content("Q3/Q4 planning: \"scope\" <draft> | v2?"), SUMMARY);

        assertThat(vault.resolve(page.id())).isRegularFile();
        // Spaces stay: legal everywhere, and what makes a vault readable. What has to go is the
        // set that is not legal — separators, and the characters Windows rejects outright.
        assertThat(page.id()).doesNotContain("/", "\\", ":", "*", "?", "\"", "<", ">", "|");
    }

    @Test
    @DisplayName("A title too long for a filesystem is cut on a character boundary")
    void aTitleTooLongForAFilesystemIsCut() {
        // Filesystems cap a name at 255 *bytes*, and a Japanese character is three of them — so
        // a title well short of 255 characters can still be too long, and cutting the string
        // rather than the bytes can slice a character in half and leave invalid UTF-8 in a name.
        var page = provider().store(content("会議".repeat(200)), SUMMARY);

        assertThat(vault.resolve(page.id())).isRegularFile();
        assertThat(page.id().getBytes(StandardCharsets.UTF_8)).hasSizeLessThan(255);
        assertThat(page.id()).doesNotContain("\uFFFD").endsWith(".md");
    }

    @Test
    @DisplayName("A title cannot write outside the vault")
    void aTitleCannotWriteOutsideTheVault() throws IOException {
        // The title reaches here from a capture provider, which got it from a meeting platform.
        // Treating it as a path would make "../" in a calendar invite enough to write anywhere
        // the backend can reach.
        var page = provider().store(content("../../etc/passwd"), SUMMARY);

        assertThat(vault.resolve(page.id()).normalize()).startsWith(vault);
        try (var inVault = Files.walk(vault)) {
            assertThat(inVault.filter(Files::isRegularFile))
                    .allSatisfy(f -> assertThat(f).hasParent(vault));
        }
    }

    @Test
    @DisplayName("A second item with the same title does not overwrite the first")
    void aSecondItemWithTheSameTitleDoesNotOverwriteTheFirst() throws IOException {
        var first = provider().store(content("Stand-up"), SUMMARY);
        var second = provider()
                .store(
                        content("Stand-up"),
                        Summary.builder()
                                .text("A different summary.")
                                .producedBy(SUMMARY.producedBy())
                                .promptVersion(SUMMARY.promptVersion())
                                .build());

        assertThat(first.id()).isEqualTo("Stand-up.md");
        // Underscored, for two reasons. A title often carries a date of its own, so a
        // space-joined suffix would leave a reader guessing which date is the meeting's; and an
        // underscore needs no escaping in either of Obsidian's link styles, where a bracket
        // would end a [text](path) link early.
        // Named for when it was stored, so the note says which stand-up it was rather than only
        // that it was not the first. The clock is pinned, so this is the value and not a shape.
        assertThat(second.id()).isEqualTo("Stand-up_2026-11-08_143205.md");
        // Nothing in the name needs escaping in a Markdown link target beyond the spaces the
        // title itself contributes, which Obsidian encodes.
        assertThat(second.id()).doesNotContain("(", ")", "[", "]");
        assertThat(noteAt(first.id())).contains("- #142 — agreed done, to be closed.");
        assertThat(noteAt(second.id())).contains("A different summary.");
    }

    @Test
    @DisplayName("A vault directory that does not exist yet is created")
    void aVaultDirectoryThatDoesNotExistIsCreated() {
        // The volume is mounted empty on a fresh clone, and a missing directory must not cost
        // the first captured meeting — the same gap SqlitePersistenceConfiguration fills.
        var nested = vault.resolve("not/created/yet");

        var page = new MarkdownVaultKnowledgeProvider(nested.toString(), CLOCK).store(content("Stand-up"), SUMMARY);

        assertThat(nested.resolve(page.id())).isRegularFile();
    }

    @Test
    @DisplayName("A stored note leaves nothing else in the vault")
    void aStoredNoteLeavesNothingElseInTheVault() throws IOException {
        // The note is written to a temporary file and moved into place, so the vault must hold
        // the note and nothing else — a .cka-writing- file left behind would sit in somebody's
        // Obsidian folder, and an empty .md would show there as a blank note.
        provider().store(content("Sprint Refinement 2026-10-07"), SUMMARY);

        try (var files = Files.list(vault)) {
            assertThat(files).singleElement().satisfies(f -> {
                assertThat(f.getFileName().toString()).isEqualTo("Sprint Refinement 2026-10-07.md");
                assertThat(f).isNotEmptyFile();
            });
        }
    }

    @Test
    @DisplayName("The note is readable by someone other than the process that wrote it")
    void theNoteIsReadableByOthers() throws IOException {
        // The whole point of a vault on a mounted volume is that a person opens it on the host
        // while CKA writes from inside a container — a different account on the same machine.
        // createTempFile creates owner-only and move carries that through, so without widening
        // it the note exists and nobody else can read it, which an acceptance run found on Linux
        // and a Mac never shows because Docker Desktop maps the uid.
        var page = provider().store(content("Sprint Refinement 2026-10-07"), SUMMARY);

        var permissions = Files.getPosixFilePermissions(vault.resolve(page.id()));
        assertThat(permissions).contains(PosixFilePermission.GROUP_READ, PosixFilePermission.OTHERS_READ);
    }

    @Test
    @DisplayName("The health check reports up when the vault can be written to")
    void theHealthCheckReportsUpWhenTheVaultCanBeWrittenTo() {
        var health = provider().checkHealth();

        assertThat(health.up()).isTrue();
        assertThat(health.detail()).contains(vault.toString());
    }

    @Test
    @DisplayName("The health check reports down, and says why, when the vault is a file")
    void theHealthCheckReportsDownWhenTheVaultIsAFile() throws IOException {
        // A mount gone wrong is the realistic failure: the path resolves to something that is
        // not a directory, and every write would fail. checkHealth reports; it must not throw.
        var notADirectory = Files.writeString(vault.resolve("oops.txt"), "not a vault");

        var health = new MarkdownVaultKnowledgeProvider(notADirectory.toString(), CLOCK).checkHealth();

        assertThat(health.up()).isFalse();
        assertThat(health.detail()).contains(notADirectory.toString());
    }

    @Test
    @DisplayName("The health check leaves nothing behind in the vault")
    void theHealthCheckLeavesNothingBehind() throws IOException {
        var subject = provider();
        subject.checkHealth();
        subject.checkHealth();

        try (var files = Files.list(vault)) {
            assertThat(files).isEmpty();
        }
    }
}
