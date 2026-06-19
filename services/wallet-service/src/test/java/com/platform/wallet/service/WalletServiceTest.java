package com.platform.wallet.service;

import com.platform.wallet.domain.TransactionType;
import com.platform.wallet.domain.Wallet;
import com.platform.wallet.domain.WalletTransaction;
import com.platform.wallet.event.WalletEventPublisher;
import com.platform.wallet.exception.InsufficientFundsException;
import com.platform.wallet.exception.WalletNotFoundException;
import com.platform.wallet.repository.WalletRepository;
import com.platform.wallet.repository.WalletTransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WalletServiceTest {

    @Mock WalletRepository walletRepo;
    @Mock WalletTransactionRepository txRepo;
    @Mock WalletEventPublisher eventPublisher;

    @InjectMocks WalletService walletService;

    private final UUID platformWalletId = UUID.fromString("00000000-0000-0000-0000-000000000001");

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(walletService, "platformFeeBps", 500);
        ReflectionTestUtils.setField(walletService, "platformWalletId", platformWalletId);
    }

    // ── createWallet ─────────────────────────────────────────────────────────

    @Test
    void createWallet_newUser_savesWallet() {
        UUID userId = UUID.randomUUID();
        when(walletRepo.findById(userId)).thenReturn(Optional.empty());

        Wallet saved = Wallet.forUser(userId);
        when(walletRepo.save(any())).thenReturn(saved);

        Wallet result = walletService.createWallet(userId);

        assertThat(result.getUserId()).isEqualTo(userId);
        assertThat(result.getBalance()).isZero();
        verify(eventPublisher).publish(any());
    }

    @Test
    void createWallet_existingUser_isIdempotent() {
        UUID userId = UUID.randomUUID();
        Wallet existing = Wallet.forUser(userId);
        when(walletRepo.findById(userId)).thenReturn(Optional.of(existing));

        Wallet result = walletService.createWallet(userId);

        assertThat(result).isSameAs(existing);
        verify(walletRepo, never()).save(any());
        verify(eventPublisher, never()).publish(any());
    }

    // ── credit ────────────────────────────────────────────────────────────────

    @Test
    void credit_success_updatesBalanceAndRecordsTx() {
        UUID userId = UUID.randomUUID();
        Wallet wallet = Wallet.forUser(userId);
        String idempotencyKey = "pay:001";

        when(txRepo.findByIdempotencyKey(idempotencyKey)).thenReturn(Optional.empty());
        when(walletRepo.findByUserIdForUpdate(userId)).thenReturn(Optional.of(wallet));
        when(walletRepo.save(wallet)).thenReturn(wallet);

        WalletTransaction tx = new WalletTransaction();
        tx.setAmount(100L);
        tx.setBalanceAfter(100L);
        when(txRepo.save(any())).thenReturn(tx);

        walletService.credit(userId, 100L, TransactionType.PURCHASE,
                null, "payment", idempotencyKey, "Top-up");

        assertThat(wallet.getBalance()).isEqualTo(100L);
        verify(walletRepo).save(wallet);
        verify(txRepo).save(any());
        verify(eventPublisher).publish(any());
    }

    @Test
    void credit_idempotencyKeyExists_returnsExistingTx() {
        UUID userId = UUID.randomUUID();
        WalletTransaction existing = new WalletTransaction();
        String key = "pay:001";

        when(txRepo.findByIdempotencyKey(key)).thenReturn(Optional.of(existing));

        WalletTransaction result = walletService.credit(userId, 100L, TransactionType.PURCHASE,
                null, "payment", key, "Top-up");

        assertThat(result).isSameAs(existing);
        verify(walletRepo, never()).findByUserIdForUpdate(any());
    }

    // ── debit ─────────────────────────────────────────────────────────────────

    @Test
    void debit_insufficientFunds_throws() {
        UUID userId = UUID.randomUUID();
        Wallet wallet = Wallet.forUser(userId);
        wallet.credit(50L);  // balance = 50
        String key = "tip:001";

        when(txRepo.findByIdempotencyKey(key)).thenReturn(Optional.empty());
        when(walletRepo.findByUserIdForUpdate(userId)).thenReturn(Optional.of(wallet));

        assertThatThrownBy(() ->
                walletService.debit(userId, 100L, TransactionType.TIP_SENT,
                        null, "tip", key, "Tip"))
                .isInstanceOf(InsufficientFundsException.class);
    }

    @Test
    void debit_walletNotFound_throws() {
        UUID userId = UUID.randomUUID();
        String key = "tip:002";

        when(txRepo.findByIdempotencyKey(key)).thenReturn(Optional.empty());
        when(walletRepo.findByUserIdForUpdate(userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() ->
                walletService.debit(userId, 10L, TransactionType.TIP_SENT,
                        null, "tip", key, "Tip"))
                .isInstanceOf(WalletNotFoundException.class);
    }

    // ── transfer ──────────────────────────────────────────────────────────────

    @Test
    void transfer_deductsGrossAndCreditNetWithFee() {
        UUID viewerId      = UUID.randomUUID();
        UUID broadcasterId = UUID.randomUUID();
        UUID referenceId   = UUID.randomUUID();
        String key         = "tip:abc";

        Wallet viewerWallet      = Wallet.forUser(viewerId);
        viewerWallet.credit(1000L);  // viewer has 1000 tokens

        Wallet broadcasterWallet = Wallet.forUser(broadcasterId);
        Wallet platformWallet    = Wallet.forUser(platformWalletId);

        when(txRepo.findByIdempotencyKey(key + ":debit")).thenReturn(Optional.empty());

        // Consistent UUID locking order (UUID.compareTo determines order)
        boolean viewerFirst = viewerId.compareTo(broadcasterId) <= 0;
        if (viewerFirst) {
            when(walletRepo.findByUserIdForUpdate(viewerId)).thenReturn(Optional.of(viewerWallet));
            when(walletRepo.findByUserIdForUpdate(broadcasterId)).thenReturn(Optional.of(broadcasterWallet));
        } else {
            when(walletRepo.findByUserIdForUpdate(broadcasterId)).thenReturn(Optional.of(broadcasterWallet));
            when(walletRepo.findByUserIdForUpdate(viewerId)).thenReturn(Optional.of(viewerWallet));
        }
        when(walletRepo.findByUserIdForUpdate(platformWalletId)).thenReturn(Optional.of(platformWallet));
        when(walletRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(txRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        walletService.transfer(
                viewerId, broadcasterId, 100L,
                TransactionType.TIP_SENT, TransactionType.TIP_RECEIVED,
                referenceId, "tip", key);

        // Sender pays full 100
        assertThat(viewerWallet.getBalance()).isEqualTo(900L);
        // Fee = 5% of 100 = 5 tokens
        assertThat(broadcasterWallet.getBalance()).isEqualTo(95L);
        assertThat(platformWallet.getBalance()).isEqualTo(5L);

        verify(eventPublisher).publish(any());
    }
}
