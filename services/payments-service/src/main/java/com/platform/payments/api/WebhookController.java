package com.platform.payments.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.platform.payments.domain.PaymentProvider;
import com.platform.payments.exception.WebhookVerificationException;
import com.platform.payments.service.PaymentService;
import com.platform.payments.service.WebhookVerificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

/**
 * Receives approval/denial postbacks from payment providers.
 * These endpoints are PUBLIC (no JWT) — authentication is via provider-specific
 * signature verification inside WebhookVerificationService.
 *
 * All raw payloads are stored on the PaymentOrder for audit.
 */
@RestController
@RequestMapping("/api/v1/payments/webhooks")
@RequiredArgsConstructor
@Slf4j
public class WebhookController {

    private final PaymentService paymentService;
    private final WebhookVerificationService verificationService;
    private final ObjectMapper objectMapper;

    /**
     * CCBill approval / denial postback (form-encoded POST).
     * CCBill sends a background POST to our server for every transaction outcome.
     *
     * Key params:
     *   subscriptionId  — CCBill transaction ID
     *   X-orderId       — our order UUID (passed in the initial form URL)
     *   responseCode    — "1" = approval, "0" = denial
     *   initialPrice, initialPeriod, currencyCode, digest — for signature verification
     */
    @PostMapping("/ccbill")
    public ResponseEntity<Void> handleCCBill(@RequestParam Map<String, String> params) {
        log.debug("CCBill webhook params={}", params.keySet());

        if (!verificationService.verifyCCBill(params)) {
            throw new WebhookVerificationException("CCBill digest verification failed");
        }

        String transactionId = params.get("subscriptionId");
        String rawOrderId    = params.get("X-orderId");
        String responseCode  = params.getOrDefault("responseCode", "0");
        UUID   orderId       = parseUuid(rawOrderId);

        if ("1".equals(responseCode)) {
            paymentService.confirmPayment(PaymentProvider.CCBILL, transactionId, orderId, params.toString());
        } else {
            String reason = params.getOrDefault("declineCode", "CCBill decline code: " + responseCode);
            paymentService.failOrder(PaymentProvider.CCBILL, transactionId, orderId, reason, params.toString());
        }

        // CCBill expects HTTP 200 with no body; non-200 triggers a retry
        return ResponseEntity.ok().build();
    }

    /**
     * Epoch approval / denial postback (form-encoded POST).
     *
     * Key params:
     *   ep_trid   — Epoch transaction ID
     *   mer_ref   — our order UUID (merchant reference)
     *   status    — "OK" = approval, "ERROR" = denial
     *   hash      — MD5 for signature verification
     */
    @PostMapping("/epoch")
    public ResponseEntity<Void> handleEpoch(@RequestParam Map<String, String> params) {
        log.debug("Epoch webhook params={}", params.keySet());

        if (!verificationService.verifyEpoch(params)) {
            throw new WebhookVerificationException("Epoch hash verification failed");
        }

        String transactionId = params.get("ep_trid");
        String rawOrderId    = params.get("mer_ref");
        String status        = params.getOrDefault("status", "ERROR");
        UUID   orderId       = parseUuid(rawOrderId);

        if ("OK".equalsIgnoreCase(status)) {
            paymentService.confirmPayment(PaymentProvider.EPOCH, transactionId, orderId, params.toString());
        } else {
            String reason = params.getOrDefault("error", "Epoch declined");
            paymentService.failOrder(PaymentProvider.EPOCH, transactionId, orderId, reason, params.toString());
        }

        return ResponseEntity.ok().build();
    }

    /**
     * SegPay approval / chargeback postback (JSON POST).
     * Signature verified via HMAC-SHA256 in X-Segpay-Signature header.
     *
     * Key fields: ppref, order_id, status ("approved"/"declined"), total
     */
    @PostMapping("/segpay")
    public ResponseEntity<Void> handleSegPay(
            @RequestBody String rawBody,
            @RequestHeader(value = "X-Segpay-Signature", required = false) String signature) {

        log.debug("SegPay webhook received");

        if (!verificationService.verifySegPay(rawBody, signature)) {
            throw new WebhookVerificationException("SegPay HMAC verification failed");
        }

        try {
            JsonNode node         = objectMapper.readTree(rawBody);
            String transactionId  = node.path("ppref").asText(null);
            String rawOrderId     = node.path("order_id").asText(null);
            String status         = node.path("status").asText("declined");
            String type           = node.path("type").asText("purchase");
            UUID   orderId        = parseUuid(rawOrderId);

            if ("chargeback".equalsIgnoreCase(type)) {
                String chargebackId = node.path("chargeback_id").asText(transactionId);
                BigDecimal amount   = new BigDecimal(node.path("total").asText("0"));
                paymentService.processChargeback(orderId, chargebackId, amount, "USD",
                        node.path("reason").asText("Chargeback"));
            } else if ("approved".equalsIgnoreCase(status)) {
                paymentService.confirmPayment(PaymentProvider.SEGPAY, transactionId, orderId, rawBody);
            } else {
                paymentService.failOrder(PaymentProvider.SEGPAY, transactionId, orderId,
                        "SegPay status: " + status, rawBody);
            }
        } catch (Exception e) {
            log.error("Failed to parse SegPay webhook payload", e);
            // Return 200 to prevent infinite retries from SegPay; log for manual review
        }

        return ResponseEntity.ok().build();
    }

    private UUID parseUuid(String raw) {
        if (raw == null || raw.isBlank()) return null;
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException e) {
            log.warn("Provider sent non-UUID order reference: {}", raw);
            return null;
        }
    }
}
