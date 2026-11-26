package com.app.config;

import com.app.security.JwtAuthenticationFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
public class SecurityConfig {
    @Bean PasswordEncoder passwordEncoder() { return new BCryptPasswordEncoder(12); }

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, JwtAuthenticationFilter jwtFilter) throws Exception {
        return http.csrf(AbstractHttpConfigurer::disable).cors(Customizer.withDefaults())
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .exceptionHandling(e -> e.authenticationEntryPoint(new org.springframework.security.web.authentication.HttpStatusEntryPoint(org.springframework.http.HttpStatus.UNAUTHORIZED)))
            .headers(h -> h.frameOptions(f -> f.sameOrigin()))
            .authorizeHttpRequests(a -> a
                .requestMatchers("/", "/index.html", "/site.css", "/home.js", "/app.html", "/app.css", "/app.js",
                    "/invitation.html", "/invitation.js", "/invitation-renderer.js", "/forgot.html", "/forgot.js", "/reset.html", "/reset.js",
                    "/verify.html", "/verify.js", "/admin.html", "/admin.js", "/template-studio.html", "/template-studio.js",
                    "/template-preview-frame.html", "/template-preview-frame.js", "/template-package-renderer.js", "/admin-invite.html", "/admin-invite.js", "/favicon.ico", "/i/**").permitAll()
                .requestMatchers("/actuator/health", "/actuator/health/**").permitAll()
                .requestMatchers(HttpMethod.POST,
                    "/api/v1/auth/register", "/api/v1/auth/verify-email", "/api/v1/auth/login",
                    "/api/v1/auth/forgot-password", "/api/v1/auth/reset-password",
                    "/api/v1/auth/refresh", "/api/v1/auth/logout", "/api/v1/auth/accept-admin-invite",
                    "/api/v1/webhooks/**").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/v1/public/**", "/api/v1/catalog/**").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/v1/public/invitations/*/rsvp").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/v1/admin/**").hasAnyRole("ADMIN","SUPER_ADMIN","SUPPORT")
                .requestMatchers("/api/v1/admin/**").hasAnyRole("ADMIN","SUPER_ADMIN")
                .anyRequest().authenticated())
            .httpBasic(AbstractHttpConfigurer::disable).formLogin(AbstractHttpConfigurer::disable)
            .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class).build();
    }
}
