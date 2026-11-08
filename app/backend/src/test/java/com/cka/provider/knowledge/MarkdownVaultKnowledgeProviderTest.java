package com.cka.provider.knowledge;

import static org.assertj.core.api.Assertions.assertThat;

import com.cka.core.CapturedContent;
import com.cka.core.Provenance;
import com.cka.core.Summary;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * T06 against a real directory. There is nothing to mock: the whole of this provider is what it
 * writes and where, so a stubbed filesystem would assert the test's own idea of the answer.
 */
@DisplayName("The Markdown vault writes one readable note per captured item")
class MarkdownVaultKnowledgeProviderTest {

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
        return new MarkdownVaultKnowledgeProvider(vault.toString());
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
                .contains("## Summary")
                .contains("## Transcript");
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

        assertThat(note).contains("[[Sprint Refinement]]");
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
        assertThat(page.id().getBytes(java.nio.charset.StandardCharsets.UTF_8)).hasSizeLessThan(255);
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

        assertThat(second.id()).isNotEqualTo(first.id());
        assertThat(noteAt(first.id())).contains("- #142 — agreed done, to be closed.");
        assertThat(noteAt(second.id())).contains("A different summary.");
    }

    @Test
    @DisplayName("A vault directory that does not exist yet is created")
    void aVaultDirectoryThatDoesNotExistIsCreated() {
        // The volume is mounted empty on a fresh clone, and a missing directory must not cost
        // the first captured meeting — the same gap SqlitePersistenceConfiguration fills.
        var nested = vault.resolve("not/created/yet");

        var page = new MarkdownVaultKnowledgeProvider(nested.toString()).store(content("Stand-up"), SUMMARY);

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

        var health = new MarkdownVaultKnowledgeProvider(notADirectory.toString()).checkHealth();

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
