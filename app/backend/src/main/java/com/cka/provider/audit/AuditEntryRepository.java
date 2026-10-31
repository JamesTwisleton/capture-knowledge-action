package com.cka.provider.audit;

import java.util.List;
import org.springframework.data.repository.CrudRepository;

/**
 * The {@code audit_entry} table, as Spring Data JDBC sees it. No implementation is written:
 * the one derived query below is what the interface is for.
 */
interface AuditEntryRepository extends CrudRepository<AuditEntryRow, Long> {

    /**
     * Ordered by the surrogate key, which is the recording order — see the comment on {@code id}
     * in {@code V1__audit_entry.sql} for why that is the column to sort on rather than a
     * timestamp, which two entries written in the same millisecond would tie on.
     */
    List<AuditEntryRow> findByChainIdOrderByIdAsc(String chainId);
}
