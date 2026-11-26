package com.app.admin;

import com.app.audit.AuditLogService;
import com.app.common.api.ApiResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin/invites")
public class AdminInviteController {
    private final JdbcTemplate jdbc;
    private final AuditLogService audit;
    private final SecureRandom random = new SecureRandom();
    private final String publicBaseUrl;
    private final boolean mailEnabled;

    public AdminInviteController(JdbcTemplate jdbc, AuditLogService audit,
            @Value("${app.public-base-url}") String publicBaseUrl,
            @Value("${app.mail.enabled:false}") boolean mailEnabled,
            @Value("${spring.mail.host:}") String smtpHost) {
        this.jdbc = jdbc;
        this.audit = audit;
        this.publicBaseUrl = publicBaseUrl;
        this.mailEnabled = mailEnabled && smtpHost != null && !smtpHost.isBlank();
    }

    @GetMapping
    public ApiResponse<?> list(Authentication auth) {
        requireSuperAdmin(auth);
        return ApiResponse.success(jdbc.queryForList(
                "SELECT id,email,role,expires_at,consumed_at,created_at FROM admin_invite ORDER BY created_at DESC LIMIT 100"));
    }

    @PostMapping
    @Transactional
    public ApiResponse<?> create(@RequestBody Map<String, String> body, Authentication auth) {
        requireSuperAdmin(auth);
        String email = body.getOrDefault("email", "").trim().toLowerCase(Locale.ROOT);
        String role = body.getOrDefault("role", "").trim().toUpperCase(Locale.ROOT);
        if (!email.matches("^[A-Z0-9.!#$%&'*+/=?^_`{|}~-]+@[A-Z0-9](?:[A-Z0-9-]{0,61}[A-Z0-9])?(?:\\.[A-Z0-9](?:[A-Z0-9-]{0,61}[A-Z0-9])?)+$".toLowerCase(Locale.ROOT)) || email.length() > 320) {
            throw new IllegalArgumentException("Enter a valid administrator email address");
        }
        if (!java.util.Set.of("ADMIN", "SUPPORT").contains(role)) {
            throw new IllegalArgumentException("Only ADMIN or SUPPORT accounts can be invited");
        }
        Integer existing = jdbc.queryForObject("SELECT count(*) FROM app_user WHERE lower(email)=?", Integer.class, email);
        if (existing != null && existing > 0) throw new IllegalArgumentException("An account already exists for this email");
        Integer pending = jdbc.queryForObject("SELECT count(*) FROM admin_invite WHERE lower(email)=? AND consumed_at IS NULL AND expires_at>now()", Integer.class, email);
        if (pending != null && pending > 0) throw new IllegalArgumentException("A valid invitation already exists for this email");

        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        String raw = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        UUID actor = UUID.fromString(auth.getName());
        UUID inviteId = UUID.randomUUID();
        Instant expires = Instant.now().plusSeconds(24 * 60 * 60L);
        jdbc.update("INSERT INTO admin_invite(id,email,role,token_hash,invited_by,expires_at) VALUES (?,?,?,?,?,?)",
                inviteId, email, role, hash(raw), actor, java.sql.Timestamp.from(expires));
        String url = publicBaseUrl.replaceAll("/+$", "") + "/admin-invite.html?token=" + raw;
        if (mailEnabled) {
            try {
                jdbc.update("INSERT INTO email_outbox(recipient,template_key,payload) VALUES (?,'ADMIN_INVITE',?::jsonb)",
                        email, new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(
                                Map.of("token", raw, "baseUrl", publicBaseUrl, "role", role)));
            } catch (Exception ex) {
                throw new IllegalStateException("Unable to queue administrator invitation email", ex);
            }
        }
        audit.record(actor, "ADMIN_INVITE_CREATED", "ADMIN_INVITE", inviteId.toString(), "SUCCESS",
                Map.of("email", email, "role", role, "expiresAt", expires.toString()));
        return ApiResponse.success(Map.of("id", inviteId, "email", email, "role", role,
                "expiresAt", expires.toString(), "inviteUrl", url, "emailQueued", mailEnabled));
    }

    private void requireSuperAdmin(Authentication auth) {
        if (auth == null || auth.getAuthorities().stream().noneMatch(a -> a.getAuthority().equals("ROLE_SUPER_ADMIN"))) {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.FORBIDDEN, "SUPER_ADMIN role required");
        }
    }

    private String hash(String value) {
        try {
            return java.util.HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            throw new IllegalStateException("Token hashing is unavailable", ex);
        }
    }
}
