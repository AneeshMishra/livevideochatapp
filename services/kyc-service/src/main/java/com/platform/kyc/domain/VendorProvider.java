package com.platform.kyc.domain;

public enum VendorProvider {
    MOCK,    // Auto-approve after submit — dev/test only
    VERIFF,  // Veriff identity verification
    YOTI,    // Yoti age verification
    PERSONA  // Persona.com identity verification
}
