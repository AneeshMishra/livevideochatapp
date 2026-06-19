package com.platform.payments.api.dto;

import com.platform.payments.domain.OrderStatus;
import com.platform.payments.domain.PaymentOrder;
import com.platform.payments.domain.PaymentProvider;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record OrderResponse(
        UUID id,
        UUID userId,
        PaymentProvider provider,
        OrderStatus status,
        long tokenAmount,
        BigDecimal fiatAmount,
        String currencyCode,
        String redirectUrl,        // non-null when status=PROCESSING; redirect user here
        Instant createdAt,
        Instant completedAt
) {
    public static OrderResponse from(PaymentOrder o) {
        return new OrderResponse(
                o.getId(),
                o.getUserId(),
                o.getProvider(),
                o.getStatus(),
                o.getTokenAmount(),
                o.getFiatAmount(),
                o.getCurrencyCode(),
                o.getProviderRedirectUrl(),
                o.getCreatedAt(),
                o.getCompletedAt());
    }
}
