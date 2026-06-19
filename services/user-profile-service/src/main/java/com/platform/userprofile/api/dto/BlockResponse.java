package com.platform.userprofile.api.dto;

import com.platform.userprofile.domain.BlockEntry;

import java.time.Instant;
import java.util.UUID;

public record BlockResponse(
        UUID blockedId,
        String reason,
        Instant blockedAt
) {
    public static BlockResponse from(BlockEntry b) {
        return new BlockResponse(b.getBlockedId(), b.getReason(), b.getCreatedAt());
    }
}
