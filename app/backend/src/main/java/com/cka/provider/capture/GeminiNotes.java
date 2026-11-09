package com.cka.provider.capture;

import java.util.regex.Pattern;

/**
 * One Gemini meeting-notes document, split into the two things the pipeline wants from it.
 *
 * <p><strong>Why this exists.</strong> Gemini does not write a transcript file beside the notes;
 * it writes <em>one</em> Google Doc with two tabs — "Meeting records" and "Transcript" — and
 * Drive's plain-text export flattens both into a single stream. So a folder that looks like it
 * holds only notes is in fact holding the transcript too, and a watcher waiting for a
 * "– Transcript" file beside it waits forever.
 *
 * <p>Observed rather than documented, from a real export:
 *
 * <pre>
 *   Jun 27, 2026                        the document's own date
 *   Meeting Jun 27, 2026 at 21:43 BST   its title
 *   Meeting records Transcript          the tab names, run together
 *   Summary                             ─┐
 *   Decisions                            │ the "Meeting records" tab
 *   Next steps                           │
 *   Details                             ─┘
 *   Jun 27, 2026                        the "Transcript" tab starts
 *   00:00:00                            ─┐ a timestamp every minute or so,
 *   Priya Raman: …                       │ then who said what
 *   00:01:01                            ─┘
 * </pre>
 *
 * <p>The split is made on the first timestamp line rather than on the word "Transcript", which
 * appears in the tab list at the top and would cut the document at its second line. A format
 * read off one vendor's output is a guess that happens to be checkable, so
 * {@link #hasTranscript()} lets the caller treat an unrecognised document as having none rather
 * than hand half a notes page to an LLM as though it were speech.
 */
record GeminiNotes(String summary, String transcript) {

    /** {@code 00:00:00}, on its own line: where the transcript tab's content starts. */
    private static final Pattern TIMESTAMP = Pattern.compile("^\\d{1,2}:\\d{2}:\\d{2}$");

    /**
     * Everything above the first timestamp is the notes tab, everything from it down is the
     * transcript. Both are returned trimmed, and either may be blank — a document with no
     * timestamp at all has no transcript, which is the honest answer rather than a guess.
     */
    static GeminiNotes parse(String exported) {
        var lines = cleaned(exported).lines().toList();

        var start = -1;
        for (var i = 0; i < lines.size(); i++) {
            if (TIMESTAMP.matcher(lines.get(i).strip()).matches()) {
                start = i;
                break;
            }
        }
        if (start < 0) {
            return new GeminiNotes(String.join("\n", lines).strip(), "");
        }
        return new GeminiNotes(
                String.join("\n", lines.subList(0, start)).strip(),
                String.join("\n", lines.subList(start, lines.size())).strip());
    }

    /**
     * Drive's plain-text export, as plain text: without the byte-order mark it begins with, which
     * would otherwise sit inside the first heading (and make a transcript of nothing but that mark
     * look non-blank), and with {@code \n} for the {@code \r\n} it ends each line with. Empty for
     * nothing at all, so a caller can ask {@code isBlank} of a document that could not be read.
     */
    static String cleaned(String exported) {
        if (exported == null) {
            return "";
        }
        return String.join("\n", exported.replace("\uFEFF", "").lines().toList());
    }

    boolean hasTranscript() {
        return !transcript.isBlank();
    }
}
