package com.platform.broadcaster.api.dto;

import java.util.UUID;

public record StreamSettingsResponse(
    UUID id,
    String title,
    String tags,
    String category,
    int privateShowPricePerMinute,
    int groupShowPricePerMinute,
    int spyShowPricePerMinute,
    boolean recordingEnabled,
    int cam2camMinViewerLevel
) {}
