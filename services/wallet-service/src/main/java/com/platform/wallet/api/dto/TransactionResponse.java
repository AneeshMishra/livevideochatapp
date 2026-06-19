package com.platform.wallet.api.dto;

import com.platform.wallet.domain.WalletTransaction;

import java.time.Instant;
import java.util.UUID;

public record TransactionResponse(
        UUID id,
        UUID walletId,
        long amount,
        String transactionType,
        long balanceAfter,
        UUID referenceId,
        String referenceType,
        UUID counterpartWalletId,
        String description,
        Instant createdAt
) {
    public static TransactionResponse from(WalletTransaction tx) {
        return new TransactionResponse(
                tx.getId(), tx.getWalletId(), tx.getAmount(),
                tx.getTransactionType().name(), tx.getBalanceAfter(),
                tx.getReferenceId(), tx.getReferenceType(),
                tx.getCounterpartWalletId(), tx.getDescription(),
                tx.getCreatedAt());
    }
}
