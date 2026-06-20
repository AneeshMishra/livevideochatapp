package com.platform.tipping.api.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateGiftTypeRequest(
        @NotBlank @Size(max = 100) String name,
        @NotBlank @Size(max = 50) String slug,
        @Size(max = 255) String description,
        @Size(max = 500) String iconUrl,
        @NotBlank String animationType,
        @NotNull @Min(1) Long tokenPrice,
        int displayOrder
) {}
