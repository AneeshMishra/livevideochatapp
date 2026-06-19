package com.platform.payments.exception;

public class WebhookVerificationException extends RuntimeException {
    public WebhookVerificationException(String message) {
        super(message);
    }
}
