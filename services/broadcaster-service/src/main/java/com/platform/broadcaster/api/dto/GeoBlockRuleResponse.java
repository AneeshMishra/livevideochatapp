package com.platform.broadcaster.api.dto;

import com.platform.broadcaster.domain.GeoBlockType;

import java.util.UUID;

public record GeoBlockRuleResponse(
    UUID id,
    String countryCode,
    GeoBlockType type
) {}
