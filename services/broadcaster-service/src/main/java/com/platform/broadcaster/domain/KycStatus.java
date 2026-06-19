package com.platform.broadcaster.domain;

public enum KycStatus {
    PENDING,      // documents not yet submitted
    UNDER_REVIEW, // submitted; awaiting vendor result
    APPROVED,     // verified 18+, 2257 docs accepted
    REJECTED,     // failed verification
    EXPIRED       // re-verification required
}
