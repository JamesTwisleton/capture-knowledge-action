package com.cka.provider.audit;

import com.cka.core.ActionType;
import com.cka.core.AuditStep;
import java.util.Collection;
import java.util.List;
import org.springframework.data.repository.CrudRepository;

/**
 * The {@code audit_entry} table, as Spring Data JDBC sees it. No implementation is written: the
 * derived queries below are what the interface is for, and being derived they are rendered
 * through the configured dialect rather than frozen as SQL strings.
 */
interface AuditEntryRepository extends CrudRepository<AuditEntryRow, Long> {

    /**
     * Ordered by the surrogate key, which is the recording order — see the comment on {@code id}
     * in {@code V1__audit_entry.sql} for why that is the column to sort on rather than a
     * timestamp, which two entries written in the same millisecond would tie on.
     */
    List<AuditEntryRow> findByChainIdOrderByIdAsc(String chainId);

    /**
     * The trust window. Long in the name and short in the SQL: an equality on the action type, a
     * lower bound on the timestamp, a set of steps, newest first.
     *
     * <p><strong>The name is the query.</strong> Spring Data parses it at startup against
     * {@link AuditEntryRow}'s property names, so it reads in pieces: {@code ActionType} with no
     * keyword after it is an equality; {@code StepIn} is an {@code IN} over the collection
     * argument; {@code RecordedAtGreaterThanEqual} is the lower bound; and
     * {@code OrderByRecordedAtDescIdDesc} is the sort. What it renders is this, and only this:
     *
     * <pre>{@code
     * WHERE action_type = ? AND step IN (?, ?) AND recorded_at >= ?
     * ORDER BY recorded_at DESC, id DESC
     * }</pre>
     *
     * <p>The arguments bind <em>by position, in the order the name mentions them</em> — the
     * parameter names below are documentation and nothing else. Their types all differ here, so
     * a mis-ordered call does not compile; two arguments of one type would compile and be
     * quietly wrong, which is worth remembering before adding a fourth clause.
     *
     * <p>Unlovely, and still the right trade. A {@code @Query} string would be frozen SQL for
     * one database, where this is rendered through whichever dialect is configured — and it is
     * checked at <em>startup</em>: name a property that does not exist and the context fails to
     * build, so every Spring context test in the suite catches the typo, rather than one
     * unlucky call catching it at runtime.
     *
     * <p>Ordered by the timestamp and not by the surrogate key, which is the opposite of the
     * query above, because this one crosses chains and the reader is asking "what happened
     * lately". It is also what lets {@code idx_audit_entry_decision} serve the sort: with a
     * range on {@code recorded_at} the index is not in {@code id} order, so {@code ORDER BY id}
     * costs a temp B-tree on every call. {@code id} remains the tiebreak, so two decisions
     * recorded in the same millisecond still come back in the order they were recorded rather
     * than in whichever order the database felt like.
     */
    List<AuditEntryRow> findByActionTypeAndStepInAndRecordedAtGreaterThanEqualOrderByRecordedAtDescIdDesc(
            ActionType actionType, Collection<AuditStep> step, long recordedAt);
}
