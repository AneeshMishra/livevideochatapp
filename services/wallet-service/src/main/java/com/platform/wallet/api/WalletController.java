package com.platform.wallet.api;

import com.platform.wallet.api.dto.PageResponse;
import com.platform.wallet.api.dto.TransactionResponse;
import com.platform.wallet.api.dto.WalletBalanceResponse;
import com.platform.wallet.service.WalletService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/wallet")
@RequiredArgsConstructor
@Tag(name = "Wallet", description = "Token balance and transaction history for the authenticated user")
@SecurityRequirement(name = "bearerAuth")
public class WalletController {

    private final WalletService walletService;

    @Operation(summary = "Get own token balance")
    @GetMapping("/me")
    public WalletBalanceResponse getMyBalance(@AuthenticationPrincipal String userId) {
        return WalletBalanceResponse.from(walletService.getWallet(UUID.fromString(userId)));
    }

    @Operation(summary = "Get own transaction history (paginated, newest first)")
    @GetMapping("/me/transactions")
    public PageResponse<TransactionResponse> getMyTransactions(
            @AuthenticationPrincipal String userId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return PageResponse.from(
                walletService.getTransactions(
                        UUID.fromString(userId), PageRequest.of(page, Math.min(size, 100))),
                TransactionResponse::from);
    }

    @Operation(summary = "Get any wallet balance by userId — ADMIN only")
    @GetMapping("/{userId}/balance")
    public WalletBalanceResponse getBalance(@PathVariable UUID userId) {
        return WalletBalanceResponse.from(walletService.getWallet(userId));
    }
}
