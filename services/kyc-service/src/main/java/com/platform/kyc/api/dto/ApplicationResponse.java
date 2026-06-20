package com.platform.kyc.api.dto;

import com.platform.kyc.domain.KycApplication;

import java.time.Instant;
import java.util.UUID;

public record ApplicationResponse(
        UUID id,
        UUID applicantId,
        String applicantType,
        String status,
        String vendorProvider,
        String verificationUrl,  // redirect here to complete ID check
        String rejectionReason,
        Instant verifiedAt,
        Instant expiresAt,
        Instant submittedAt,
        Instant createdAt
) {
    public static ApplicationResponse from(KycApplication a) {
        return new ApplicationResponse(
            a.getId(),
            a.getApplicantId(),
            a.getApplicantType().name(),
            a.getStatus().name(),
            a.getVendorProvider().name(),
            a.getVendorVerificationUrl(),
            a.getRejectionReason(),
            a.getVerifiedAt(),
            a.getExpiresAt(),
            a.getSubmittedAt(),
            a.getCreatedAt()
        );
    }
}
