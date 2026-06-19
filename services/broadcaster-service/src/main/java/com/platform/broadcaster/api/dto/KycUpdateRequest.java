package com.platform.broadcaster.api.dto;

import com.platform.broadcaster.domain.KycStatus;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record KycUpdateRequest(
    @NotNull
    KycStatus kycStatus,

    /** Encrypted S3 reference to the uploaded 2257/KYC document bundle. */
    @Size(max = 1000)
    String kycDocumentRef
) {}
