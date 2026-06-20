package com.platform.kyc.domain;

public enum KycStatus {
    PENDING,       // Application created; awaiting document uploads
    SUBMITTED,     // Documents uploaded; sent to vendor for review
    UNDER_REVIEW,  // Vendor acknowledged; human or AI review in progress
    APPROVED,      // Verified 18+; 2257 docs accepted (broadcasters)
    REJECTED,      // Failed verification; reason stored in rejection_reason
    EXPIRED        // Verification period lapsed; re-verification required
}
