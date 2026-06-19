package com.platform.auth.service;

import com.platform.auth.domain.Role;
import com.platform.auth.domain.User;
import com.platform.auth.domain.UserStatus;
import com.platform.auth.event.AuthEventPublisher;
import com.platform.auth.exception.InvalidCredentialsException;
import com.platform.auth.exception.UserAlreadyExistsException;
import com.platform.auth.repository.RefreshTokenRepository;
import com.platform.auth.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock UserRepository userRepository;
    @Mock RefreshTokenRepository refreshTokenRepository;
    @Mock JwtService jwtService;
    @Mock AuthEventPublisher eventPublisher;

    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder(4);

    @InjectMocks
    AuthService authService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(authService, "passwordEncoder", passwordEncoder);
        ReflectionTestUtils.setField(authService, "refreshTokenTtlDays", 7L);
        ReflectionTestUtils.setField(authService, "maxFailedLoginAttempts", 5);
        ReflectionTestUtils.setField(authService, "accountLockDurationMinutes", 30L);
    }

    @Test
    void register_duplicateEmail_throws() {
        when(userRepository.existsByEmail("test@example.com")).thenReturn(true);

        assertThatThrownBy(() -> authService.register("test@example.com", "user1", "password123", null))
                .isInstanceOf(UserAlreadyExistsException.class)
                .hasMessageContaining("Email");
    }

    @Test
    void register_duplicateUsername_throws() {
        when(userRepository.existsByEmail(any())).thenReturn(false);
        when(userRepository.existsByUsername("user1")).thenReturn(true);

        assertThatThrownBy(() -> authService.register("new@example.com", "user1", "password123", null))
                .isInstanceOf(UserAlreadyExistsException.class)
                .hasMessageContaining("Username");
    }

    @Test
    void register_success_assignsViewerRole() {
        when(userRepository.existsByEmail(any())).thenReturn(false);
        when(userRepository.existsByUsername(any())).thenReturn(false);

        User saved = new User();
        saved.setId(UUID.randomUUID());
        saved.setEmail("new@example.com");
        saved.setUsername("newuser");
        saved.setPasswordHash(passwordEncoder.encode("password123"));
        saved.setStatus(UserStatus.ACTIVE);
        saved.setRoles(Set.of(Role.VIEWER));

        when(userRepository.save(any())).thenReturn(saved);
        when(jwtService.generateAccessToken(any())).thenReturn("access.token.here");
        when(refreshTokenRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        var claims = mock(io.jsonwebtoken.Claims.class);
        when(claims.getExpiration()).thenReturn(new java.util.Date(System.currentTimeMillis() + 900_000));
        when(jwtService.validateAndExtractClaims("access.token.here")).thenReturn(claims);

        var pair = authService.register("new@example.com", "newuser", "password123", null);

        assertThat(pair.accessToken()).isEqualTo("access.token.here");
        assertThat(pair.refreshToken()).isNotBlank();
        verify(eventPublisher).publish(any());
    }

    @Test
    void login_wrongPassword_throws() {
        User user = new User();
        user.setId(UUID.randomUUID());
        user.setEmail("test@example.com");
        user.setPasswordHash(passwordEncoder.encode("correct-password"));
        user.setStatus(UserStatus.ACTIVE);
        user.setRoles(Set.of(Role.VIEWER));

        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(user));
        when(userRepository.save(any())).thenReturn(user);

        assertThatThrownBy(() -> authService.login("test@example.com", "wrong-password", "127.0.0.1", null))
                .isInstanceOf(InvalidCredentialsException.class);

        assertThat(user.getFailedLoginAttempts()).isEqualTo(1);
    }

    @Test
    void login_unknownEmail_throws() {
        when(userRepository.findByEmail(any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.login("no@example.com", "pass", "127.0.0.1", null))
                .isInstanceOf(InvalidCredentialsException.class);
    }
}
