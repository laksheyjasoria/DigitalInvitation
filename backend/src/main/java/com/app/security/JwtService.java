package com.app.security;

import com.app.config.JwtProperties;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import javax.crypto.SecretKey;
import org.springframework.stereotype.Service;

@Service
public class JwtService {
    private final JwtProperties properties;
    public JwtService(JwtProperties properties) { this.properties = properties; }
    private SecretKey key() {
        String secret = properties.secret();
        if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < 32)
            throw new IllegalStateException("APP_JWT_SECRET must contain at least 32 bytes");
        return Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }
    public String issue(String userId, String email, String role) {
        Instant now = Instant.now();
        return Jwts.builder().issuer(properties.issuer()).subject(userId).claim("email", email)
            .claim("role", role).issuedAt(Date.from(now))
            .expiration(Date.from(now.plusSeconds(properties.accessTokenMinutes() * 60)))
            .signWith(key()).compact();
    }
    public io.jsonwebtoken.Claims parse(String token) {
        return Jwts.parser().verifyWith(key()).requireIssuer(properties.issuer()).build()
            .parseSignedClaims(token).getPayload();
    }
}
