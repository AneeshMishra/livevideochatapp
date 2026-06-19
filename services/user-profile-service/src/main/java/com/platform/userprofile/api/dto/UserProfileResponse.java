package com.platform.userprofile.api.dto;

import com.platform.userprofile.domain.UserProfile;

import java.time.Instant;
import java.util.UUID;

public record UserProfileResponse(
        UUID userId,
        String username,
        String displayName,
        String avatarUrl,
        String bio,
        String language,
        String country,
        int followingCount,
        Instant createdAt
) {
    public static UserProfileResponse from(UserProfile p) {
        return new UserProfileResponse(
                p.getUserId(), p.getUsername(), p.getDisplayName(),
                p.getAvatarUrl(), p.getBio(), p.getLanguage(),
                p.getCountry(), p.getFollowingCount(), p.getCreatedAt()
        );
    }
}
