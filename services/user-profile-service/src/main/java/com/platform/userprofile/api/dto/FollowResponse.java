package com.platform.userprofile.api.dto;

import com.platform.userprofile.domain.Follow;

import java.time.Instant;
import java.util.UUID;

public record FollowResponse(
        UUID followeeId,
        Instant followedAt
) {
    public static FollowResponse from(Follow f) {
        return new FollowResponse(f.getFolloweeId(), f.getCreatedAt());
    }
}
