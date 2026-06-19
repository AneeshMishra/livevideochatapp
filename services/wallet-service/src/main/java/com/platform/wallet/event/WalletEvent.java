package com.platform.wallet.event;

import com.platform.wallet.domain.TransactionType;

import java.time.Instant;
import java.util.UUID;

public sealed interface WalletEvent permits
        WalletEvent.WalletCreated,
        WalletEvent.TokensCredited,
        WalletEvent.TokensDebited,
        WalletEvent.TransferCompleted {

    String eventType();
    Instant occurredAt();

    record WalletCreated(
            UUID userId,
            Instant occurredAt
    ) implements WalletEvent {
        public String eventType() { return "WALLET_CREATED"; }
    }

    record TokensCredited(
            UUID walletId,
            long amount,
            long balanceAfter,
            TransactionType transactionType,
            UUID transactionId,
            String idempotencyKey,
            Instant occurredAt
    ) implements WalletEvent {
        public String eventType() { return "TOKENS_CREDITED"; }
    }

    record TokensDebited(
            UUID walletId,
            long amount,
            long balanceAfter,
            TransactionType transactionType,
            UUID transactionId,
            String idempotencyKey,
            Instant occurredAt
    ) implements WalletEvent {
        public String eventType() { return "TOKENS_DEBITED"; }
    }

    record TransferCompleted(
            UUID fromWalletId,
            UUID toWalletId,
            long amount,
            long platformFee,
            TransactionType transactionType,
            UUID referenceId,
            String idempotencyKey,
            Instant occurredAt
    ) implements WalletEvent {
        public String eventType() { return "TRANSFER_COMPLETED"; }
    }
}
