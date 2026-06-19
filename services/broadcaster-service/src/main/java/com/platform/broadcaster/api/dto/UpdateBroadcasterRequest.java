package com.platform.broadcaster.api.dto;

import jakarta.validation.constraints.Size;

public record UpdateBroadcasterRequest(
    @Size(min = 2, max = 100)
    String displayName,

    @Size(max = 2000)
    String bio,

    @Size(max = 500)
    String avatarUrl
) {}
