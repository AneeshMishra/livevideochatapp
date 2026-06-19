package com.platform.broadcaster.api.dto;

import com.platform.broadcaster.domain.GeoBlockType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record AddGeoBlockRuleRequest(
    @NotBlank
    @Size(min = 2, max = 2)
    @Pattern(regexp = "[A-Z]{2}", message = "Must be ISO 3166-1 alpha-2 (e.g. US, GB)")
    String countryCode,

    @NotNull
    GeoBlockType type
) {}
