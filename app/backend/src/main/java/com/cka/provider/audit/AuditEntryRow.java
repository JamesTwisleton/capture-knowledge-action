package com.cka.provider.audit;

import com.cka.core.AuditEntry;
import com.cka.core.AuditStep;
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
 * @param id       the ordering column, assigned by the database; {@code null} before insert
 * @param chainId  the chain's UUID as text, because SQLite has no UUID type and the driver
 *                 cannot bind one — converting at this boundary keeps that fact out of the core
 * @param step     mapped to its name by Spring Data's default enum handling
 * @param detail   what happened, in words a person can read
 */
@Table("audit_entry")
record AuditEntryRow(@Id Long id, String chainId, AuditStep step, String detail) {

    /** A row ready to insert: no id yet, since the database assigns it. */
    static AuditEntryRow of(AuditEntry entry) {
        return new AuditEntryRow(null, entry.chainId().toString(), entry.step(), entry.detail());
    }

    AuditEntry toEntry() {
        return AuditEntry.builder()
                .chainId(UUID.fromString(chainId))
                .step(step)
                .detail(detail)
                .build();
    }
}
