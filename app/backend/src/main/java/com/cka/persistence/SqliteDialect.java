package com.cka.persistence;

import org.springframework.data.jdbc.core.dialect.JdbcDialect;
import org.springframework.data.relational.core.dialect.AbstractDialect;
import org.springframework.data.relational.core.dialect.LimitClause;
import org.springframework.data.relational.core.dialect.LockClause;

/**
 * Teaches Spring Data JDBC to speak SQLite, which it does not ship a dialect for (T10).
 *
 * <p>Spring Data Relational provides nine — Ansi, Db2, H2, HsqlDb, MariaDb, MySql, Oracle,
 * Postgres and SqlServer — and {@code DialectResolver} picks between them on the JDBC driver's
 * reported product name. SQLite matches none of them, so without this the application fails at
 * startup with {@code NoDialectException} rather than anything that names the real problem.
 *
 * <p>Only two methods have to be answered: {@code Dialect} declares {@link #limit()} and
 * {@link #lock()} abstract and defaults the rest, and the defaults are right for SQLite.
 *
 * <p>It is worth being clear about what this does <em>not</em> imply. SQLite is the database
 * needing custom code here; Postgres and the others are already supported, so substituting one
 * is a URL and a migration path, not another class like this one.
 */
public final class SqliteDialect extends AbstractDialect implements JdbcDialect {

    public static final SqliteDialect INSTANCE = new SqliteDialect();

    /**
     * Close to HsqlDb's, and deliberately not reused from it, because the two differ in exactly
     * the two places SQLite is strict.
     */
    private static final LimitClause LIMIT_CLAUSE = new LimitClause() {

        @Override
        public String getLimit(long limit) {
            return "LIMIT " + limit;
        }

        /**
         * SQLite's grammar has no bare {@code OFFSET}: it is only ever a trailing part of a
         * {@code LIMIT}. {@code -1} is the documented way to say "no limit" and is what makes an
         * offset-only page legal at all.
         */
        @Override
        public String getOffset(long offset) {
            return "LIMIT -1 OFFSET " + offset;
        }

        /** {@code LIMIT} first, then {@code OFFSET} — the order SQLite fixes, where HsqlDb does not. */
        @Override
        public String getLimitOffset(long limit, long offset) {
            return "LIMIT " + limit + " OFFSET " + offset;
        }

        @Override
        public Position getClausePosition() {
            return Position.AFTER_ORDER_BY;
        }
    };

    /**
     * Renders nothing, because SQLite has no {@code FOR UPDATE} and would reject it.
     *
     * <p>That is not a gap being papered over. SQLite serialises writers at the database file,
     * so a transaction already holds the only write lock there is and row-level locking has
     * nothing to express. Returning the empty string is the honest rendering of that.
     */
    private static final LockClause LOCK_CLAUSE = new LockClause() {

        @Override
        public String getLock(org.springframework.data.relational.core.sql.LockOptions lockOptions) {
            return "";
        }

        @Override
        public Position getClausePosition() {
            return Position.AFTER_ORDER_BY;
        }
    };

    private SqliteDialect() {}

    @Override
    public LimitClause limit() {
        return LIMIT_CLAUSE;
    }

    @Override
    public LockClause lock() {
        return LOCK_CLAUSE;
    }

    /**
     * Off, because it needs the window functions and {@code FOR UPDATE} semantics the single
     * query loading optimisation is built on. Spring Data falls back to one query per aggregate,
     * which is what HsqlDb does too.
     */
    @Override
    public boolean supportsSingleQueryLoading() {
        return false;
    }
}
