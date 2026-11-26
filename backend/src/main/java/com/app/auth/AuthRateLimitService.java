package com.app.auth;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class AuthRateLimitService {
    private static final int MAX_ATTEMPTS = 8;
    private static final int WINDOW_MINUTES = 15;
    private final JdbcTemplate jdbc;

    public AuthRateLimitService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void assertAllowed(String email, String remoteAddress) {
        String key = key(email, remoteAddress);
        Boolean blocked = jdbc.query(
                "SELECT COALESCE(blocked_until > now(),false) FROM auth_login_attempt_bucket WHERE bucket_key=?",
                rs -> rs.next() ? rs.getBoolean(1) : false, key);
        if (Boolean.TRUE.equals(blocked)) {
            throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.TOO_MANY_REQUESTS, "Too many sign-in attempts. Please wait 15 minutes and try again.");
        }
    }

    public void recordFailure(String email, String remoteAddress) {
        String key = key(email, remoteAddress);
        Integer attempts = jdbc.queryForObject(
                "INSERT INTO auth_login_attempt_bucket(bucket_key,attempts,window_started_at,blocked_until) " +
                "VALUES (?,1,now(),NULL) " +
                "ON CONFLICT(bucket_key) DO UPDATE SET " +
                "attempts=CASE WHEN auth_login_attempt_bucket.window_started_at < now()-interval '15 minutes' THEN 1 ELSE auth_login_attempt_bucket.attempts+1 END, " +
                "window_started_at=CASE WHEN auth_login_attempt_bucket.window_started_at < now()-interval '15 minutes' THEN now() ELSE auth_login_attempt_bucket.window_started_at END, " +
                "blocked_until=CASE WHEN (CASE WHEN auth_login_attempt_bucket.window_started_at < now()-interval '15 minutes' THEN 1 ELSE auth_login_attempt_bucket.attempts+1 END) >= ? THEN now()+interval '15 minutes' ELSE NULL END, " +
                "updated_at=now() RETURNING attempts",
                Integer.class, key, MAX_ATTEMPTS);
        if (attempts != null && attempts >= MAX_ATTEMPTS) {
            throw new LoginRateLimitException();
        }
    }

    public void clear(String email, String remoteAddress) {
        jdbc.update("DELETE FROM auth_login_attempt_bucket WHERE bucket_key=?", key(email, remoteAddress));
    }

    @org.springframework.scheduling.annotation.Scheduled(cron = "0 17 * * * *")
    public void removeExpiredBuckets() {
        jdbc.update("DELETE FROM auth_login_attempt_bucket WHERE updated_at < now()-interval '24 hours'");
    }

    private String key(String email, String remoteAddress) {
        try {
            String normalized = (email == null ? "" : email.trim().toLowerCase(java.util.Locale.ROOT))
                    + "|" + (remoteAddress == null ? "unknown" : remoteAddress);
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(normalized.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            throw new IllegalStateException("Unable to calculate login rate-limit key", ex);
        }
    }
}
