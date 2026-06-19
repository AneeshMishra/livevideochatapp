package com.platform.auth.api.dto;

import java.time.Instant;

public record AuthResponse(
        String accessToken,
        String refreshToken,
        String tokenType,
        Instant accessTokenExpiresAt
) {
    public static AuthResponse of(String accessToken, String refreshToken, Instant expiresAt) {
        return new AuthResponse(accessToken, refreshToken, "Bearer", expiresAt);
    }
}
