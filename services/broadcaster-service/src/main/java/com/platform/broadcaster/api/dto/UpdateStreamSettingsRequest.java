package com.platform.broadcaster.api.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

public record UpdateStreamSettingsRequest(
    @Size(max = 200)
    String title,

    @Size(max = 500)
    String tags,

    @Size(max = 100)
    String category,

    @Min(1) @Max(10_000)
    Integer privateShowPricePerMinute,

    @Min(1) @Max(10_000)
    Integer groupShowPricePerMinute,

    @Min(1) @Max(10_000)
    Integer spyShowPricePerMinute,

    Boolean recordingEnabled,

    @Min(0)
    Integer cam2camMinViewerLevel
) {}
