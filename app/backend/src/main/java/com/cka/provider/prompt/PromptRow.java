package com.cka.provider.prompt;

import com.cka.core.Prompt;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

/**
 * One row of {@code prompt}, and the only place that knows the database's shape. Separate from
 * {@link Prompt} for the reason given on {@code AuditEntryRow}: the core record is the
 * pipeline's vocabulary and carries no {@code @Id} and no table name.
 *
 * @param id the surrogate key Spring Data needs, assigned by the database; {@code null} before
 *           insert. Nothing reads it — a prompt is identified by its content type and version.
 */
@Table("prompt")
record PromptRow(@Id Long id, String contentType, int version, String instructions) {

    static PromptRow of(Prompt prompt) {
        return new PromptRow(null, prompt.contentType(), prompt.version(), prompt.instructions());
    }

    Prompt toPrompt() {
        return Prompt.builder()
                .contentType(contentType)
                .version(version)
                .instructions(instructions)
                .build();
    }
}
