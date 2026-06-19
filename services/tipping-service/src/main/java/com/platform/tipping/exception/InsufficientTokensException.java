package com.platform.tipping.exception;

import java.util.UUID;

public class InsufficientTokensException extends RuntimeException {
    public InsufficientTokensException(UUID userId) {
        super("Insufficient token balance for user: " + userId);
    }
}
