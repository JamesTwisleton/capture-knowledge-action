package com.cka.provider.capture;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The fixture below is the shape of a real export, taken from an actual Gemini notes document:
 * a byte-order mark, the title, the two tab names run together, the notes headings, then the
 * transcript tab starting with its own date and a timestamp. Only the shape is real — the words
 * are invented, because a meeting's contents do not belong in a repository.
 */
@DisplayName("A Gemini notes document splits into its notes and its transcript")
class GeminiNotesTest {

    private static final String EXPORT = """
            \uFEFFJun 27, 2026
            Meeting Jun 27, 2026 at 21:43 BST
            Meeting records Transcript

            Summary
            The team talked about the login bug and agreed it was a duplicate.

            Decisions
            Close #142.

            Next steps
            Sofia to re-estimate #156.

            Details
            Jun 27, 2026
            00:00:00
            Priya Raman: Right, shall we start with one four two.
            00:01:01
            Tom Albrecht: It is merged and verified, so it can be closed.
            """;

    @Test
    @DisplayName("The transcript is everything from the first timestamp down")
    void theTranscriptIsEverythingFromTheFirstTimestamp() {
        var notes = GeminiNotes.parse(EXPORT);

        assertThat(notes.hasTranscript()).isTrue();
        assertThat(notes.transcript())
                .startsWith("00:00:00")
                .contains("Priya Raman:")
                .contains("Tom Albrecht:");
    }

    @Test
    @DisplayName("The notes are everything above it, and carry no transcript")
    void theNotesAreEverythingAboveIt() {
        var notes = GeminiNotes.parse(EXPORT);

        assertThat(notes.summary()).contains("Summary").contains("Decisions").contains("Next steps");
        assertThat(notes.summary()).doesNotContain("00:00:00").doesNotContain("Priya Raman:");
    }

    @Test
    @DisplayName("The split is not made on the word \"Transcript\", which is a tab name at the top")
    void theSplitIsNotMadeOnTheWordTranscript() {
        // "Meeting records Transcript" is line three of every one of these documents. Splitting
        // there would leave two lines of notes and call the whole summary a transcript.
        var notes = GeminiNotes.parse(EXPORT);

        assertThat(notes.summary()).contains("Meeting records Transcript");
        assertThat(notes.transcript()).doesNotContain("Meeting records Transcript");
    }

    @Test
    @DisplayName("The byte-order mark Drive prepends does not end up inside the first heading")
    void theByteOrderMarkIsRemoved() {
        assertThat(GeminiNotes.parse(EXPORT).summary()).startsWith("Jun 27, 2026");
    }

    @Test
    @DisplayName("A document with no timestamps has no transcript, rather than a guessed one")
    void aDocumentWithNoTimestampsHasNoTranscript() {
        // Gemini's format is read off its output, not from a specification, so it can change.
        // Handing a notes page to the summariser as though it were speech is worse than saying
        // there is nothing here, so the watcher waits.
        var notes = GeminiNotes.parse("Summary\nWe agreed the thing.\n");

        assertThat(notes.hasTranscript()).isFalse();
        assertThat(notes.summary()).contains("We agreed the thing.");
    }

    @Test
    @DisplayName("An empty or absent document is empty, not an exception")
    void anEmptyDocumentIsEmpty() {
        assertThat(GeminiNotes.parse(null).hasTranscript()).isFalse();
        assertThat(GeminiNotes.parse("   ").hasTranscript()).isFalse();
    }

    @Test
    @DisplayName("Drive's CRLF line endings and byte-order mark do not reach the transcript")
    void crlfAndByteOrderMarkAreRemoved() {
        var notes = GeminiNotes.parse("\uFEFFSummary\r\nAgreed.\r\n00:00:00\r\nPriya: done.\r\n");

        assertThat(notes.summary()).isEqualTo("Summary\nAgreed.");
        assertThat(notes.transcript()).isEqualTo("00:00:00\nPriya: done.");
        assertThat(GeminiNotes.cleaned("\uFEFF")).isBlank();
    }
}
