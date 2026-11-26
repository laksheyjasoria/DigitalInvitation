package com.app.auth;

import com.app.audit.AuditLogService;
import com.app.common.api.ApiResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")
public class AdminInviteAcceptanceController {
    private final JdbcTemplate jdbc;
    private final PasswordEncoder encoder;
    private final AuditLogService audit;

    public AdminInviteAcceptanceController(JdbcTemplate jdbc, PasswordEncoder encoder, AuditLogService audit) {
        this.jdbc = jdbc;
        this.encoder = encoder;
        this.audit = audit;
    }

    public record AcceptRequest(
            @NotBlank String token,
            @NotBlank @Size(min=8,max=72)
            @Pattern(regexp="^(?=.*[A-Za-z])(?=.*\\d)(?=.*[^A-Za-z0-9\\s]).{8,72}$",
                    message="Password must be at least 8 characters and include a letter, number and symbol") String password,
            @NotBlank @Size(max=120) String displayName) { }

    @PostMapping("/accept-admin-invite")
    @Transactional
    public ApiResponse<?> accept(@Valid @RequestBody AcceptRequest request) {
        if (request.token().length() > 100) throw new IllegalArgumentException("Invitation token is invalid");
        String tokenHash = hash(request.token());
        var rows = jdbc.query(
                "SELECT id,email,role,expires_at,consumed_at FROM admin_invite WHERE token_hash=? FOR UPDATE",
                (rs,n) -> Map.of("id",rs.getObject("id",UUID.class),"email",rs.getString("email"),
                        "role",rs.getString("role"),"expiresAt",rs.getTimestamp("expires_at").toInstant(),
                        "consumed",rs.getTimestamp("consumed_at") != null), tokenHash);
        if (rows.isEmpty()) throw new IllegalArgumentException("Administrator invitation is invalid or expired");
        var invite = rows.get(0);
        if (Boolean.TRUE.equals(invite.get("consumed")) || !((Instant)invite.get("expiresAt")).isAfter(Instant.now()))
            throw new IllegalArgumentException("Administrator invitation is invalid or expired");
        String email = (String) invite.get("email");
        Integer existing = jdbc.queryForObject("SELECT count(*) FROM app_user WHERE lower(email)=?",Integer.class,email);
        if (existing != null && existing > 0) throw new IllegalArgumentException("An account already exists for this invitation email");
        UUID userId = UUID.randomUUID();
        jdbc.update("INSERT INTO app_user(id,email,password_hash,display_name,role,status,email_verified_at) VALUES (?,?,?,?,?,'ACTIVE',now())",
                userId,email,encoder.encode(request.password()),request.displayName().trim(),invite.get("role"));
        int consumed = jdbc.update("UPDATE admin_invite SET consumed_at=now() WHERE id=? AND consumed_at IS NULL AND expires_at>now()",invite.get("id"));
        if (consumed != 1) throw new IllegalArgumentException("Administrator invitation was already used");
        audit.record(userId,"ADMIN_INVITE_ACCEPTED","ADMIN_INVITE",invite.get("id").toString(),"SUCCESS",
                Map.of("email",email,"role",invite.get("role")));
        return ApiResponse.success(Map.of("userId",userId,"email",email,"role",invite.get("role"),"status","ACTIVE"));
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
