package com.platform.tipping.client;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.Date;
import java.util.List;

/**
 * Generates a short-lived ADMIN JWT signed with the shared JWT_SECRET.
 * The wallet-service's SecurityConfig accepts this token, granting ADMIN access
 * to the /api/v1/wallet/internal/* endpoints for service-to-service calls.
 *
 * Token is cached and refreshed 5 minutes before expiry.
 */
@Component
@Slf4j
public class ServiceAccountTokenProvider {

    @Value("${app.jwt.secret}")
    private String jwtSecretBase64;

    private volatile String cachedToken;
    private volatile Instant expiresAt = Instant.EPOCH;

    private static final long TOKEN_TTL_HOURS = 1;
    private static final long REFRESH_MARGIN_MINUTES = 5;

    public String getToken() {
        if (Instant.now().isAfter(expiresAt.minus(REFRESH_MARGIN_MINUTES, ChronoUnit.MINUTES))) {
            cachedToken = generateToken();
            expiresAt = Instant.now().plus(TOKEN_TTL_HOURS, ChronoUnit.HOURS);
            log.debug("Service account token refreshed, expires at {}", expiresAt);
        }
        return cachedToken;
    }

    private String generateToken() {
        SecretKey key = Keys.hmacShaKeyFor(Base64.getDecoder().decode(jwtSecretBase64));
        Instant now = Instant.now();
        return Jwts.builder()
                .subject("tipping-service")
                .claim("roles", List.of("ADMIN"))
                .claim("service", true)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(TOKEN_TTL_HOURS, ChronoUnit.HOURS)))
                .signWith(key)
                .compact();
    }
}
