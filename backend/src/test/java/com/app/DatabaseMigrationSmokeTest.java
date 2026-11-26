package com.app;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Starts the real application against PostgreSQL so Flyway migrations and
 * Spring bean wiring are exercised in CI, not merely Java compilation.
 */
@EnabledIfEnvironmentVariable(named = "CI", matches = "true")
@AutoConfigureMockMvc
@SpringBootTest(properties = {
        "app.security.jwt.secret=ci-only-secret-that-is-long-enough-for-hmac-sha256",
        "app.public-base-url=http://localhost:8080",
        "app.mail.enabled=false",
        "app.payment.razorpay.enabled=false",
        "app.storage.fileserver.enabled=false",
        "spring.datasource.url=jdbc:postgresql://localhost:5432/digital_invitation_ci",
        "spring.datasource.username=di_ci",
        "spring.datasource.password=di_ci_password",
        "spring.datasource.driver-class-name=org.postgresql.Driver",
        "spring.flyway.enabled=true",
        "spring.jpa.hibernate.ddl-auto=none"
})
class DatabaseMigrationSmokeTest {
    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private com.app.security.JwtService jwtService;

    @Autowired
    private com.app.auth.AuthRateLimitService authRateLimitService;

    @Autowired
    private com.app.invitation.PublicSubmissionRateLimitService publicSubmissionRateLimitService;

    @Test
    void configurableTimezoneOptionsAreSeeded() {
        String configured = jdbc.queryForObject(
                "SELECT setting_value::text FROM system_setting WHERE setting_key='invitation.supported-timezones'",
                String.class);
        assertTrue(configured != null && configured.contains("Asia/Kolkata"),
                "The configurable timezone list should be seeded by Flyway");
    }

    @Test
    void loginAttemptsAreThrottledAndCanBeCleared() {
        String email = "ci-" + java.util.UUID.randomUUID() + "@example.test";
        String ip = "192.0.2.10";
        for (int attempt = 1; attempt < 8; attempt++) {
            authRateLimitService.recordFailure(email, ip);
        }
        assertThrows(org.springframework.web.server.ResponseStatusException.class,
                () -> authRateLimitService.recordFailure(email, ip));
        assertThrows(org.springframework.web.server.ResponseStatusException.class,
                () -> authRateLimitService.assertAllowed(email, ip));
        authRateLimitService.clear(email, ip);
    }

    @Test
    void publicRsvpSubmissionsAreThrottledPerPublicationAndClient() {
        java.util.UUID publicationId = java.util.UUID.randomUUID();
        for (int attempt = 0; attempt < 5; attempt++) {
            publicSubmissionRateLimitService.assertAllowed(publicationId, "192.0.2.20");
        }
        assertThrows(org.springframework.web.server.ResponseStatusException.class,
                () -> publicSubmissionRateLimitService.assertAllowed(publicationId, "192.0.2.20"));
    }

    @Test
    void userAuthenticationVersionColumnExists() {
        Integer count = jdbc.queryForObject(
                "SELECT count(*) FROM information_schema.columns " +
                "WHERE table_schema='public' AND table_name='app_user' AND column_name='auth_version'",
                Integer.class);
        assertTrue(count != null && count == 1,
                "User authentication version must exist for immediate token revocation");
    }

    @Test
    void accessTokenIsRejectedImmediatelyAfterAuthVersionChanges() throws Exception {
        java.util.UUID userId = java.util.UUID.randomUUID();
        String email = "auth-version-" + userId + "@example.test";
        jdbc.update("INSERT INTO app_user(id,email,password_hash,display_name,role,status,auth_version) " +
                "VALUES (?,?,?,'CI user','CUSTOMER','ACTIVE',3)", userId, email, "not-used");
        String token = jwtService.issue(userId.toString(), email, "CUSTOMER", 3);
        mockMvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
        jdbc.update("UPDATE app_user SET auth_version=4 WHERE id=?", userId);
        mockMvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void flywayCreatesCorePlatformTables() {
        Integer count = jdbc.queryForObject(
                "SELECT count(*) FROM information_schema.tables " +
                "WHERE table_schema='public' AND table_name IN " +
                "('app_user','system_setting','invitation','invitation_publication'," +
                "'customer_order','payment_transaction','invitation_rsvp','invitation_extension','auth_login_attempt_bucket','auth_refresh_session','public_submission_bucket')",
                Integer.class);
        assertTrue(count != null && count == 11,
                "All core platform tables should exist after Flyway migration");
    }
}
