package com.platform.wallet.exception;

public class DuplicateTransactionException extends RuntimeException {
    public DuplicateTransactionException(String idempotencyKey) {
        super("Transaction already processed: " + idempotencyKey);
    }
}
