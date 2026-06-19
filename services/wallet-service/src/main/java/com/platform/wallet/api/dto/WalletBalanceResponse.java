package com.platform.wallet.api.dto;

import com.platform.wallet.domain.Wallet;

import java.time.Instant;
import java.util.UUID;

public record WalletBalanceResponse(
        UUID userId,
        long balance,
        long version,
        Instant updatedAt
) {
    public static WalletBalanceResponse from(Wallet w) {
        return new WalletBalanceResponse(
                w.getUserId(), w.getBalance(), w.getVersion(), w.getUpdatedAt());
    }
}
