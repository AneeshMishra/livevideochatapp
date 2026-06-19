package com.platform.payments.exception;

public class PackageNotFoundException extends RuntimeException {
    public PackageNotFoundException(long packageId) {
        super("Token package not found: " + packageId);
    }
}
