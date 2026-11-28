package com.app.config;

import java.util.Locale;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
@org.springframework.context.annotation.Profile("!test")
public class AdminBootstrap implements ApplicationRunner {

    private final JdbcTemplate jdbc;
    private final PasswordEncoder encoder;

    @Value("${app.bootstrap.admin.email:}")
    private String email;

    @Value("${app.bootstrap.admin.password:}")
    private String password;

    public AdminBootstrap(JdbcTemplate jdbc, PasswordEncoder encoder) {
        this.jdbc = jdbc;
        this.encoder = encoder;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (email == null || email.isBlank()) {
            return;
        }
        if (password == null || password.length() < 8 || password.length() > 72
                || !password.matches("^(?=.*[A-Za-z])(?=.*\\d)(?=.*[^A-Za-z0-9\\s]).{8,72}$")) {
            throw new IllegalStateException(
                    "APP_BOOTSTRAP_ADMIN_PASSWORD must be 8-72 characters and include a letter, a number, and a symbol");
        }

        String normalizedEmail = email.trim().toLowerCase(Locale.ROOT);
        String passwordHash = encoder.encode(password);
        var existing = jdbc.queryForList(
                "SELECT id FROM app_user WHERE email = ?", normalizedEmail);

        if (existing.isEmpty()) {
            jdbc.update(
                    "INSERT INTO app_user(id, email, password_hash, display_name, role, status, email_verified_at) "
                            + "VALUES (?, ?, ?, ?, 'SUPER_ADMIN', 'ACTIVE', now())",
                    UUID.randomUUID(), normalizedEmail, passwordHash, "Platform Administrator");
            return;
        }

        // The configured bootstrap account is authoritative. This also repairs an account
        // that was registered before the bootstrap environment variables were configured.
        jdbc.update(
                "UPDATE app_user SET password_hash = ?, role = 'SUPER_ADMIN', status = 'ACTIVE', "
                        + "email_verified_at = COALESCE(email_verified_at, now()), "
                        + "auth_version = auth_version + 1, updated_at = now() WHERE email = ?",
                passwordHash, normalizedEmail);
    }
}
