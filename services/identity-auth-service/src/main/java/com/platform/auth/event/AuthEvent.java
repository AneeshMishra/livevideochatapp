package com.platform.auth.event;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

public sealed interface AuthEvent permits
        AuthEvent.UserRegistered,
        AuthEvent.UserLoggedIn,
        AuthEvent.PasswordChanged,
        AuthEvent.AccountLocked,
        AuthEvent.AccountUnlocked,
        AuthEvent.TokensRevoked {

    String eventType();
    Instant occurredAt();

    record UserRegistered(
            UUID userId,
            String email,
            String username,
            Set<String> roles,
            Instant occurredAt
    ) implements AuthEvent {
        public String eventType() { return "USER_REGISTERED"; }
    }

    record UserLoggedIn(
            UUID userId,
            String email,
            String ipAddress,
            Instant occurredAt
    ) implements AuthEvent {
        public String eventType() { return "USER_LOGGED_IN"; }
    }

    record PasswordChanged(
            UUID userId,
            Instant occurredAt
    ) implements AuthEvent {
        public String eventType() { return "PASSWORD_CHANGED"; }
    }

    record AccountLocked(
            UUID userId,
            String reason,
            Instant lockedUntil,
            Instant occurredAt
    ) implements AuthEvent {
        public String eventType() { return "ACCOUNT_LOCKED"; }
    }

    record AccountUnlocked(
            UUID userId,
            Instant occurredAt
    ) implements AuthEvent {
        public String eventType() { return "ACCOUNT_UNLOCKED"; }
    }

    record TokensRevoked(
            UUID userId,
            String reason,
            Instant occurredAt
    ) implements AuthEvent {
        public String eventType() { return "TOKENS_REVOKED"; }
    }
}
