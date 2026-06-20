package com.platform.tipping.api.dto;

import com.platform.tipping.domain.GiftType;

import java.util.UUID;

public record GiftTypeResponse(
        UUID id,
        String name,
        String slug,
        String description,
        String iconUrl,
        String animationType,
        long tokenPrice,
        int displayOrder
) {
    public static GiftTypeResponse from(GiftType g) {
        return new GiftTypeResponse(
                g.getId(), g.getName(), g.getSlug(),
                g.getDescription(), g.getIconUrl(),
                g.getAnimationType(), g.getTokenPrice(), g.getDisplayOrder()
        );
    }
}
