package com.cka.config;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * The clock the application reads time from.
 *
 * <p>A bean rather than {@code Instant.now()} at each call site, because several things that
 * record time have to be pinnable in a test: {@code AuditTrail} stamps every step of the chain,
 * and the Markdown vault names a note after the moment it was stored. A test that cannot fix
 * "now" can only assert the shape of what it produced, never the value.
 *
 * <p>UTC, not the system zone. The audit chain is compared and ordered across entries, and a
 * backend whose timestamps shift when the host's zone changes — or when it is moved between
 * machines — is one whose ordering stops meaning anything. Where a time is shown to a person,
 * the front end converts it; where one is stored or used in a filename, it is UTC.
 */
@Configuration
class ClockConfiguration {

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }
}
