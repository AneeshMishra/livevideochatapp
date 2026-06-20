package com.platform.kyc.api.dto;

import com.platform.kyc.domain.ApplicantType;
import jakarta.validation.constraints.NotNull;

public record StartApplicationRequest(
        @NotNull ApplicantType applicantType
) {}
