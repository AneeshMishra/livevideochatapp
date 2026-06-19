package com.platform.wallet.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(
    name = "wallet_transactions",
    indexes = {
        @Index(name = "idx_wallet_tx_wallet_id",      columnList = "wallet_id"),
        @Index(name = "idx_wallet_tx_idempotency_key", columnList = "idempotency_key"),
        @Index(name = "idx_wallet_tx_reference_id",    columnList = "reference_id"),
        @Index(name = "idx_wallet_tx_created_at",      columnList = "created_at DESC")
    }
)
@Getter
@Setter
@NoArgsConstructor
public class WalletTransaction {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "wallet_id", nullable = false)
    private UUID walletId;

    /**
     * Signed token amount:
     *   positive = credit (tokens added)
     *   negative = debit  (tokens removed)
     */
    @Column(nullable = false)
    private long amount;

    @Enumerated(EnumType.STRING)
    @Column(name = "transaction_type", nullable = false, length = 30)
    private TransactionType transactionType;

    /**
     * Running balance of the wallet AFTER this entry was applied.
     * Enables quick audit reconciliation without re-summing the full ledger.
     */
    @Column(name = "balance_after", nullable = false)
    private long balanceAfter;

    /**
     * Opaque caller-provided key — unique constraint prevents double-apply.
     * Format convention: {caller-service}:{operation}:{external-id}
     */
    @Column(name = "idempotency_key", nullable = false, unique = true, length = 255)
    private String idempotencyKey;

    /** ID of the entity that triggered this transaction (tipId, orderId, sessionId …) */
    @Column(name = "reference_id")
    private UUID referenceId;

    @Column(name = "reference_type", length = 50)
    private String referenceType;

    /** Counterpart wallet in a transfer (null for purchases/adjustments) */
    @Column(name = "counterpart_wallet_id")
    private UUID counterpartWalletId;

    @Column(length = 500)
    private String description;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();
}
