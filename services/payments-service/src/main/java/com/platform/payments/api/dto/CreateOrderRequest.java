package com.platform.payments.api.dto;

import com.platform.payments.domain.PaymentProvider;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record CreateOrderRequest(
        @NotNull(message = "packageId is required")
        @Positive(message = "packageId must be positive")
        Long packageId,

        @NotNull(message = "provider is required")
        PaymentProvider provider
) {}
