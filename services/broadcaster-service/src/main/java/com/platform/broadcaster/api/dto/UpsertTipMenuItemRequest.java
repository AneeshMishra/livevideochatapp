package com.platform.broadcaster.api.dto;

import jakarta.validation.constraints.*;

public record UpsertTipMenuItemRequest(
    @NotBlank @Size(max = 100)
    String label,

    @Size(max = 300)
    String description,

    @Min(1) @Max(100_000)
    int tokenPrice,

    int sortOrder
) {}
