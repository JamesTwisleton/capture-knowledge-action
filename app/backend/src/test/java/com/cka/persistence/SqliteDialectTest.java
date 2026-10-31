package com.cka.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.relational.core.dialect.LimitClause;
import org.springframework.data.relational.core.dialect.LockClause;
import org.springframework.data.relational.core.sql.LockOptions;

/**
 * The dialect's clauses, run against a real SQLite database rather than compared to expected
 * strings.
 *
 * <p>That distinction is the whole point of this class. The risk in a hand-written dialect is not
 * that it renders a string somebody disagrees with — it is that SQLite rejects the string, and
 * asserting on the text would be checking the claim against itself. Nothing in the application
 * paginates yet, so a wrong clause here would otherwise lie dormant until T14 or T16 paged the
 * activity view, and surface a long way from this file.
 */
@DisplayName("The SQLite dialect renders clauses SQLite actually accepts")
class SqliteDialectTest {

    /** In memory, so each test gets a database of its own and nothing is left on disk. */
    private Connection connection;

    @BeforeEach
    void fiveRows() throws Exception {
        connection = DriverManager.getConnection("jdbc:sqlite::memory:");
        try (var statement = connection.createStatement()) {
            statement.execute("CREATE TABLE numbers (n INTEGER)");
            statement.execute("INSERT INTO numbers (n) VALUES (1), (2), (3), (4), (5)");
        }
    }

    @AfterEach
    void closeDatabase() throws Exception {
        connection.close();
    }

    @Test
    @DisplayName("A limit takes the first rows")
    void aLimitTakesTheFirstRows() throws Exception {
        var clause = SqliteDialect.INSTANCE.limit().getLimit(2);

        assertThat(clause).isEqualTo("LIMIT 2");
        assertThat(select(clause)).containsExactly(1, 2);
    }

    @Test
    @DisplayName("An offset skips rows, carrying the LIMIT -1 that SQLite requires")
    void anOffsetSkipsRows() throws Exception {
        // The quirk: SQLite's grammar has no bare OFFSET, so "skip 3, take the rest" has to be
        // spelled with a limit of -1. Plain "OFFSET 3" is a syntax error, which is what the
        // execution below would catch.
        var clause = SqliteDialect.INSTANCE.limit().getOffset(3);

        assertThat(clause).isEqualTo("LIMIT -1 OFFSET 3");
        assertThat(select(clause)).containsExactly(4, 5);
    }

    @Test
    @DisplayName("A page puts LIMIT before OFFSET, the order SQLite fixes")
    void aPagePutsLimitBeforeOffset() throws Exception {
        // HsqlDb renders this pair the other way round. SQLite rejects that order, which is why
        // this dialect does not simply extend HsqlDbDialect — and this is the test that says so.
        var clause = SqliteDialect.INSTANCE.limit().getLimitOffset(2, 1);

        assertThat(clause).isEqualTo("LIMIT 2 OFFSET 1");
        assertThat(select(clause)).containsExactly(2, 3);
    }

    @Test
    @DisplayName("The reversed order HsqlDb uses is rejected, so the choice above is load-bearing")
    void theReversedOrderIsRejected() {
        // Proving the negative: without this, "LIMIT before OFFSET" is an assertion about SQLite
        // that nothing checks, and a future tidy-up could reorder it believing either works.
        assertThatThrownBy(() -> select("OFFSET 1 LIMIT 2"))
                .as("SQLite should reject HsqlDb's OFFSET-then-LIMIT ordering")
                .isInstanceOf(SQLException.class);
    }

    @Test
    @DisplayName("The lock clause renders nothing, and a statement carrying it still runs")
    void theLockClauseRendersNothing() throws Exception {
        // SQLite has no FOR UPDATE and would reject one; it locks the whole file per writer, so
        // there is no row-level lock to express. Empty is the correct rendering, not a stub.
        // Mocked, because the implementation ignores its argument entirely — building a real
        // LockOptions needs a From clause that only Spring Data's select builder produces.
        var clause = SqliteDialect.INSTANCE.lock().getLock(mock(LockOptions.class));

        assertThat(clause).isEmpty();
        assertThat(select(clause)).containsExactly(1, 2, 3, 4, 5);
    }

    @Test
    @DisplayName("Both clauses are appended after ORDER BY")
    void bothClausesAreAppendedAfterOrderBy() {
        // Two separate enums that share a constant name: LimitClause.Position and
        // LockClause.Position are unrelated types, and assertThat(Object) compares across them
        // happily — so getting this wrong fails at runtime rather than at compile time.
        assertThat(SqliteDialect.INSTANCE.limit().getClausePosition()).isEqualTo(LimitClause.Position.AFTER_ORDER_BY);
        assertThat(SqliteDialect.INSTANCE.lock().getClausePosition()).isEqualTo(LockClause.Position.AFTER_ORDER_BY);
    }

    /** Runs the clause where Spring Data would put it, and returns what came back. */
    private List<Integer> select(String clause) throws Exception {
        var sql = "SELECT n FROM numbers ORDER BY n " + clause;
        try (var statement = connection.createStatement();
                var rows = statement.executeQuery(sql)) {
            var found = new ArrayList<Integer>();
            while (rows.next()) {
                found.add(rows.getInt("n"));
            }
            return found;
        }
    }
}
