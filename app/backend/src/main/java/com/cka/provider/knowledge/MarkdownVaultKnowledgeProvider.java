package com.cka.provider.knowledge;

import com.cka.core.CapturedContent;
import com.cka.core.PageReference;
import com.cka.core.Summary;
import com.cka.provider.ProviderHealth;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.PosixFilePermissions;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * The knowledge store as a folder of Markdown files (T06): one note per captured item, with YAML
 * front matter Obsidian reads as properties and a wiki link to the content type.
 *
 * <p><strong>Obsidian-compatible, not Obsidian-dependent.</strong> Nothing here knows Obsidian
 * exists — the output is plain Markdown that happens to follow conventions Obsidian understands,
 * so the vault opens in it, in any text editor, and directly in an LLM's context window. That is
 * the PRD's reason for choosing a folder of files over a database (7.3).
 *
 * <p>Nothing reads these notes back. Mention detection works from the {@code Knowledge} that
 * {@code KnowledgeStage} puts on the bus, so the format serves people and Obsidian rather than
 * the pipeline — which is what makes it safe to change.
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "cka.provider", name = "knowledge", havingValue = "markdown-vault")
public class MarkdownVaultKnowledgeProvider implements KnowledgeProvider {

    /**
     * Characters no filename may contain. The union of what Windows rejects and what POSIX
     * treats as a separator, because a vault is a folder someone syncs between machines — a note
     * that only exists on Linux is a note lost on the next person's laptop.
     */
    private static final String ILLEGAL_IN_FILENAMES = "[\\\\/:*?\"<>|\\x00-\\x1f]";

    /**
     * Bytes, not characters, and that distinction is the whole point: ext4, APFS and NTFS all
     * cap a single name at 255 <em>bytes</em>, while a character can be three of them in UTF-8.
     * A meeting title in Japanese, or an English one carrying an em dash, hits the limit at a
     * third of the length a character count would allow — and the failure is an
     * {@code ENAMETOOLONG} from the filesystem, which costs the team the whole meeting.
     *
     * <p>180 leaves room for the extension and a de-duplicating suffix. Titles are calendar
     * invites, and some of them are paragraphs.
     */
    private static final int MAX_TITLE_BYTES = 180;

    private final Path vault;

    public MarkdownVaultKnowledgeProvider(@Value("${cka.vault.path}") String vaultPath) {
        this.vault = Path.of(vaultPath).toAbsolutePath().normalize();
    }

    @Override
    public PageReference store(CapturedContent content, Summary summary) {
        Path note = null;
        Path partial = null;
        try {
            Files.createDirectories(vault);
            note = claimFilename(filenameFor(content.title()));
            partial = writeToTempFile(render(content, summary));
            // ATOMIC_MOVE alone: every other option is ignored when it is present, and on every
            // filesystem this runs on it replaces the target — which is the empty file claimed
            // above, never somebody else's note.
            Files.move(partial, note, StandardCopyOption.ATOMIC_MOVE);
            partial = null;
            var written = note;
            note = null;
            return PageReference.builder()
                    .id(vault.relativize(written).toString())
                    .location(written.toUri().toString())
                    .build();
        } catch (IOException e) {
            // Nothing half-finished is left in somebody's vault: not the temporary file, and not
            // the empty note whose name was claimed before the content existed.
            discard(partial);
            discard(note);
            // Thrown, not reported: KnowledgeStage publishes knowledge stored as soon as this
            // returns, and the Action stage acts on it. A page reference for a note that was
            // never written is an action pointing at nothing.
            throw new UncheckedIOException("Could not write a note to the vault at " + vault, e);
        }
    }

    private void discard(Path leftover) {
        if (leftover == null) {
            return;
        }
        try {
            Files.deleteIfExists(leftover);
        } catch (IOException e) {
            log.warn("Left {} behind in the vault after a failed write", leftover, e);
        }
    }

    /**
     * A real write, not {@code Files.isWritable}, which answers a different question: whether
     * permission would allow a write, not whether a note can be stored. Measured — it returns
     * {@code true} for a {@code VAULT_PATH} that points at a regular file, where the write fails
     * with "Not a directory", and {@code true} for a full filesystem, where it fails with "No
     * space left on device". (It does catch a read-only mount, which the permission check
     * reaches.) A misconfigured path and a full disk are the two ways this breaks in a real
     * deployment, and they are exactly the two it cannot see.
     *
     * <p>The probe is named with a leading dot, which Obsidian ignores, and deleted in a
     * {@code finally} — the vault is somebody's own folder, and the front end polls this.
     */
    @Override
    public ProviderHealth checkHealth() {
        Path probe = null;
        try {
            Files.createDirectories(vault);
            probe = Files.createTempFile(vault, ".cka-health-", ".tmp");
            Files.writeString(probe, "ok");
            return ProviderHealth.up("vault writable at " + vault);
        } catch (Exception e) {
            return ProviderHealth.down("cannot write to the vault at " + vault + ": " + e.getMessage());
        } finally {
            if (probe != null) {
                try {
                    Files.deleteIfExists(probe);
                } catch (IOException e) {
                    log.warn("Left a health probe file behind in the vault: {}", probe, e);
                }
            }
        }
    }

    /**
     * The content, written somewhere it cannot be mistaken for a note. Obsidian watches the vault
     * and opens what it finds, so writing straight to the final name is a note someone can open
     * half-finished; the caller moves this into place instead, which is atomic.
     *
     * <p>The {@code force} is what makes the note durable before {@code store} returns. Writing a
     * file only hands the bytes to the operating system, and {@code KnowledgeStage} announces the
     * write the moment this method's caller returns.
     */
    private Path writeToTempFile(String markdown) throws IOException {
        var partial = Files.createTempFile(vault, ".cka-writing-", ".tmp");
        makeReadable(partial);
        try (var channel = Files.newByteChannel(partial, StandardOpenOption.WRITE)) {
            channel.write(StandardCharsets.UTF_8.encode(markdown));
            if (channel instanceof FileChannel file) {
                file.force(true);
            }
        }
        return partial;
    }

