package com.cka.persistence;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.flyway.autoconfigure.FlywayConfigurationCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.data.jdbc.core.dialect.JdbcDialect;

/**
 * The two things SQLite needs that a server database does not (T10).
 *
 * <p>Both are here rather than in {@code com.cka.provider.audit} because neither is about
 * auditing: prompt storage (T11) and the widened audit chain (T14) sit on the same foundation.
 */
@Slf4j
@Configuration
class SqlitePersistenceConfiguration {

    private static final String SQLITE_URL_PREFIX = "jdbc:sqlite:";

    /**
     * Spring Data JDBC ships no SQLite dialect, and has no fallback — {@code DialectResolver}
     * throws {@code NoDialectException} ("Please provide a Dialect") rather than guessing at
     * ANSI. So one is provided, and {@link SqliteDialect} explains what it answers and why.
     *
     * <p>A plain bean is enough: Spring Boot's own dialect bean is conditional on one being
     * missing, so declaring this backs it off by type. The bean's name plays no part — the
     * {@code spring.factories} provider SPI that Spring Data also offers would work, and is how
     * Spring Data registers its own, but it is two files and a reflective lookup to achieve
     * exactly what one method does here.
     */
    @Bean
    JdbcDialect sqliteJdbcDialect() {
        return SqliteDialect.INSTANCE;
    }

    /**
     * Creates the directory holding the database file, which the driver will not.
     *
     * <p>A SQLite URL names a file; the driver creates the file but not its parent, so a missing
     * directory surfaces as {@code unable to open database file} — a message that says nothing
     * about directories. Under Compose the path is a mounted volume and always exists, so this
     * only ever matters to someone running the backend straight from a checkout, which is
     * exactly when that message costs the most time.
     *
     * <p>Hung off Flyway's customiser because it runs while the Flyway bean is configured and
     * therefore before the first connection is opened — the migration is what opens it. A
     * {@code @PostConstruct} elsewhere would be racing that ordering rather than guaranteeing it.
     */
    @Bean
    FlywayConfigurationCustomizer sqliteDatabaseDirectory(Environment environment) {
        return configuration -> createDatabaseDirectory(environment.getProperty("spring.datasource.url", ""));
    }

    /** Does nothing for any other database: a server URL names a host, not a path to create. */
    private static void createDatabaseDirectory(String url) {
        if (!url.startsWith(SQLITE_URL_PREFIX)) {
            return;
        }
        Path parent = Path.of(url.substring(SQLITE_URL_PREFIX.length()))
                .toAbsolutePath()
                .getParent();
        if (parent == null || Files.isDirectory(parent)) {
            return;
        }
        try {
            Files.createDirectories(parent);
            log.info("Created {} to hold the SQLite database", parent);
        } catch (IOException e) {
            // Thrown, not logged: there is no database without it, so carrying on would only
            // trade this message for the driver's far less helpful one a moment later.
            throw new UncheckedIOException("Could not create the directory for the SQLite database: " + parent, e);
        }
    }
}
