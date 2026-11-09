package com.cka.provider.capture;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcOperations;
import org.springframework.stereotype.Component;

/**
 * What has been captured, on the shared SQL layer from T10 — so it survives the restart that
 * Spring DevTools performs on every class change, which is what made an in-memory set useless.
 *
 * <p>Plain SQL rather than Spring Data JDBC, unlike the audit and prompt stores. Those map rows
 * to records a consumer reads back; this one answers one question and writes one row, and a
 * repository, an entity and an {@code @Id} to express that would be three files saying what two
 * statements say.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SqlCapturedItems implements CapturedItems {

    private final JdbcOperations jdbc;
    private final Clock clock;

    @Override
    public Set<String> allTitles() {
        return Set.copyOf(jdbc.queryForList("SELECT title FROM captured_item", String.class));
    }

    /** Only the first sighting is kept, so {@code stored_at} says when the note appeared. */
    @Override
    public void confirmStored(String title) {
        jdbc.update(
                "UPDATE captured_item SET stored_at = ? WHERE title = ? AND stored_at IS NULL",
                Instant.now(clock).toEpochMilli(),
                title);
    }

    @Override
    public Set<String> confirmedStored() {
        return Set.copyOf(
                jdbc.queryForList("SELECT title FROM captured_item WHERE stored_at IS NOT NULL", String.class));
    }

    @Override
    public List<UnstoredCapture> unstored() {
        return jdbc.query(
                "SELECT title, captured_at FROM captured_item WHERE stored_at IS NULL",
                (row, n) ->
                        new UnstoredCapture(row.getString("title"), Instant.ofEpochMilli(row.getLong("captured_at"))));
    }

    @Override
    public boolean retry(String title) {
        return jdbc.update("DELETE FROM captured_item WHERE title = ? AND stored_at IS NULL", title) > 0;
    }

    @Override
    public void forget(String title) {
        jdbc.update("DELETE FROM captured_item WHERE title = ?", title);
    }

    @Override
    public boolean alreadyCaptured(String title) {
        var count = jdbc.queryForObject("SELECT COUNT(*) FROM captured_item WHERE title = ?", Integer.class, title);
        return count != null && count > 0;
    }

    /**
     * Writing the same title twice is not an error worth stopping a poll for. Across a DevTools
     * restart two polls can both read a title as new before either writes it, and the primary
     * key turns the second write into a failure rather than a duplicate — so that failure is the
     * constraint doing its job, and the meeting has still been captured exactly once.
     *
     * <p>Whether it <em>was</em> that failure is answered by asking the database, not by reading
     * an error code. {@code DuplicateKeyException} never arrives here: the SQLite driver reports
     * a constraint violation with a null SQL state, so Spring cannot classify it and raises an
     * {@code UncategorizedSQLException} instead. Catching that by name would be a guess about
     * one driver; catching any access failure and then checking whether the row is present says
     * the same thing in terms every database agrees on, and still rethrows a write that failed
     * for some other reason.
     */
    @Override
    public void remember(String title) {
        try {
            jdbc.update(
                    "INSERT INTO captured_item (title, captured_at) VALUES (?, ?)",
                    title,
                    Instant.now(clock).toEpochMilli());
        } catch (DataAccessException failed) {
            if (!alreadyCaptured(title)) {
                throw failed;
            }
            log.debug("\"{}\" was already recorded as captured", title);
        }
    }
}