    /**
     * Widens the note's permissions, because {@code createTempFile} deliberately creates with
     * owner-only access and {@code move} carries that through to the note. A vault nobody but
     * the writing process can read is the one thing this provider must not produce: CKA writes
     * from inside a container and a person opens the result on the host, so the note has to be
     * readable by a different account on the same machine.
     *
     * <p>Set on the temporary file rather than after the move, so the note is never visible in
     * the vault with the wrong permissions on it. Skipped where the filesystem has no POSIX
     * permissions to set — Windows carries none, and there is nothing to widen.
     */
    private void makeReadable(Path file) throws IOException {
        if (!file.getFileSystem().supportedFileAttributeViews().contains("posix")) {
            return;
        }
        Files.setPosixFilePermissions(file, PosixFilePermissions.fromString("rw-r--r--"));
    }

    /**
     * The note itself. Front matter first, because Obsidian only reads properties from the very
     * top of a file; then the summary, which is what a person came for; then the transcript it
     * was made from.
     */
    private String render(CapturedContent content, Summary summary) {
        var sb = new StringBuilder();
        sb.append("---\n")
                .append("content-type: ")
                .append(yaml(content.contentType()))
                .append('\n')
                .append("title: ")
                .append(yaml(content.title()))
                .append('\n')
                .append("source: ")
                .append(yaml(content.sourceUri()))
                .append('\n')
                .append("summarised-by: ")
                .append(yaml(summary.producedBy().provider() + "/"
                        + summary.producedBy().model()))
                .append('\n')
                .append("prompt-version: ")
                .append(summary.promptVersion())
                .append('\n')
                .append("---\n\n");

        // A wiki link rather than a tag: Obsidian shows every note of a type on one page, so the
        // vault organises itself by content type without anything here maintaining an index.
        sb.append("# ")
                .append(content.title())
                .append("\n\n")
                .append("Type: [[")
                .append(content.contentType())
                .append("]] · ")
                .append("[Recording](")
                .append(content.sourceUri())
                .append(")\n\n");

        sb.append("## Summary\n\n").append(summary.text()).append("\n\n");

        if (content.platformSummary() != null) {
            sb.append("## The platform's own summary\n\n")
                    .append(content.platformSummary())
                    .append("\n\n");
        }

        // Last, and deliberately: it is the longest part and the least often read, but it is the
        // evidence behind everything above it, so it is kept in the same file rather than linked
        // from one — a vault of notes that depend on a second file is a vault that can lose half.
        return sb.append("## Transcript\n\n")
                .append(content.transcript())
                .append('\n')
                .toString();
    }

    /**
     * A filename from a title someone typed into a calendar invite. Illegal characters go, the
     * path separators with them — a title is never a path, and {@code ../} in a meeting name
     * must not reach a directory the vault does not own.
     */
    private String filenameFor(String title) {
        var cleaned = title.replaceAll(ILLEGAL_IN_FILENAMES, " ")
                .replaceAll("\\s+", " ")
                .replaceAll("^[.\\s]+|[.\\s]+$", "")
                .trim();
        cleaned = truncateToBytes(cleaned).trim();
        // A title that was nothing but punctuation still has to go somewhere findable.
        return (cleaned.isEmpty() ? "Untitled" : cleaned) + ".md";
    }

    /**
     * The first name not already taken, counting up as Obsidian itself does, and <em>claimed</em>
     * by creating the file rather than by asking whether it exists. Two meetings called "Stand-up"
     * are the normal case, not a conflict, and the second must not overwrite the first — knowledge
     * that silently replaces knowledge is worse than none.
     *
     * <p>Claiming rather than checking is what makes that true under concurrency. Asking
     * {@code Files.exists} and then writing leaves a window in which two writers agree on the
     * same free name, and the move that follows replaces rather than fails.
     * {@code Files.createFile} is atomic: exactly one caller gets the name.
     */
    private Path claimFilename(String filename) throws IOException {
        var base = filename.substring(0, filename.length() - ".md".length());
        for (int n = 1; ; n++) {
            var candidate = vault.resolve(n == 1 ? filename : base + " " + n + ".md");
            try {
                return Files.createFile(candidate);
            } catch (FileAlreadyExistsException taken) {
                // Someone else has this one. Next.
            }
        }
    }

    /**
     * Cuts a title to {@link #MAX_TITLE_BYTES} without cutting a character in half. Truncating
     * the {@code String} would do exactly that: a name sliced mid-sequence is invalid UTF-8,
     * which the filesystem stores and every editor renders as a replacement character.
     *
     * <p>In UTF-8 every byte after the first in a character matches {@code 10xxxxxx}, and no
     * first byte does — so stepping back while that pattern holds lands on a character boundary,
     * dropping the whole character rather than part of it.
     */
    private String truncateToBytes(String title) {
        var bytes = title.getBytes(StandardCharsets.UTF_8);
        if (bytes.length <= MAX_TITLE_BYTES) {
            return title;
        }
        var end = MAX_TITLE_BYTES;
        while (end > 0 && (bytes[end] & 0xC0) == 0x80) {
            end--;
        }
        return new String(bytes, 0, end, StandardCharsets.UTF_8);
    }

    /**
     * Quotes every scalar. A title containing a colon is valid YAML's most common accident —
     * {@code title: Sprint Refinement: part 2} is a parse error, and Obsidian would show the
     * note with no properties at all rather than complain.
     */
    private String yaml(String value) {
        return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }
}
