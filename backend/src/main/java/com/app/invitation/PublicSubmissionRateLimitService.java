package com.app.invitation;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

@Service
public class PublicSubmissionRateLimitService {
    private static final int MAX_SUBMISSIONS_PER_HOUR = 5;
    private final JdbcTemplate jdbc;

    public PublicSubmissionRateLimitService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void assertAllowed(UUID publicationId, String remoteAddress) {
        String key = key(publicationId, remoteAddress);
        Integer attempts = jdbc.queryForObject(
                "INSERT INTO public_submission_bucket(bucket_key,attempts,window_started_at) VALUES (?,1,now()) " +
                "ON CONFLICT(bucket_key) DO UPDATE SET " +
                "attempts=CASE WHEN public_submission_bucket.window_started_at < now()-interval '1 hour' THEN 1 ELSE public_submission_bucket.attempts+1 END, " +
                "window_started_at=CASE WHEN public_submission_bucket.window_started_at < now()-interval '1 hour' THEN now() ELSE public_submission_bucket.window_started_at END, " +
                "updated_at=now() RETURNING attempts",
                Integer.class, key);
        if (attempts != null && attempts > MAX_SUBMISSIONS_PER_HOUR) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
                    "Too many RSVP submissions. Please try again later.");
        }
    }

    @org.springframework.scheduling.annotation.Scheduled(cron = "0 29 * * * *")
    public void removeExpiredBuckets() {
        jdbc.update("DELETE FROM public_submission_bucket WHERE updated_at < now()-interval '48 hours'");
    }

    private String key(UUID publicationId, String remoteAddress) {
        try {
            String value = publicationId + "|" + (remoteAddress == null ? "unknown" : remoteAddress);
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            throw new IllegalStateException("Unable to calculate RSVP rate-limit key", ex);
        }
    }
}
