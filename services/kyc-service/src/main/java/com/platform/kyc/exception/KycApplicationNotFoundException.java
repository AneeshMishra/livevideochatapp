package com.platform.kyc.exception;

import java.util.UUID;

public class KycApplicationNotFoundException extends RuntimeException {
    public KycApplicationNotFoundException(UUID id) {
        super("KYC application not found: " + id);
    }
    public KycApplicationNotFoundException(String message) {
        super(message);
    }
}
