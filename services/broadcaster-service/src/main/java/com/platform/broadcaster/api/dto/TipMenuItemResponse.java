package com.platform.broadcaster.api.dto;

import java.util.UUID;

public record TipMenuItemResponse(
    UUID id,
    String label,
    String description,
    int tokenPrice,
    int sortOrder,
    boolean active
) {}
