package com.platform.payments.exception;

import java.util.UUID;

public class OrderNotFoundException extends RuntimeException {
    public OrderNotFoundException(UUID orderId) {
        super("Payment order not found: " + orderId);
    }

    public OrderNotFoundException(String description) {
        super("Payment order not found: " + description);
    }
}
