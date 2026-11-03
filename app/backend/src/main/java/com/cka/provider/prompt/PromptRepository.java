package com.cka.provider.prompt;

import java.util.List;
import org.springframework.data.repository.CrudRepository;

/** The {@code prompt} table, as Spring Data JDBC sees it. */
interface PromptRepository extends CrudRepository<PromptRow, Long> {

    /**
     * One derived query serves all three operations: the history as it stands, the current
     * version at its head, and the number the next version takes. A content type's history is a
     * handful of rows, so a second query to fetch only the newest would be a second thing to
     * keep correct for no measurable gain.
     *
     * <p>Ordered by {@code version}, not by the surrogate key — the version is what the audit
     * chain records and what the UI shows, so it is the column that must be right.
     */
    List<PromptRow> findByContentTypeOrderByVersionDesc(String contentType);
}
