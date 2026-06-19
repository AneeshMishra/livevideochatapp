package com.platform.auth.service;

import com.platform.auth.domain.RefreshToken;
import com.platform.auth.domain.Role;
import com.platform.auth.domain.User;
import com.platform.auth.domain.UserStatus;
import com.platform.auth.event.AuthEvent;
import com.platform.auth.event.AuthEventPublisher;
import com.platform.auth.exception.AccountLockedException;
import com.platform.auth.exception.InvalidCredentialsException;
import com.platform.auth.exception.TokenExpiredException;
import com.platform.auth.exception.UserAlreadyExistsException;
import com.platform.auth.repository.RefreshTokenRepository;
import com.platform.auth.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.DigestUtils;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthService {

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AuthEventPublisher eventPublisher;

    @Value("${app.jwt.refresh-token-ttl-days}")
    private long refreshTokenTtlDays;

    @Value("${app.security.max-failed-login-attempts}")
    private int maxFailedLoginAttempts;

    @Value("${app.security.account-lock-duration-minutes}")
    private long accountLockDurationMinutes;

    public record TokenPair(String accessToken, String refreshToken, Instant accessTokenExpiresAt) {}

    @Transactional
    public TokenPair register(String email, String username, String password, String displayName) {
        if (userRepository.existsByEmail(email)) {
            throw new UserAlreadyExistsException("Email already registered");
        }
        if (userRepository.existsByUsername(username)) {
            throw new UserAlreadyExistsException("Username already taken");
        }

        User user = new User();
        user.setEmail(email.toLowerCase().strip());
        user.setUsername(username.strip());
        user.setPasswordHash(passwordEncoder.encode(password));
        user.setDisplayName(displayName != null ? displayName.strip() : username);
        user.setStatus(UserStatus.ACTIVE);
        user.setRoles(Set.of(Role.VIEWER));

        user = userRepository.save(user);

        eventPublisher.publish(new AuthEvent.UserRegistered(
                user.getId(),
                user.getEmail(),
                user.getUsername(),
                user.getRoles().stream().map(Enum::name).collect(Collectors.toSet()),
                Instant.now()
        ));

        log.info("User registered user_id={} username={}", user.getId(), user.getUsername());
        return issueTokenPair(user, null, null);
    }

    @Transactional
    public TokenPair login(String email, String password, String ipAddress, String userAgent) {
        User user = userRepository.findByEmail(email.toLowerCase().strip())
                .orElseThrow(InvalidCredentialsException::new);

        if (user.isLocked()) {
            throw new AccountLockedException(user.getLockedUntil());
        }

        if (user.getStatus() == UserStatus.SUSPENDED || user.getStatus() == UserStatus.BANNED) {
            throw new InvalidCredentialsException();
        }

        if (!passwordEncoder.matches(password, user.getPasswordHash())) {
            handleFailedLogin(user);
            throw new InvalidCredentialsException();
        }

        user.setFailedLoginAttempts(0);
        user.setLockedUntil(null);
        user.setLastLoginAt(Instant.now());
        userRepository.save(user);

        eventPublisher.publish(new AuthEvent.UserLoggedIn(user.getId(), user.getEmail(), ipAddress, Instant.now()));

        log.info("User logged in user_id={} ip={}", user.getId(), ipAddress);
        return issueTokenPair(user, ipAddress, userAgent);
    }

    @Transactional
    public TokenPair refresh(String rawRefreshToken) {
        String tokenHash = hashToken(rawRefreshToken);
        RefreshToken stored = refreshTokenRepository.findByTokenHash(tokenHash)
                .orElseThrow(TokenExpiredException::new);

        if (stored.isRevoked() || stored.isExpired()) {
            // Potential token theft — revoke all sessions for this user
            if (stored.isRevoked()) {
                log.warn("Revoked refresh token reuse detected user_id={}", stored.getUserId());
                refreshTokenRepository.revokeAllForUser(stored.getUserId());
                eventPublisher.publish(new AuthEvent.TokensRevoked(
                        stored.getUserId(), "revoked-token-reuse", Instant.now()));
            }
            throw new TokenExpiredException();
        }

        User user = userRepository.findById(stored.getUserId())
                .orElseThrow(TokenExpiredException::new);

        // Rotate: revoke old token, issue new access token (keep same refresh token for simplicity)
        stored.setRevoked(true);
        refreshTokenRepository.save(stored);

        return issueTokenPair(user, stored.getIpAddress(), stored.getUserAgent());
    }

    @Transactional
    public void logout(String rawRefreshToken) {
        String tokenHash = hashToken(rawRefreshToken);
        refreshTokenRepository.findByTokenHash(tokenHash).ifPresent(token -> {
            token.setRevoked(true);
            refreshTokenRepository.save(token);
        });
    }

    @Transactional
    public void logoutAll(UUID userId) {
        int revoked = refreshTokenRepository.revokeAllForUser(userId);
        log.info("Revoked {} refresh tokens for user_id={}", revoked, userId);
        eventPublisher.publish(new AuthEvent.TokensRevoked(userId, "logout-all", Instant.now()));
    }

    // Purge expired/revoked tokens once per hour
    @Scheduled(fixedDelay = 3_600_000)
    @Transactional
    public void purgeExpiredTokens() {
        int deleted = refreshTokenRepository.deleteExpiredAndRevoked(Instant.now());
        if (deleted > 0) {
            log.debug("Purged {} expired/revoked refresh tokens", deleted);
        }
    }

    private TokenPair issueTokenPair(User user, String ipAddress, String userAgent) {
        String accessToken = jwtService.generateAccessToken(user);

        String rawRefreshToken = UUID.randomUUID().toString();
        RefreshToken refreshToken = new RefreshToken();
        refreshToken.setUserId(user.getId());
        refreshToken.setTokenHash(hashToken(rawRefreshToken));
        refreshToken.setExpiresAt(Instant.now().plus(refreshTokenTtlDays, ChronoUnit.DAYS));
        refreshToken.setIpAddress(ipAddress);
        refreshToken.setUserAgent(userAgent != null && userAgent.length() > 255
                ? userAgent.substring(0, 255) : userAgent);
        refreshTokenRepository.save(refreshToken);

        Instant expiresAt = jwtService.validateAndExtractClaims(accessToken)
                .getExpiration().toInstant();

        return new TokenPair(accessToken, rawRefreshToken, expiresAt);
    }

    private void handleFailedLogin(User user) {
        int attempts = user.getFailedLoginAttempts() + 1;
        user.setFailedLoginAttempts(attempts);

        if (attempts >= maxFailedLoginAttempts) {
            Instant lockedUntil = Instant.now().plus(accountLockDurationMinutes, ChronoUnit.MINUTES);
            user.setLockedUntil(lockedUntil);
            userRepository.save(user);
            eventPublisher.publish(new AuthEvent.AccountLocked(
                    user.getId(), "too-many-failed-attempts", lockedUntil, Instant.now()));
            log.warn("Account locked user_id={} until={}", user.getId(), lockedUntil);
        } else {
            userRepository.save(user);
        }
    }

    private String hashToken(String rawToken) {
        return DigestUtils.md5DigestAsHex(rawToken.getBytes(StandardCharsets.UTF_8));
    }
}
