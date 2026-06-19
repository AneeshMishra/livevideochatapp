package com.platform.tipping.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.platform.tipping.exception.InsufficientTokensException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.UUID;

/**
 * HTTP client for wallet-service's internal transfer API.
 * Authenticates using a service-account JWT with ADMIN role.
 * The request DTO field names mirror WalletAdminController's TransferRequest.
 */
@Component
@Slf4j
public class WalletClient {

    private final RestClient restClient;
    private final ServiceAccountTokenProvider tokenProvider;

    public WalletClient(
            ServiceAccountTokenProvider tokenProvider,
            @Value("${app.wallet.service-url:http://wallet-service:8083}") String walletServiceUrl,
            @Value("${app.wallet.connect-timeout-ms:3000}") long connectTimeoutMs,
            @Value("${app.wallet.read-timeout-ms:5000}") long readTimeoutMs) {

        this.tokenProvider = tokenProvider;
        this.restClient = RestClient.builder()
                .baseUrl(walletServiceUrl)
                .requestInterceptor((request, body, execution) -> {
                    request.getHeaders().setBearerAuth(tokenProvider.getToken());
                    request.getHeaders().setContentType(MediaType.APPLICATION_JSON);
                    return execution.execute(request, body);
                })
                .build();
    }

    /**
     * Transfers grossAmount tokens from sender to recipient.
     * Platform fee (configured in wallet-service) is deducted automatically.
     *
     * @throws InsufficientTokensException when the sender's balance is too low
     * @throws WalletServiceException      for any other wallet-service error
     */
    public TransferResult transfer(UUID fromUserId, UUID toUserId, long grossAmount,
                                   UUID referenceId, String idempotencyKey) {
        TransferRequest body = new TransferRequest(
                fromUserId, toUserId, grossAmount,
                "TIP_SENT", "TIP_RECEIVED",
                referenceId, "tip", idempotencyKey);

        try {
            return restClient.post()
                    .uri("/api/v1/wallet/internal/transfer")
                    .body(body)
                    .retrieve()
                    .body(TransferResult.class);
        } catch (HttpClientErrorException ex) {
            if (ex.getStatusCode() == HttpStatus.UNPROCESSABLE_ENTITY) {
                // Wallet returns 422 for InsufficientFundsException
                log.info("Insufficient tokens for transfer fromUserId={} amount={}", fromUserId, grossAmount);
                throw new InsufficientTokensException(fromUserId);
            }
            log.error("Wallet service error status={} for transfer fromUserId={}", ex.getStatusCode(), fromUserId);
            throw new WalletServiceException("Wallet service returned: " + ex.getStatusCode(), ex);
        } catch (Exception ex) {
            log.error("Wallet service unreachable for transfer fromUserId={}", fromUserId, ex);
            throw new WalletServiceException("Wallet service unavailable", ex);
        }
    }

    // ── DTO records (mirror wallet-service TransferRequest / response) ─────────

    record TransferRequest(
            UUID fromUserId,
            UUID toUserId,
            long grossAmount,
            String senderTransactionType,    // "TIP_SENT"
            String receiverTransactionType,  // "TIP_RECEIVED"
            UUID referenceId,
            String referenceType,
            String idempotencyKey
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record TransferResult(
            TxSummary senderTx,
            TxSummary receiverTx,
            TxSummary platformFeeTx
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record TxSummary(UUID id, long amount, long balanceAfter) {}

    public static class WalletServiceException extends RuntimeException {
        public WalletServiceException(String msg, Throwable cause) {
            super(msg, cause);
        }
    }
}
