package com.platform.wallet.api.dto;

import com.platform.wallet.domain.TransactionType;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record DebitRequest(

        @NotNull
        UUID userId,

        @Min(1)
        long amount,

        @NotNull
        TransactionType transactionType,

        UUID referenceId,

        @Size(max = 50)
        String referenceType,

        @NotBlank
        @Size(max = 255)
        String idempotencyKey,

        @Size(max = 500)
        String description
) {}
