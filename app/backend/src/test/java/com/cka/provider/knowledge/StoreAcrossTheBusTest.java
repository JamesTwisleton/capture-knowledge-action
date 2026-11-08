package com.cka.provider.knowledge;

import static org.assertj.core.api.Assertions.assertThat;

import com.cka.core.CapturedContent;
import com.cka.core.Provenance;
import com.cka.core.Summary;
import com.cka.core.event.ContentSummarised;
import com.cka.core.event.KnowledgeStored;
import com.cka.pipeline.KnowledgeStage;
import com.cka.provider.llm.LlmProvider;
import com.cka.testsupport.InMemoryEventBus;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * T06's acceptance criterion: a content-summarised event produces a readable note in the vault
 * and a knowledge-stored event on the bus, "with no direct call from the publisher".
 *
 * <p>That last clause is what this test is shaped around. No {@link LlmProvider} appears — it is
 * imported only to be named here — so the write cannot have been triggered by anything but the
 * event. The publisher in the real pipeline is T12, and the two are joined by the bus alone.
 */
@DisplayName("A summarised content event writes a note and announces it, with no publisher present")
class StoreAcrossTheBusTest {

    @TempDir
    Path vault;

    private final InMemoryEventBus bus = new InMemoryEventBus();

    @Test
    @DisplayName("Publishing content summarised writes the note and publishes knowledge stored")
    void publishingContentSummarisedWritesTheNoteAndAnnouncesIt() throws IOException {
        var stored = new ArrayList<KnowledgeStored>();
        bus.subscribe(KnowledgeStored.class, stored::add);
        new KnowledgeStage(new MarkdownVaultKnowledgeProvider(vault.toString()), bus).start();

        var chainId = UUID.randomUUID();
        var content = CapturedContent.builder()
                .sourceUri("https://drive.google.com/file/d/abc123")
                .title("Sprint Refinement 2026-10-07")
                .contentType("Sprint Refinement")
                .transcript("Priya: #142 is merged and verified, so it can be closed.")
                .platformSummary("The team agreed #142 is done.")
                .build();
        var summary = Summary.builder()
                .text("- #142 — agreed done, to be closed.")
                .producedBy(Provenance.builder()
                        .provider("anthropic")
                        .model("claude-sonnet-5")
                        .build())
                .promptVersion(3)
                .build();

        bus.publish(ContentSummarised.builder()
                .chainId(chainId)
                .content(content)
                .summary(summary)
                .build());

        assertThat(stored).hasSize(1);
        var announced = stored.getFirst();
        // The same chain id throughout: T14 assembles the audit chain by it, so a new id here
        // would leave the note untraceable to the recording it came from.
        assertThat(announced.chainId()).isEqualTo(chainId);
        assertThat(announced.knowledge().content()).isEqualTo(content);
        assertThat(announced.knowledge().summary()).isEqualTo(summary);

        // The criterion says "readable", so the assertion is on the file a person would open.
        var note = Files.readString(vault.resolve(announced.knowledge().page().id()));
        assertThat(note)
                .contains("- #142 — agreed done, to be closed.")
                .contains("Priya: #142 is merged and verified, so it can be closed.")
                .contains("[[Sprint Refinement]]");
    }

    @Test
    @DisplayName("The announcement comes after the write, so nothing acts on a note that is not there")
    void theAnnouncementComesAfterTheWrite() {
        // KnowledgeStage publishes as soon as store() returns and the Action stage acts on that,
        // so the note has to exist by the time the event is seen — not eventually.
        // Counted, because an assertion that only runs inside a subscriber proves nothing if the
        // subscriber is never called: the test would pass for a stage that published nothing.
        var delivered = new ArrayList<KnowledgeStored>();
        bus.subscribe(KnowledgeStored.class, event -> {
            assertThat(vault.resolve(event.knowledge().page().id()))
                    .as("the note, at the moment the event is delivered")
                    .isRegularFile();
            delivered.add(event);
        });
        new KnowledgeStage(new MarkdownVaultKnowledgeProvider(vault.toString()), bus).start();

        bus.publish(ContentSummarised.builder()
                .chainId(UUID.randomUUID())
                .content(CapturedContent.builder()
                        .sourceUri("https://example.test/notes")
                        .title("Stand-up")
                        .contentType("General")
                        .transcript("Whatever was said.")
                        .build())
                .summary(Summary.builder()
                        .text("A summary.")
                        .producedBy(Provenance.builder()
                                .provider("anthropic")
                                .model("claude-sonnet-5")
                                .build())
                        .promptVersion(1)
                        .build())
                .build());

        assertThat(delivered).hasSize(1);
    }
}
