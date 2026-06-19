package com.platform.wallet.api.dto;

import com.platform.wallet.domain.TransactionType;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record TransferRequest(

        @NotNull
        UUID fromUserId,

        @NotNull
        UUID toUserId,

        /** Gross amount deducted from sender — platform fee is subtracted before crediting receiver */
        @Min(1)
        long grossAmount,

        @NotNull
        TransactionType senderTransactionType,

        @NotNull
        TransactionType receiverTransactionType,

        UUID referenceId,

        @Size(max = 50)
        String referenceType,

        @NotBlank
        @Size(max = 255)
        String idempotencyKey
) {}
