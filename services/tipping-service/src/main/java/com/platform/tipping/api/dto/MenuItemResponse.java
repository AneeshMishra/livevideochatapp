package com.platform.tipping.api.dto;

import com.platform.tipping.domain.TipMenuItem;

import java.util.UUID;

public record MenuItemResponse(
        UUID id,
        UUID broadcasterId,
        String title,
        String description,
        long tokenPrice,
        int position,
        boolean active
) {
    public static MenuItemResponse from(TipMenuItem item) {
        return new MenuItemResponse(
                item.getId(), item.getBroadcasterId(),
                item.getTitle(), item.getDescription(),
                item.getTokenPrice(), item.getPosition(), item.isActive());
    }
}
