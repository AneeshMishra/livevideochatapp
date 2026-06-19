package com.platform.wallet.repository;

import com.platform.wallet.domain.WalletTransaction;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface WalletTransactionRepository extends JpaRepository<WalletTransaction, UUID> {

    /**
     * Idempotency check — if a transaction with this key already exists, return it.
     * The unique DB constraint is the final guard; this read avoids a constraint violation on retry.
     */
    Optional<WalletTransaction> findByIdempotencyKey(String idempotencyKey);

    Page<WalletTransaction> findByWalletIdOrderByCreatedAtDesc(UUID walletId, Pageable pageable);

    Page<WalletTransaction> findByReferenceIdOrderByCreatedAtDesc(UUID referenceId, Pageable pageable);
}
