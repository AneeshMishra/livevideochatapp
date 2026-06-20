package com.platform.kyc.api.dto;

import jakarta.validation.constraints.Size;

public record AdminDecisionRequest(
        @Size(max = 1000) String reason
) {}
