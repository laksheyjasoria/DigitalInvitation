package com.app.auth;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

@Service
public class AuthRateLimitService {
    private final JdbcTemplate jdbc;
    private final int maxAttempts;
    private final int windowMinutes;
    private final int blockMinutes;

    public AuthRateLimitService(JdbcTemplate jdbc,
            @org.springframework.beans.factory.annotation.Value("${app.security.login.max-attempts:8}") int maxAttempts,
            @org.springframework.beans.factory.annotation.Value("${app.security.login.window-minutes:15}") int windowMinutes,
            @org.springframework.beans.factory.annotation.Value("${app.security.login.block-minutes:15}") int blockMinutes) {
        if (maxAttempts < 1 || windowMinutes < 1 || blockMinutes < 1) {
            throw new IllegalArgumentException("Login rate-limit settings must be positive");
        }
        this.jdbc = jdbc;
        this.maxAttempts = maxAttempts;
        this.windowMinutes = windowMinutes;
        this.blockMinutes = blockMinutes;
    }

    public void assertAllowed(String email, String remoteAddress) {
        String key = key(email, remoteAddress);
        Boolean blocked = jdbc.query(
                "SELECT COALESCE(blocked_until > now(),false) FROM auth_login_attempt_bucket WHERE bucket_key=?",
                rs -> rs.next() ? rs.getBoolean(1) : false, key);
        if (Boolean.TRUE.equals(blocked)) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
                    "Too many sign-in attempts. Please wait " + blockMinutes + " minutes and try again.");
        }
    }

    public void recordFailure(String email, String remoteAddress) {
        String key = key(email, remoteAddress);
        Integer attempts = jdbc.queryForObject(
                "INSERT INTO auth_login_attempt_bucket(bucket_key,attempts,window_started_at,blocked_until) " +
                "VALUES (?,1,now(),NULL) " +
                "ON CONFLICT(bucket_key) DO UPDATE SET " +
                "attempts=CASE WHEN auth_login_attempt_bucket.window_started_at < now()-(? * interval '1 minute') THEN 1 ELSE auth_login_attempt_bucket.attempts+1 END, " +
                "window_started_at=CASE WHEN auth_login_attempt_bucket.window_started_at < now()-(? * interval '1 minute') THEN now() ELSE auth_login_attempt_bucket.window_started_at END, " +
                "blocked_until=CASE WHEN (CASE WHEN auth_login_attempt_bucket.window_started_at < now()-(? * interval '1 minute') THEN 1 ELSE auth_login_attempt_bucket.attempts+1 END) >= ? THEN now()+(? * interval '1 minute') ELSE NULL END, " +
                "updated_at=now() RETURNING attempts",
                Integer.class, key, windowMinutes, windowMinutes, windowMinutes, maxAttempts, blockMinutes);
        if (attempts != null && attempts >= maxAttempts) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
                    "Too many sign-in attempts. Please wait " + blockMinutes + " minutes and try again.");
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
