package com.platform.auth.api;

import com.platform.auth.api.dto.*;
import com.platform.auth.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
@Tag(name = "Authentication", description = "Registration, login, token refresh, and logout")
public class AuthController {

    private final AuthService authService;

    @Operation(summary = "Register a new user account")
    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public AuthResponse register(@Valid @RequestBody RegisterRequest request, HttpServletRequest http) {
        AuthService.TokenPair pair = authService.register(
                request.email(), request.username(), request.password(), request.displayName());
        return AuthResponse.of(pair.accessToken(), pair.refreshToken(), pair.accessTokenExpiresAt());
    }

    @Operation(summary = "Login with email and password")
    @PostMapping("/login")
    public AuthResponse login(@Valid @RequestBody LoginRequest request, HttpServletRequest http) {
        AuthService.TokenPair pair = authService.login(
                request.email(), request.password(),
                resolveClientIp(http), http.getHeader("User-Agent"));
        return AuthResponse.of(pair.accessToken(), pair.refreshToken(), pair.accessTokenExpiresAt());
    }

    @Operation(summary = "Exchange a refresh token for a new access token")
    @PostMapping("/refresh")
    public AuthResponse refresh(@Valid @RequestBody RefreshTokenRequest request) {
        AuthService.TokenPair pair = authService.refresh(request.refreshToken());
        return AuthResponse.of(pair.accessToken(), pair.refreshToken(), pair.accessTokenExpiresAt());
    }

    @Operation(summary = "Revoke a refresh token (logout this device)")
    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(@Valid @RequestBody LogoutRequest request) {
        authService.logout(request.refreshToken());
    }

    @Operation(summary = "Revoke all refresh tokens for the authenticated user (logout everywhere)")
    @PostMapping("/logout-all")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logoutAll(@AuthenticationPrincipal String userId) {
        authService.logoutAll(UUID.fromString(userId));
    }

    private String resolveClientIp(HttpServletRequest request) {
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            return xff.split(",")[0].strip();
        }
        return request.getRemoteAddr();
    }
}
