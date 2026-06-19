package com.platform.wallet.api;

import com.platform.wallet.api.dto.CreditRequest;
import com.platform.wallet.api.dto.DebitRequest;
import com.platform.wallet.api.dto.TransactionResponse;
import com.platform.wallet.api.dto.TransferRequest;
import com.platform.wallet.service.WalletService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * Internal service-to-service API.
 * Requires ADMIN role — callers must include a valid JWT with role=ADMIN.
 * In production this endpoint is not exposed to the public internet; it is
 * only reachable within the service mesh.
 */
@RestController
@RequestMapping("/api/v1/wallet/internal")
@RequiredArgsConstructor
@Tag(name = "Wallet (Internal)", description = "Service-to-service credit, debit, and transfer operations")
@SecurityRequirement(name = "bearerAuth")
@PreAuthorize("hasRole('ADMIN')")
public class WalletAdminController {

    private final WalletService walletService;

    @Operation(summary = "Credit tokens into a wallet (e.g. after token purchase)")
    @PostMapping("/credit")
    @ResponseStatus(HttpStatus.CREATED)
    public TransactionResponse credit(@Valid @RequestBody CreditRequest req) {
        return TransactionResponse.from(walletService.credit(
                req.userId(), req.amount(), req.transactionType(),
                req.referenceId(), req.referenceType(),
                req.idempotencyKey(), req.description()));
    }

    @Operation(summary = "Debit tokens from a wallet (e.g. content purchase)")
    @PostMapping("/debit")
    @ResponseStatus(HttpStatus.CREATED)
    public TransactionResponse debit(@Valid @RequestBody DebitRequest req) {
        return TransactionResponse.from(walletService.debit(
                req.userId(), req.amount(), req.transactionType(),
                req.referenceId(), req.referenceType(),
                req.idempotencyKey(), req.description()));
    }

    @Operation(summary = "Transfer tokens between wallets (tip, private show, subscription)")
    @PostMapping("/transfer")
    @ResponseStatus(HttpStatus.CREATED)
    public TransferResultResponse transfer(@Valid @RequestBody TransferRequest req) {
        WalletService.TransferResult result = walletService.transfer(
                req.fromUserId(), req.toUserId(), req.grossAmount(),
                req.senderTransactionType(), req.receiverTransactionType(),
                req.referenceId(), req.referenceType(), req.idempotencyKey());

        return new TransferResultResponse(
                TransactionResponse.from(result.senderTx()),
                result.receiverTx() != null ? TransactionResponse.from(result.receiverTx()) : null,
                result.platformFeeTx() != null ? TransactionResponse.from(result.platformFeeTx()) : null);
    }

    record TransferResultResponse(
            TransactionResponse senderTx,
            TransactionResponse receiverTx,
            TransactionResponse platformFeeTx
    ) {}
}
