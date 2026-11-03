package com.cka.provider.prompt;

import com.cka.core.Prompt;
import com.cka.persistence.DatabaseHealth;
import com.cka.provider.ProviderHealth;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * The prompt store on SQL (T11), on the same layer as the audit store: SQLite by default, any
 * SQL database by configuration.
 *
 * <p>Append-only. Nothing here updates or deletes a row, which is what makes a prompt version
 * recorded against a summary still readable a hundred edits later.
 */
@Component
@ConditionalOnProperty(prefix = "cka.provider", name = "prompt", havingValue = "sql")
@RequiredArgsConstructor
public class SqlPromptStore implements PromptStore {

    private final PromptRepository prompts;
    private final DatabaseHealth database;

    @Override
    public Prompt current(String contentType) {
        return newest(contentType)
                .or(() -> newest(Prompt.FALLBACK_CONTENT_TYPE))
                .orElseThrow(() -> new IllegalStateException("No prompt for content type \"" + contentType
                        + "\", and no \"" + Prompt.FALLBACK_CONTENT_TYPE
                        + "\" prompt to fall back on — both ship seeded, so one has been deleted."));
    }

    /**
     * The next version is read and then written, which on another database would be a race. Here
     * it cannot silently be one: {@code UNIQUE (content_type, version)} turns a collision into a
     * failed write rather than a lost edit, and SQLite takes a single write lock on the whole
     * file, so the second writer queues behind the first instead of racing it.
     */
    @Override
    public Prompt save(String contentType, String instructions) {
        var next = newest(contentType).map(current -> current.version() + 1).orElse(1);
        var written = Prompt.builder()
                .contentType(contentType)
                .version(next)
                .instructions(instructions)
                .build();
        prompts.save(PromptRow.of(written));
        return written;
    }

    @Override
    public List<Prompt> versions(String contentType) {
        return prompts.findByContentTypeOrderByVersionDesc(contentType).stream()
                .map(PromptRow::toPrompt)
                .toList();
    }

    @Override
    public ProviderHealth checkHealth() {
        return database.check();
    }

    private Optional<Prompt> newest(String contentType) {
        return versions(contentType).stream().findFirst();
    }
}
