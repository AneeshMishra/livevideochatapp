package com.platform.tipping.api.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record MenuItemRequest(

        @NotBlank
        @Size(max = 100)
        String title,

        @Size(max = 300)
        String description,

        @Min(1)
        long tokenPrice,

        int position
) {}
