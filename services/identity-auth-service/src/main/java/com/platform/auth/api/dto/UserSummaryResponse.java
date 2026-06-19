package com.platform.auth.api.dto;

import com.platform.auth.domain.User;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

public record UserSummaryResponse(
        UUID id,
        String email,
        String username,
        String displayName,
        String status,
        Set<String> roles,
        boolean mfaEnabled,
        Instant createdAt,
        Instant lastLoginAt
) {
    public static UserSummaryResponse from(User user) {
        return new UserSummaryResponse(
                user.getId(),
                user.getEmail(),
                user.getUsername(),
                user.getDisplayName(),
                user.getStatus().name(),
                user.getRoles().stream().map(Enum::name).collect(Collectors.toSet()),
                user.isMfaEnabled(),
                user.getCreatedAt(),
                user.getLastLoginAt()
        );
    }
}
