package com.app;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
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
    private com.fasterxml.jackson.databind.ObjectMapper objectMapper;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private com.app.security.JwtService jwtService;

    @Autowired
    private com.app.auth.AuthRateLimitService authRateLimitService;

    @MockBean
    private com.app.templatestudio.GitHubTemplateReleaseService githubTemplateReleaseService;

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
    void publishedInvitationRetainsItsExactTemplateVersionAndImmutableSnapshot() throws Exception {
        java.util.UUID ownerId = java.util.UUID.randomUUID();
        String email = "published-snapshot-" + ownerId + "@example.test";
        jdbc.update("INSERT INTO app_user(id,email,password_hash,display_name,role,status) VALUES (?,?,?,'Snapshot Owner','CUSTOMER','ACTIVE')",
                ownerId, email, "not-used");
        java.util.UUID templateVersionId = jdbc.queryForObject(
                "SELECT v.id FROM invitation_template t JOIN invitation_template_version v ON v.template_id=t.id AND v.version_number=t.current_version " +
                "WHERE t.template_key='wedding-floral-garden' AND t.status='PUBLISHED'",
                java.util.UUID.class);
        String definitionText = jdbc.queryForObject(
                "SELECT definition::text FROM invitation_template_version WHERE id=?", String.class, templateVersionId);
        var definition = objectMapper.readTree(definitionText);
        java.util.List<java.util.Map<String,Object>> sections = new java.util.ArrayList<>();
        for (var section : definition.path("sections")) {
            java.util.Map<String,Object> values = new java.util.LinkedHashMap<>();
            java.util.Map<String,Object> itemValues = new java.util.LinkedHashMap<>();
            for (var field : section.path("fields")) {
                String key = field.isTextual() ? field.asText() : field.path("key").asText();
                String type = field.isTextual() ? "text" : field.path("type").asText("text");
                String value = switch (type) {
                    case "date" -> "2027-12-01";
                    case "time" -> "17:00";
                    case "number" -> "1";
                    case "email" -> "ci@example.test";
                    case "url" -> "https://example.test";
                    default -> "CI " + key;
                };
                if (section.path("repeatable").asBoolean(false)) itemValues.put(key, value);
                else values.put(key, value);
            }
            java.util.Map<String,Object> saved = new java.util.LinkedHashMap<>();
            saved.put("key", section.path("key").asText());
            saved.put("label", section.path("label").asText());
            saved.put("enabled", true);
            saved.put("values", values);
            if (section.path("repeatable").asBoolean(false)) saved.put("items", java.util.List.of(itemValues));
            sections.add(saved);
        }
        java.util.Map<String,Object> content = java.util.Map.of("locale", "en", "locationUrl", "https://maps.app.goo.gl/ci-location", "sections", sections);
        String token = jwtService.issue(ownerId.toString(), email, "CUSTOMER", 0);
        var created = mockMvc.perform(post("/api/v1/invitations")
                        .header("Authorization", "Bearer " + token)
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(java.util.Map.of(
                                "templateVersionId", templateVersionId,
                                "title", "Original CI Invitation",
                                "content", content,
                                "lastFunctionDate", "2027-12-01",
                                "timezone", "Asia/Kolkata"))))
                .andExpect(status().isCreated())
                .andReturn();
        java.util.UUID invitationId = java.util.UUID.fromString(
                objectMapper.readTree(created.getResponse().getContentAsString()).path("data").path("id").asText());

        var requiredKeys = jdbc.query(
                "SELECT substring(setting_key from 18) AS consent_key FROM system_setting " +
                "WHERE setting_key LIKE 'consent.required.%' AND setting_value='true'::jsonb",
                (rs, n) -> rs.getString("consent_key"));
        java.util.Map<String,Boolean> consents = new java.util.LinkedHashMap<>();
        requiredKeys.forEach(key -> consents.put(key, true));
        mockMvc.perform(post("/api/v1/invitations/" + invitationId + "/consents")
                        .header("Authorization", "Bearer " + token)
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(consents)))
                .andExpect(status().isOk());

        var published = mockMvc.perform(post("/api/v1/invitations/" + invitationId + "/publish")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
        String slug = objectMapper.readTree(published.getResponse().getContentAsString()).path("data").path("slug").asText();
        assertTrue(!slug.isBlank());

        java.util.UUID newerVersionId = java.util.UUID.randomUUID();
        jdbc.update("INSERT INTO invitation_template_version(id,template_id,version_number,definition,created_by) " +
                        "SELECT ?,template_id,version_number+1,definition,created_by FROM invitation_template_version WHERE id=?",
                newerVersionId, templateVersionId);
        jdbc.update("UPDATE invitation_template SET current_version=(SELECT version_number FROM invitation_template_version WHERE id=?) " +
                        "WHERE id=(SELECT template_id FROM invitation_template_version WHERE id=?)",
                newerVersionId, newerVersionId);
        jdbc.update("UPDATE invitation SET title='Changed draft row',content='{\"locale\":\"changed\"}'::jsonb WHERE id=?", invitationId);

        java.util.UUID publishedVersionId = jdbc.queryForObject(
                "SELECT template_version_id FROM invitation_publication WHERE public_slug=?",
                java.util.UUID.class, slug);
        assertEquals(templateVersionId, publishedVersionId);
        assertThrows(org.springframework.dao.DataAccessException.class,
                () -> jdbc.update("UPDATE invitation_template_version SET definition='{}'::jsonb WHERE id=?", templateVersionId));
        assertThrows(org.springframework.dao.DataAccessException.class,
                () -> jdbc.update("UPDATE invitation_publication SET published_snapshot='{}'::jsonb WHERE public_slug=?", slug));

        mockMvc.perform(get("/api/v1/public/invitations/" + slug))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.title").value("Original CI Invitation"))
                .andExpect(jsonPath("$.data.content.locale").value("en"));
    }

    @Test
    @org.springframework.transaction.annotation.Transactional
    void adminPublishesValidatedPackageAndCatalogueWaitsForClasspathImage() throws Exception {
        java.util.UUID adminId = java.util.UUID.randomUUID();
        String email = "template-publisher-" + adminId + "@example.test";
        jdbc.update("INSERT INTO app_user(id,email,password_hash,display_name,role,status) VALUES (?,?,?,'Template Publisher','ADMIN','ACTIVE')",
                adminId, email, "not-used");
        String token = jwtService.issue(adminId.toString(), email, "ADMIN", 0);
        java.util.UUID categoryId = jdbc.queryForObject(
                "SELECT id FROM event_category WHERE category_key='WEDDING'", java.util.UUID.class);

        String definition = "<template id=\"TEMP9999999\" name=\"CI Package\" version=\"1.0.0\" schemaVersion=\"1\">" +
                "<sections><section key=\"hero\" label=\"Invitation\"><field key=\"headline\" label=\"Heading\" type=\"text\" required=\"true\"/></section></sections></template>";
        String preview = "<preview><hero><headline>Sample invitation</headline></hero></preview>";
        byte[] html = "<html><body><main>CI package</main></body></html>".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        byte[] css = "body{margin:0}".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        byte[] js = "window.templateReady=true;".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        MockMultipartFile[] files = {
                new MockMultipartFile("files", "index.html", "text/html", html),
                new MockMultipartFile("files", "styles.css", "text/css", css),
                new MockMultipartFile("files", "script.js", "application/javascript", js),
                new MockMultipartFile("files", "template.xml", "application/xml", definition.getBytes(java.nio.charset.StandardCharsets.UTF_8)),
                new MockMultipartFile("files", "preview.xml", "application/xml", preview.getBytes(java.nio.charset.StandardCharsets.UTF_8))
        };

        var upload = mockMvc.perform(multipart("/api/v1/admin/template-studio/packages/files")
                        .file(files[0]).file(files[1]).file(files[2]).file(files[3]).file(files[4])
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
        java.util.UUID packageId = java.util.UUID.fromString(
                objectMapper.readTree(upload.getResponse().getContentAsString()).path("data").path("id").asText());

        org.mockito.Mockito.when(githubTemplateReleaseService.commitPackage(
                        org.mockito.ArgumentMatchers.eq("TEMP9999999"),
                        org.mockito.ArgumentMatchers.eq("1.0.0"),
                        org.mockito.ArgumentMatchers.anyString(),
                        org.mockito.ArgumentMatchers.any(byte[].class)))
                .thenReturn(new com.app.templatestudio.GitHubTemplateReleaseService.ReleaseCommit(
                        "a".repeat(40), "backend/src/main/resources/templates/packages/TEMP9999999/1.0.0.zip",
                        "template-releases"));

        var published = mockMvc.perform(post("/api/v1/admin/template-studio/packages/" + packageId + "/publish")
                        .header("Authorization", "Bearer " + token)
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(java.util.Map.of("categoryId", categoryId))))
                .andExpect(status().isOk())
                .andReturn();
        var publishedData = objectMapper.readTree(published.getResponse().getContentAsString()).path("data");
        assertEquals("PUBLISHED", publishedData.path("status").asText());
        assertEquals("a".repeat(40), publishedData.path("commitSha").asText());

        java.util.UUID templateVersionId = java.util.UUID.fromString(publishedData.path("templateVersionId").asText());
        assertEquals("RELEASED", jdbc.queryForObject(
                "SELECT status FROM template_package_stage WHERE id=?", String.class, packageId));
        assertEquals("backend/src/main/resources/templates/packages/TEMP9999999/1.0.0.zip",
                jdbc.queryForObject("SELECT package_resource_path FROM invitation_template_version WHERE id=?",
                        String.class, templateVersionId));

        var catalogue = mockMvc.perform(get("/api/v1/catalog/categories/" + categoryId + "/templates"))
                .andExpect(status().isOk())
                .andReturn();
        var catalogueRows = objectMapper.readTree(catalogue.getResponse().getContentAsString()).path("data");
        boolean visibleBeforeImageDeployment = false;
        for (var row : catalogueRows) {
            if ("temp9999999".equals(row.path("template_key").asText())) visibleBeforeImageDeployment = true;
        }
        assertTrue(!visibleBeforeImageDeployment,
                "A committed package must not be selectable until its classpath Docker image is deployed");
    }

    @Test
    void superAdminCanInviteSupportAndSupportIsReadOnly() throws Exception {
        java.util.UUID superAdminId = java.util.UUID.randomUUID();
        String superAdminEmail = "super-admin-" + superAdminId + "@example.test";
        jdbc.update("INSERT INTO app_user(id,email,password_hash,display_name,role,status) VALUES (?,?,?,'CI Super Admin','SUPER_ADMIN','ACTIVE')",
                superAdminId, superAdminEmail, "not-used");
        String superAdminToken = jwtService.issue(superAdminId.toString(), superAdminEmail, "SUPER_ADMIN", 0);
        String invitedEmail = "support-" + superAdminId + "@example.test";

        var created = mockMvc.perform(post("/api/v1/admin/invites")
                        .header("Authorization", "Bearer " + superAdminToken)
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(java.util.Map.of("email", invitedEmail, "role", "SUPPORT"))))
                .andExpect(status().isOk())
                .andReturn();
        String inviteUrl = objectMapper.readTree(created.getResponse().getContentAsString())
                .path("data").path("inviteUrl").asText();
        String rawToken = inviteUrl.substring(inviteUrl.indexOf("token=") + 6);
        String tokenHash = java.util.HexFormat.of().formatHex(
                java.security.MessageDigest.getInstance("SHA-256")
                        .digest(rawToken.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        assertEquals(tokenHash, jdbc.queryForObject(
                "SELECT token_hash FROM admin_invite WHERE lower(email)=?", String.class, invitedEmail));

        var accepted = mockMvc.perform(post("/api/v1/auth/accept-admin-invite")
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(java.util.Map.of(
                                "token", rawToken, "password", "GoodPass1!", "displayName", "CI Support"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.role").value("SUPPORT"))
                .andReturn();
        java.util.UUID supportId = java.util.UUID.fromString(
                objectMapper.readTree(accepted.getResponse().getContentAsString()).path("data").path("userId").asText());
        assertEquals("ACTIVE", jdbc.queryForObject("SELECT status FROM app_user WHERE id=?", String.class, supportId));
        assertTrue(jdbc.queryForObject("SELECT consumed_at IS NOT NULL FROM admin_invite WHERE lower(email)=?",
                Boolean.class, invitedEmail));

        String supportToken = jwtService.issue(supportId.toString(), invitedEmail, "SUPPORT", 0);
        mockMvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer " + supportToken))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/invitations")
                        .header("Authorization", "Bearer " + supportToken)
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/admin/template-studio/packages")
                        .header("Authorization", "Bearer " + supportToken))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/admin/invites")
                        .header("Authorization", "Bearer " + supportToken)
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(java.util.Map.of("email", "another@example.test", "role", "ADMIN"))))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/auth/accept-admin-invite")
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(java.util.Map.of(
                                "token", rawToken, "password", "GoodPass1!", "displayName", "Replay"))))
                .andExpect(status().isBadRequest());
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
    void launchCataloguePublishesExactlyOneWeddingTemplateAndArchivesDemoDesigns() {
        Integer templateCount = jdbc.queryForObject(
                "SELECT count(*) FROM invitation_template WHERE template_key IN " +
                "('wedding-floral-garden','wedding-royal-heritage','wedding-minimal-modern'," +
                "'wedding-sunset-romance','birthday-confetti-pop','birthday-golden-hour'," +
                "'anniversary-ever-after','anniversary-modern-milestone','family-gathering-bloom'," +
                "'milestone-achievement','other-festival-lights','other-clean-canvas')",
                Integer.class);
        assertTrue(templateCount != null && templateCount == 12,
                "All twelve original sample definitions should remain available for admin review");
        Integer publishedCount = jdbc.queryForObject(
                "SELECT count(*) FROM invitation_template WHERE status='PUBLISHED'", Integer.class);
        Integer archivedCount = jdbc.queryForObject(
                "SELECT count(*) FROM invitation_template WHERE status='ARCHIVED'", Integer.class);
        assertEquals(1, publishedCount, "Only the launch template should be customer-visible");
        assertEquals(17, archivedCount, "All old demo and legacy starter designs should be archived");

        Integer styledCount = jdbc.queryForObject(
                "SELECT count(*) FROM invitation_template t " +
                "JOIN invitation_template_version v ON v.template_id=t.id AND v.version_number=t.current_version " +
                "WHERE t.template_key LIKE 'wedding-%' AND t.status='PUBLISHED' " +
                "AND jsonb_typeof(v.definition)='object' " +
                "AND jsonb_typeof(v.definition->'design')='object' " +
                "AND jsonb_typeof(v.definition->'design'->'palette')='object' " +
                "AND v.definition->'design'->'palette'->>'accent' IS NOT NULL",
                Integer.class);
        String sampleDefinition = jdbc.queryForObject(
                "SELECT v.definition::text FROM invitation_template t " +
                "JOIN invitation_template_version v ON v.template_id=t.id " +
                "WHERE t.template_key='wedding-floral-garden' ORDER BY v.version_number LIMIT 1",
                String.class);
        assertTrue(styledCount != null && styledCount == 1,
                "The sole launch template should contain a design palette for the public renderer. " +
                "Matching rows=" + styledCount + "; sample definition=" + sampleDefinition);
    }

}
