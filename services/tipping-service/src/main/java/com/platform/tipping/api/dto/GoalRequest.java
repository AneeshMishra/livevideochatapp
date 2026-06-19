package com.platform.tipping.api.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record GoalRequest(

        @NotNull
        UUID roomId,

        @NotBlank
        @Size(max = 200)
        String title,

        @Min(1)
        long targetTokens
) {}
