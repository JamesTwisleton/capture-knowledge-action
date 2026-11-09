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
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;
import org.yaml.snakeyaml.error.YAMLException;

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

    /**
     * Second resolution, and no colons — those are illegal in filenames on Windows, which rules
     * out plain ISO-8601.
     *
     * <p>Joined with underscores, and attached with one, so the suffix reads as a unit against a
     * title made of spaces — the underscores are the suffix's, never the title's, which is left
     * exactly as it was found. Teams put dates in meeting titles
     * already, so a space-joined suffix would leave a reader two dates and no way to tell which
     * is the meeting's.
     *
     * <p><strong>Underscores and not brackets, because links come first.</strong> Parentheses are
     * safe inside {@code [[wiki links]]}, which is what Obsidian writes by default — but a vault
     * with "Use [[Wikilinks]]" switched off writes {@code [text](path)} instead, where an
     * unescaped bracket in the path ends the link early. An underscore needs no escaping in
     * either form. Spaces in a name are unavoidable, since they come from the title, and Obsidian
     * encodes those itself; adding a character it has to escape is the avoidable part.
     */
    private static final DateTimeFormatter SUFFIX = DateTimeFormatter.ofPattern("yyyy-MM-dd_HHmmss");

    /**
     * How much of a note is read to find its front matter, which is at the top by definition. A
     * transcript is tens of thousands of characters and there are a few hundred notes.
     */
    private static final int FRONT_MATTER_SCAN_BYTES = 16 * 1024;

    /** A Markdown ATX heading: the hashes at the start of a line, and the space after them. */
    private static final Pattern HEADING = Pattern.compile("^(#{1,6})\\s");

    /** Where a fenced code block opens or closes, inside which nothing is a heading. */
    private static final Pattern CODE_FENCE = Pattern.compile("^ {0,3}(```|~~~)");

    /** The level this note's own section headings use, so content inside one starts below it. */
    private static final int SECTION_LEVEL = 1;

    private final Path vault;

    /** Injected rather than {@code Instant.now()}, as {@code AuditTrail}'s is, so a test can pin it. */
    private final Clock clock;

    public MarkdownVaultKnowledgeProvider(@Value("${cka.vault.path}") String vaultPath, Clock clock) {
        this.vault = Path.of(vaultPath).toAbsolutePath().normalize();
        this.clock = clock;
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
     * Read back out of the notes themselves, from the {@code title} the front matter records,
     * rather than from the filenames. A filename is this provider's own invention — illegal
     * characters replaced, a clashing one given a timestamp — so deriving a title from one would
     * be undoing a transformation that is deliberately not reversible. The front matter holds
     * the title exactly as it arrived.
     *
     * <p>Every note on every call, subfolders included, because filing a note into a folder is
     * an ordinary thing to do in a vault and must not read as deleting it. Folders whose name
     * starts with a dot are skipped — {@code .obsidian} is configuration and {@code .trash} is
     * where Obsidian puts what a person deleted. A vault is a few hundred files, read once per
     * poll interval, and a cache would be a second thing to keep true about a folder whose
     * entire point is that a person edits it behind CKA's back.
     *
     * <p>Throws rather than answering for a vault it cannot fully read — one that is missing, or
     * a note that cannot be read or whose front matter does not parse. Each of those is "I cannot
     * tell", and answering as though the note were absent would forget its meeting and summarise
     * it again; a missing vault would do that for every meeting at once.
     */
    @Override
    public Set<String> storedTitles() {
        try (var paths = Files.walk(vault)) {
            return paths.filter(path -> path.getFileName().toString().endsWith(".md"))
                    .filter(path -> !inHiddenFolder(path))
                    .filter(Files::isRegularFile)
                    .map(this::titleOf)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toUnmodifiableSet());
        } catch (IOException e) {
            throw new UncheckedIOException("Could not read the vault at " + vault, e);
        }
    }

    private boolean inHiddenFolder(Path note) {
        var folder = vault.relativize(note).getParent();
        if (folder == null) {
            return false;
        }
        for (var part : folder) {
            if (part.toString().startsWith(".")) {
                return true;
            }
        }
        return false;
    }

    /**
     * The front matter's {@code title}, or {@code null} for a note with no front matter or no
     * title in it — somebody's own note. Read as YAML rather than matched as a line, because
     * Obsidian rewrites the front matter whenever a property is edited in its panel, and it
     * writes a plain title unquoted.
     *
     * <p>Decoded leniently: the scan stops at a byte count, which can fall inside a character,
     * and a stray byte further down a transcript has nothing to do with the title.
     */
    private String titleOf(Path note) {
        String head;
        try (var in = Files.newInputStream(note)) {
            head = new String(in.readNBytes(FRONT_MATTER_SCAN_BYTES), StandardCharsets.UTF_8).replace("\uFEFF", "");
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        var lines = head.lines().toList();
        if (lines.isEmpty() || !lines.getFirst().strip().equals("---")) {
            return null;
        }
        var end = -1;
        for (int i = 1; i < lines.size(); i++) {
            if (lines.get(i).strip().equals("---")) {
                end = i;
                break;
            }
        }
        if (end < 0) {
            return null;
        }
        Object frontMatter;
        try {
            frontMatter =
                    new Yaml(new SafeConstructor(new LoaderOptions())).load(String.join("\n", lines.subList(1, end)));
        } catch (YAMLException e) {
            throw new UncheckedIOException(new IOException("Unreadable front matter in " + vault.relativize(note), e));
        }
        if (frontMatter instanceof Map<?, ?> properties && properties.get("title") != null) {
            return String.valueOf(properties.get("title"));
        }
        return null;
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

        // A wiki link for a type a team actually uses, because Obsidian then lists every note of
        // that type on one page. Not for an undetermined one: [[Unknown]] would make a page
        // collecting everything CKA could not identify, which is a worse thing to have than no
        // page — and the line says plainly which prompt summarised it instead.
        sb.append("# ").append(content.title()).append("\n\n");
        sb.append(
                        CapturedContent.UNKNOWN_CONTENT_TYPE.equals(content.contentType())
                                ? "Capture type: unknown, used default summary prompt"
                                : "Capture type: [[" + content.contentType() + "]]")
                .append(" · [Recording](")
                .append(content.sourceUri())
                .append(")\n\n");

        // In the note as well as the front matter: the front matter is for Obsidian's property
        // panel and for searching, and this is for the person reading the page, who should not
        // have to open a properties drawer to find out which model wrote what they are reading.
        sb.append("Summarised by ")
                .append(summary.producedBy().provider())
                .append("/")
                .append(summary.producedBy().model())
                .append(", prompt v")
                .append(summary.promptVersion())
                .append("\n\n");

        sb.append("# Summary\n\n").append(demoted(summary.text())).append("\n\n");

        if (content.platformSummary() != null) {
            sb.append("# The platform's own summary\n\n")
                    .append(demoted(content.platformSummary()))
                    .append("\n\n");
        }

        return sb.append("# Transcript\n\n")
                .append(content.transcript())
                .append('\n')
                .toString();
    }

    /**
     * Pushes a block's own headings below the section heading it sits under, so the note reads
     * as one document rather than as three pasted together.
     *
     * <p>An LLM writes whatever heading levels it likes, and both of the ones here do start at
     * {@code #} — so a summary dropped in verbatim renders its first heading <em>larger</em> than
     * the "Summary" heading above it, and its second at the same size. The shape of the page
     * then depends on what the model felt like today, which is not a thing to leave to chance.
     *
     * <p>Shifted by the whole block's shallowest heading rather than per line, so the relative
     * structure the model chose survives: a document using {@code #} and {@code ##} becomes
     * {@code ##} and {@code ###}, and one that already starts at {@code ##} is left alone.
     * Clamped at six, below which Markdown has no headings left.
     */
    private String demoted(String markdown) {
        var lines = markdown.lines().toList();
        var levels = headingLevels(lines);
        var shallowest = Arrays.stream(levels).filter(level -> level > 0).min().orElse(0);
        if (shallowest == 0 || shallowest >= SECTION_LEVEL + 1) {
            return markdown;
        }
        var shift = SECTION_LEVEL + 1 - shallowest;
        return IntStream.range(0, lines.size())
                .mapToObj(i -> levels[i] == 0
                        ? lines.get(i)
                        : "#".repeat(Math.min(6, levels[i] + shift))
                                + lines.get(i).substring(levels[i]))
                .collect(Collectors.joining("\n"));
    }

    /**
     * Each line's heading level, or zero for a line that is not a heading — which includes a
     * {@code #} inside a fenced code block. A summary of a meeting about a deploy script carries
     * that script's shell comments, and demoting those would change the code a person copies.
     */
    private static int[] headingLevels(List<String> lines) {
        var levels = new int[lines.size()];
        var fenced = false;
        for (int i = 0; i < lines.size(); i++) {
            if (CODE_FENCE.matcher(lines.get(i)).find()) {
                fenced = !fenced;
                continue;
            }
            var heading = HEADING.matcher(lines.get(i));
            if (!fenced && heading.find()) {
                levels[i] = heading.group(1).length();
            }
        }
        return levels;
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
     * The name a note gets, <em>claimed</em> by creating the file rather than by asking whether it
     * exists. Two meetings called "Stand-up" are the normal case, not a conflict, and the second
     * must not overwrite the first — knowledge that silently replaces knowledge is worse than none.
     *
     * <p>A clash is disambiguated by when the note was stored. The title is not rewritten — the
     * stamp is appended to it, so {@code Stand-up.md} is followed by {@code Stand-up} +
     * {@code _2026-11-08_143205} + {@code .md}. That says which stand-up it was, where a counter
     * would only say it was not the first. The counter survives as the last resort for two notes
     * stored in the same second, the one case a timestamp cannot separate.
     *
     * <p>Claiming rather than checking is what makes any of it hold under concurrency. Asking
     * {@code Files.exists} and then writing leaves a window in which two writers agree on the
     * same free name, and the move that follows replaces rather than fails.
     * {@code Files.createFile} is atomic: exactly one caller gets the name.
     */
    private Path claimFilename(String filename) throws IOException {
        var base = filename.substring(0, filename.length() - ".md".length());
        var stamped = base + "_" + SUFFIX.format(LocalDateTime.now(clock));
        for (int n = 1; ; n++) {
            var name = switch (n) {
                case 1 -> filename;
                case 2 -> stamped + ".md";
                default -> stamped + "_" + (n - 1) + ".md";
            };
            try {
                return Files.createFile(vault.resolve(name));
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
