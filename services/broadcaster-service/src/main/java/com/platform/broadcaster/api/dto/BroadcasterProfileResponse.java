package com.platform.broadcaster.api.dto;

import com.platform.broadcaster.domain.BroadcasterStatus;
import com.platform.broadcaster.domain.KycStatus;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record BroadcasterProfileResponse(
    UUID id,
    UUID userId,
    UUID studioId,
    String displayName,
    String bio,
    String avatarUrl,
    BroadcasterStatus status,
    KycStatus kycStatus,
    int revenueSplitPercent,
    StreamSettingsResponse streamSettings,
    List<TipMenuItemResponse> tipMenuItems,
    List<GeoBlockRuleResponse> geoBlockRules,
    Instant createdAt,
    Instant updatedAt
) {}
