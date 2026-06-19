package com.platform.wallet.service;

import com.platform.wallet.domain.TransactionType;
import com.platform.wallet.domain.Wallet;
import com.platform.wallet.domain.WalletTransaction;
import com.platform.wallet.event.WalletEvent;
import com.platform.wallet.event.WalletEventPublisher;
import com.platform.wallet.exception.WalletNotFoundException;
import com.platform.wallet.repository.WalletRepository;
import com.platform.wallet.repository.WalletTransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class WalletService {

    private final WalletRepository walletRepo;
    private final WalletTransactionRepository txRepo;
    private final WalletEventPublisher eventPublisher;

    @Value("${app.wallet.platform-fee-bps}")
    private int platformFeeBps;

    @Value("${app.wallet.platform-wallet-id}")
    private UUID platformWalletId;

    // ── Wallet lifecycle ────────────────────────────────────────────────────

    /**
     * Idempotent — safe to call multiple times (e.g. Kafka replay).
     */
    @Transactional
    public Wallet createWallet(UUID userId) {
        return walletRepo.findById(userId).orElseGet(() -> {
            Wallet wallet = Wallet.forUser(userId);
            Wallet saved = walletRepo.save(wallet);
            eventPublisher.publish(new WalletEvent.WalletCreated(userId, Instant.now()));
            return saved;
        });
    }

    // ── Balance queries (read-only, no lock needed) ─────────────────────────

    @Transactional(readOnly = true)
    public Wallet getWallet(UUID userId) {
        return walletRepo.findById(userId)
                .orElseThrow(() -> new WalletNotFoundException(userId));
    }

    @Transactional(readOnly = true)
    public Page<WalletTransaction> getTransactions(UUID userId, Pageable pageable) {
        return txRepo.findByWalletIdOrderByCreatedAtDesc(userId, pageable);
    }

    // ── Credit (tokens added to wallet) ────────────────────────────────────

    /**
     * Credits tokens into a wallet.
     * Idempotent via idempotencyKey — safe to retry on failure.
     *
     * @param userId         target wallet owner
     * @param amount         positive token count
     * @param type           transaction type (e.g. PURCHASE, TIP_RECEIVED)
     * @param referenceId    source entity id (e.g. paymentId)
     * @param referenceType  source entity type (e.g. "payment")
     * @param idempotencyKey caller-provided deduplication key
     * @param description    human-readable description
     */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public WalletTransaction credit(
            UUID userId, long amount,
            TransactionType type, UUID referenceId, String referenceType,
            String idempotencyKey, String description
    ) {
        // Fast-path: already processed
        var existing = txRepo.findByIdempotencyKey(idempotencyKey);
        if (existing.isPresent()) {
            log.debug("Idempotent credit replay idempotency_key={}", idempotencyKey);
            return existing.get();
        }

        // Acquire row-level exclusive lock — prevents concurrent balance changes
        Wallet wallet = walletRepo.findByUserIdForUpdate(userId)
                .orElseThrow(() -> new WalletNotFoundException(userId));

        wallet.credit(amount);
        walletRepo.save(wallet);

        WalletTransaction tx = buildTx(wallet.getUserId(), amount, type,
                referenceId, referenceType, null, idempotencyKey,
                wallet.getBalance(), description);

        WalletTransaction saved = saveTxSafe(tx, idempotencyKey);

        eventPublisher.publish(new WalletEvent.TokensCredited(
                wallet.getUserId(), amount, wallet.getBalance(),
                type, saved.getId(), idempotencyKey, Instant.now()));

        log.info("Credited user_id={} amount={} balance_after={} type={}",
                userId, amount, wallet.getBalance(), type);
        return saved;
    }

    // ── Debit (tokens removed from wallet) ─────────────────────────────────

    /**
     * Debits tokens from a wallet.
     * Throws {@link com.platform.wallet.exception.InsufficientFundsException} if balance insufficient.
     * Idempotent via idempotencyKey.
     */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public WalletTransaction debit(
            UUID userId, long amount,
            TransactionType type, UUID referenceId, String referenceType,
            String idempotencyKey, String description
    ) {
        var existing = txRepo.findByIdempotencyKey(idempotencyKey);
        if (existing.isPresent()) {
            log.debug("Idempotent debit replay idempotency_key={}", idempotencyKey);
            return existing.get();
        }

        Wallet wallet = walletRepo.findByUserIdForUpdate(userId)
                .orElseThrow(() -> new WalletNotFoundException(userId));

        // debit() throws InsufficientFundsException if balance < amount
        wallet.debit(amount);
        walletRepo.save(wallet);

        WalletTransaction tx = buildTx(wallet.getUserId(), -amount, type,
                referenceId, referenceType, null, idempotencyKey,
                wallet.getBalance(), description);

        WalletTransaction saved = saveTxSafe(tx, idempotencyKey);

        eventPublisher.publish(new WalletEvent.TokensDebited(
                wallet.getUserId(), amount, wallet.getBalance(),
                type, saved.getId(), idempotencyKey, Instant.now()));

        log.info("Debited user_id={} amount={} balance_after={} type={}",
                userId, amount, wallet.getBalance(), type);
        return saved;
    }

    // ── Transfer (viewer tips broadcaster) ──────────────────────────────────

    /**
     * Atomically transfers tokens from viewer to broadcaster, deducting platform fee.
     * Deadlock-safe: wallets are locked in consistent UUID order.
     *
     * @param fromUserId     sender (viewer)
     * @param toUserId       receiver (broadcaster)
     * @param grossAmount    total tokens the sender pays
     * @param type           e.g. TIP_SENT / TIP_RECEIVED
     * @param referenceId    tipId or sessionId
     * @param referenceType  "tip" | "private_show" etc.
     * @param idempotencyKey deduplication key
     */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public TransferResult transfer(
            UUID fromUserId, UUID toUserId, long grossAmount,
            TransactionType senderType, TransactionType receiverType,
            UUID referenceId, String referenceType,
            String idempotencyKey
    ) {
        // Idempotency: check for the sender leg (unique key)
        String senderKey  = idempotencyKey + ":debit";
        String receiverKey = idempotencyKey + ":credit";
        String feeKey     = idempotencyKey + ":fee";

        var existingSender = txRepo.findByIdempotencyKey(senderKey);
        if (existingSender.isPresent()) {
            log.debug("Idempotent transfer replay idempotency_key={}", idempotencyKey);
            var existingReceiver = txRepo.findByIdempotencyKey(receiverKey).orElse(null);
            return new TransferResult(existingSender.get(), existingReceiver, null);
        }

        long fee          = (grossAmount * platformFeeBps) / 10_000L;
        long netToReceiver = grossAmount - fee;

        // ── Lock wallets in consistent UUID order to prevent deadlock ───────
        boolean fromFirst = fromUserId.compareTo(toUserId) <= 0;
        UUID firstId  = fromFirst ? fromUserId : toUserId;
        UUID secondId = fromFirst ? toUserId   : fromUserId;

        Wallet firstWallet  = walletRepo.findByUserIdForUpdate(firstId)
                .orElseThrow(() -> new WalletNotFoundException(firstId));
        Wallet secondWallet = walletRepo.findByUserIdForUpdate(secondId)
                .orElseThrow(() -> new WalletNotFoundException(secondId));

        Wallet fromWallet = fromFirst ? firstWallet : secondWallet;
        Wallet toWallet   = fromFirst ? secondWallet : firstWallet;

        // Apply balance changes
        fromWallet.debit(grossAmount);
        toWallet.credit(netToReceiver);

        walletRepo.save(fromWallet);
        walletRepo.save(toWallet);

        // Persist two ledger entries + optional platform fee entry
        WalletTransaction senderTx = buildTx(fromWallet.getUserId(), -grossAmount, senderType,
                referenceId, referenceType, toWallet.getUserId(), senderKey,
                fromWallet.getBalance(),
                String.format("Transfer to %s (gross=%d)", toUserId, grossAmount));

        WalletTransaction receiverTx = buildTx(toWallet.getUserId(), netToReceiver, receiverType,
                referenceId, referenceType, fromWallet.getUserId(), receiverKey,
                toWallet.getBalance(),
                String.format("Transfer from %s (net=%d fee=%d)", fromUserId, netToReceiver, fee));

        WalletTransaction savedSender   = saveTxSafe(senderTx, senderKey);
        WalletTransaction savedReceiver = saveTxSafe(receiverTx, receiverKey);

        // Platform fee credit (if non-zero)
        WalletTransaction feeTx = null;
        if (fee > 0) {
            Wallet platformWallet = walletRepo.findByUserIdForUpdate(platformWalletId)
                    .orElseGet(() -> walletRepo.save(Wallet.forUser(platformWalletId)));
            platformWallet.credit(fee);
            walletRepo.save(platformWallet);

            WalletTransaction feeEntry = buildTx(platformWalletId, fee, TransactionType.PLATFORM_FEE,
                    referenceId, referenceType, fromWallet.getUserId(), feeKey,
                    platformWallet.getBalance(),
                    String.format("Platform fee from transfer %s", referenceId));
            feeTx = saveTxSafe(feeEntry, feeKey);
        }

        eventPublisher.publish(new WalletEvent.TransferCompleted(
                fromUserId, toUserId, grossAmount, fee, senderType,
                referenceId, idempotencyKey, Instant.now()));

        log.info("Transfer complete from={} to={} gross={} fee={} ref={}",
                fromUserId, toUserId, grossAmount, fee, referenceId);

        return new TransferResult(savedSender, savedReceiver, feeTx);
    }

    // ── Helpers ─────────────────────────────────────────────────────────────

    private WalletTransaction buildTx(
            UUID walletId, long amount, TransactionType type,
            UUID referenceId, String referenceType, UUID counterpart,
            String idempotencyKey, long balanceAfter, String description
    ) {
        WalletTransaction tx = new WalletTransaction();
        tx.setWalletId(walletId);
        tx.setAmount(amount);
        tx.setTransactionType(type);
        tx.setReferenceId(referenceId);
        tx.setReferenceType(referenceType);
        tx.setCounterpartWalletId(counterpart);
        tx.setIdempotencyKey(idempotencyKey);
        tx.setBalanceAfter(balanceAfter);
        tx.setDescription(description);
        return tx;
    }

    /**
     * Saves a transaction, handling the race where a concurrent thread already
     * persisted the same idempotency key (last-write-wins on the unique constraint).
     */
    private WalletTransaction saveTxSafe(WalletTransaction tx, String idempotencyKey) {
        try {
            return txRepo.save(tx);
        } catch (DataIntegrityViolationException ex) {
            // Unique constraint on idempotency_key — a concurrent thread won the race
            return txRepo.findByIdempotencyKey(idempotencyKey)
                    .orElseThrow(() -> ex);
        }
    }

    // ── Result types ─────────────────────────────────────────────────────────

    public record TransferResult(
            WalletTransaction senderTx,
            WalletTransaction receiverTx,
            WalletTransaction platformFeeTx
    ) {}
}
