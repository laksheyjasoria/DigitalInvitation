package com.app.security;

import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {
    private final JwtService jwtService;
    private final JdbcTemplate jdbc;

    public JwtAuthenticationFilter(JwtService jwtService, JdbcTemplate jdbc) {
        this.jwtService = jwtService;
        this.jdbc = jdbc;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            try {
                Claims claims = jwtService.parse(header.substring(7));
                UUID userId = UUID.fromString(claims.getSubject());
                long tokenVersion = ((Number) claims.get("ver")).longValue();
                List<Map<String, Object>> users = jdbc.query(
                        "SELECT auth_version,role,status FROM app_user WHERE id=?",
                        (rs, row) -> Map.of("version", rs.getLong("auth_version"),
                                "role", rs.getString("role"), "status", rs.getString("status")),
                        userId);
                if (!users.isEmpty()) {
                    Map<String, Object> user = users.get(0);
                    long currentVersion = ((Number) user.get("version")).longValue();
                    if ("ACTIVE".equals(user.get("status")) && tokenVersion == currentVersion) {
                        var authentication = new UsernamePasswordAuthenticationToken(userId.toString(), null,
                                List.of(new SimpleGrantedAuthority("ROLE_" + user.get("role"))));
                        SecurityContextHolder.getContext().setAuthentication(authentication);
                    }
                }
            } catch (RuntimeException ignored) {
                SecurityContextHolder.clearContext();
            }
        }
        chain.doFilter(request, response);
    }
}
