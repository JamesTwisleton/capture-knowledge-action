package com.cka.provider.audit;

import com.cka.core.ActionType;
import com.cka.core.AuditEntry;
import com.cka.core.AuditStep;
import com.cka.core.Decider;
import com.cka.core.Provenance;
import java.time.Instant;
import java.util.UUID;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

/**
 * One row of {@code audit_entry}, and the only place that knows the database's shape.
 *
 * <p>Separate from {@link AuditEntry} on purpose. The core record is the pipeline's vocabulary
 * and must stay free of persistence concerns — it carries no {@code @Id}, no table name and no
 * surrogate key, exactly as {@code com.cka.core.event} carries no Jackson annotations.
 *
 * <p>Two core records are flattened here rather than mapped as embedded values: a {@link
 * Provenance} to {@code provider}/{@code model} and a {@link Decider} to {@code
 * decider_kind}/{@code decider_name}. Four plain columns are what makes "everything this model
 * decided" or "everything James rejected" an ordinary query, and the conversion is a line each.
 *
 * @param id          the ordering column, assigned by the database; {@code null} before insert
 * @param chainId     the chain's UUID as text, because SQLite has no UUID type and the driver
 *                    cannot bind one — converting at this boundary keeps that fact out of the core
 * @param step        mapped to its name by Spring Data's default enum handling
 * @param recordedAt  epoch milliseconds; see the comment in {@code V2…sql} for why not text
 * @param detail      what happened, in words a person can read
 * @param actionType  the action the step concerns, or {@code null} before one exists
 * @param provider    the first half of {@link AuditEntry#producedBy()}, {@code null} with it
 * @param model       the second half; the two are null or set together
 * @param confidence  {@code null} where nothing scored the step, rather than a zero that would
 *                    read as a real score
 * @param deciderKind SYSTEM or HUMAN, never null — every step is decided by one or the other
 * @param deciderName the human's name, {@code null} for the system
 * @param promptVersion the prompt version behind the chain, set on the summarised step only
 */
@Table("audit_entry")
record AuditEntryRow(
        @Id Long id,
        String chainId,
        AuditStep step,
        long recordedAt,
        String detail,
        ActionType actionType,
        String provider,
        String model,
        Double confidence,
        Decider.Kind deciderKind,
        String deciderName,
        Integer promptVersion) {

    /** A row ready to insert: no id yet, since the database assigns it. */
    static AuditEntryRow of(AuditEntry entry) {
        return new AuditEntryRow(
                null,
                entry.chainId().toString(),
                entry.step(),
                entry.recordedAt().toEpochMilli(),
                entry.detail(),
                entry.actionType(),
                entry.producedBy() == null ? null : entry.producedBy().provider(),
                entry.producedBy() == null ? null : entry.producedBy().model(),
                entry.confidence(),
                entry.decider().kind(),
                entry.decider().name(),
                entry.promptVersion());
    }

    AuditEntry toEntry() {
        return AuditEntry.builder()
                .chainId(UUID.fromString(chainId))
                .step(step)
                .recordedAt(Instant.ofEpochMilli(recordedAt))
                .detail(detail)
                .actionType(actionType)
                .producedBy(
                        provider == null
                                ? null
                                : Provenance.builder()
                                        .provider(provider)
                                        .model(model)
                                        .build())
                .confidence(confidence)
                .decider(new Decider(deciderKind, deciderName))
                .promptVersion(promptVersion)
                .build();
    }
}
