package com.platform.kyc.domain;

public enum DocumentType {
    ID_FRONT,             // Government-issued photo ID — front
    ID_BACK,              // Government-issued photo ID — back
    SELFIE,               // Liveness selfie for face-match
    MODEL_RELEASE_2257,   // 18 USC §2257 performer release form (broadcasters only)
    PROOF_OF_AGE,         // Birth certificate or passport bio page
    ADDRESS_PROOF         // Utility bill / bank statement (optional, jurisdiction-dependent)
}
