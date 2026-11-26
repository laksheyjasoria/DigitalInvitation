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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
    void refreshTokensRotateAndReplayRevokesTheSessionFamily() throws Exception {
        java.util.UUID userId = java.util.UUID.randomUUID();
        String email = "refresh-" + userId + "@example.test";
        jdbc.update("INSERT INTO app_user(id,email,password_hash,display_name,role,status) VALUES (?,?,?,'Refresh CI','CUSTOMER','ACTIVE')",
                userId, email, "not-used");
        String rawRefresh = "ci-refresh-" + java.util.UUID.randomUUID();
        String tokenHash = java.util.HexFormat.of().formatHex(
                java.security.MessageDigest.getInstance("SHA-256")
                        .digest(rawRefresh.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        jdbc.update("INSERT INTO auth_refresh_session(user_id,token_hash,expires_at) VALUES (?,?,now()+interval '30 days')",
                userId, tokenHash);

        var rotated = mockMvc.perform(post("/api/v1/auth/refresh")
                        .cookie(new jakarta.servlet.http.Cookie("di_refresh", rawRefresh)))
                .andExpect(status().isOk())
                .andReturn();
        String setCookie = rotated.getResponse().getHeader("Set-Cookie");
        assertTrue(setCookie != null && setCookie.contains("di_refresh=") && setCookie.contains("HttpOnly"),
                "Refresh response must rotate an HttpOnly cookie");
        Long activeAfterRotation = jdbc.queryForObject(
                "SELECT count(*) FROM auth_refresh_session WHERE user_id=? AND revoked_at IS NULL",
                Long.class, userId);
        assertTrue(activeAfterRotation != null && activeAfterRotation == 1,
                "Exactly one active refresh session should remain after rotation");

        mockMvc.perform(post("/api/v1/auth/refresh")
                        .cookie(new jakarta.servlet.http.Cookie("di_refresh", rawRefresh)))
                .andExpect(status().isUnauthorized());
        Long activeAfterReplay = jdbc.queryForObject(
                "SELECT count(*) FROM auth_refresh_session WHERE user_id=? AND revoked_at IS NULL",
                Long.class, userId);
        assertTrue(activeAfterReplay != null && activeAfterReplay == 0,
                "Replaying a rotated token must revoke the active session family");
    }

    @Test
    void invitationDetailsAreOwnerScopedAndPublicationLockQueryIsValid() throws Exception {
        java.util.UUID ownerId = java.util.UUID.randomUUID();
        java.util.UUID otherId = java.util.UUID.randomUUID();
        String ownerEmail = "owner-" + ownerId + "@example.test";
        String otherEmail = "other-" + otherId + "@example.test";
        jdbc.update("INSERT INTO app_user(id,email,password_hash,display_name,role,status) VALUES (?,?,?,'Owner','CUSTOMER','ACTIVE')",
                ownerId, ownerEmail, "not-used");
        jdbc.update("INSERT INTO app_user(id,email,password_hash,display_name,role,status) VALUES (?,?,?,'Other','CUSTOMER','ACTIVE')",
                otherId, otherEmail, "not-used");
        java.util.UUID templateVersionId = jdbc.queryForObject(
                "SELECT id FROM invitation_template_version ORDER BY created_at LIMIT 1", java.util.UUID.class);
        java.util.UUID invitationId = java.util.UUID.randomUUID();
        jdbc.update("INSERT INTO invitation(id,owner_user_id,template_version_id,title,content,timezone) VALUES (?,?,?,'Private CI invitation','{}'::jsonb,'Asia/Kolkata')",
                invitationId, ownerId, templateVersionId);

        String otherToken = jwtService.issue(otherId.toString(), otherEmail, "CUSTOMER", 0);
        mockMvc.perform(get("/api/v1/invitations/" + invitationId)
                        .header("Authorization", "Bearer " + otherToken))
                .andExpect(status().isNotFound());

        String ownerToken = jwtService.issue(ownerId.toString(), ownerEmail, "CUSTOMER", 0);
        mockMvc.perform(get("/api/v1/invitations/" + invitationId)
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk());

        var lockedRows = jdbc.queryForList(
                "SELECT i.id,i.title,i.status,o.status order_status FROM invitation i LEFT JOIN LATERAL " +
                "(SELECT id,status FROM customer_order WHERE invitation_id=i.id ORDER BY created_at DESC LIMIT 1) o ON true " +
                "WHERE i.id=? AND i.owner_user_id=? FOR UPDATE OF i",
                invitationId, ownerId);
        assertTrue(lockedRows.size() == 1,
                "The publication query must lock only the owned invitation row");
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
    @Test
    void sampleInvitationTemplatesAreSeededWithDistinctVisualDesigns() {
        Integer templateCount = jdbc.queryForObject(
                "SELECT count(*) FROM invitation_template WHERE template_key IN " +
                "('wedding-floral-garden','wedding-royal-heritage','wedding-minimal-modern'," +
                "'wedding-sunset-romance','birthday-confetti-pop','birthday-golden-hour'," +
                "'anniversary-ever-after','anniversary-modern-milestone','family-gathering-bloom'," +
                "'milestone-achievement','other-festival-lights','other-clean-canvas') " +
                "AND status='PUBLISHED'",
                Integer.class);
        assertTrue(templateCount != null && templateCount == 12,
                "All twelve sample invitation templates should be published by Flyway");

        Integer styledCount = jdbc.queryForObject(
                "SELECT count(*) FROM invitation_template t " +
                "JOIN invitation_template_version v ON v.template_id=t.id AND v.version_number=t.current_version " +
                "WHERE t.template_key LIKE 'wedding-%' " +
                "AND jsonb_typeof(v.definition)='object' " +
                "AND jsonb_typeof(v.definition->'design')='object' " +
                "AND jsonb_typeof(v.definition->'design'->'palette')='object' " +
                "AND v.definition->'design'->'palette'->>'accent' IS NOT NULL",
                Integer.class);
        assertTrue(styledCount != null && styledCount == 4,
                "Wedding sample templates should contain a design palette for the public renderer");
    }

}
