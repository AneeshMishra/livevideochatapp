package com.platform.wallet.exception;

import java.util.UUID;

public class InsufficientFundsException extends RuntimeException {

    private final long currentBalance;
    private final long required;

    public InsufficientFundsException(UUID userId, long currentBalance, long required) {
        super(String.format("Insufficient funds for wallet %s: balance=%d required=%d",
                userId, currentBalance, required));
        this.currentBalance = currentBalance;
        this.required = required;
    }

    public long getCurrentBalance() { return currentBalance; }
    public long getRequired()       { return required; }
}
