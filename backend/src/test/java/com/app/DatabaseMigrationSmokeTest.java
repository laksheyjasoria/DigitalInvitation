package com.app;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Starts the real application against PostgreSQL so Flyway migrations and
 * Spring bean wiring are exercised in CI, not merely Java compilation.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE, properties = {
        "app.security.jwt.secret=ci-only-secret-that-is-long-enough-for-hmac-sha256",
        "app.public-base-url=http://localhost:8080",
        "app.mail.enabled=false",
        "app.payment.razorpay.enabled=false",
        "app.storage.fileserver.enabled=false"
})
class DatabaseMigrationSmokeTest {
    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void flywayCreatesCorePlatformTables() {
        Integer count = jdbc.queryForObject(
                "SELECT count(*) FROM information_schema.tables " +
                "WHERE table_schema='public' AND table_name IN " +
                "('app_user','system_setting','invitation','invitation_publication'," +
                "'customer_order','payment_transaction','invitation_rsvp','invitation_extension')",
                Integer.class);
        assertTrue(count != null && count == 8,
                "All core platform tables should exist after Flyway migration");
    }
}
