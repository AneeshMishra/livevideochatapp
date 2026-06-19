package com.platform.userprofile.api.dto;

import jakarta.validation.constraints.Size;

public record BlockRequest(
        @Size(max = 255)
        String reason
) {}
