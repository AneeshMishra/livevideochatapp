package com.platform.broadcaster.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record CreateBroadcasterRequest(
    @NotNull UUID userId,
    UUID studioId,           // optional

    @NotBlank
    @Size(min = 2, max = 100)
    String displayName,

    @Size(max = 2000)
    String bio
) {}
