package com.platform.payments.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.Map;

/**
 * Verifies webhook signatures from each payment provider.
 * When the provider-specific secret is left blank (dev/test), verification is skipped.
 * In production ALL secrets MUST be set — a blank key disables the safety check.
 */
@Service
@Slf4j
public class WebhookVerificationService {

    @Value("${app.providers.ccbill.digest-key:}")
    private String ccbillDigestKey;

    @Value("${app.providers.epoch.secret-key:}")
    private String epochSecretKey;

    @Value("${app.providers.segpay.secret-key:}")
    private String segpaySecretKey;

    /**
     * CCBill approval/denial postback verification.
     * Digest = MD5(clientAccnum + clientSubacc + subscriptionTypeId +
     *              initialPrice + initialPeriod + currencyCode + digestKey)
     */
    public boolean verifyCCBill(Map<String, String> params) {
        if (ccbillDigestKey.isBlank()) {
            log.warn("CCBill digest key not configured — skipping signature verification (dev mode only)");
            return true;
        }

        String clientAccnum       = params.getOrDefault("clientAccnum", "");
        String clientSubacc       = params.getOrDefault("clientSubacc", "");
        String subscriptionTypeId = params.getOrDefault("subscriptionTypeId", "");
        String initialPrice       = params.getOrDefault("initialPrice", "");
        String initialPeriod      = params.getOrDefault("initialPeriod", "");
        String currencyCode       = params.getOrDefault("currencyCode", "");
        String providedDigest     = params.getOrDefault("digest", "");

        String raw = clientAccnum + clientSubacc + subscriptionTypeId
                + initialPrice + initialPeriod + currencyCode + ccbillDigestKey;
        String expected = md5Hex(raw);

        boolean valid = expected.equalsIgnoreCase(providedDigest);
        if (!valid) {
            log.warn("CCBill digest mismatch — possible replay or misconfiguration");
        }
        return valid;
    }

    /**
     * Epoch postback verification.
     * Hash = MD5(ep_trid + mer_ref + total + secretKey)
     */
    public boolean verifyEpoch(Map<String, String> params) {
        if (epochSecretKey.isBlank()) {
            log.warn("Epoch secret key not configured — skipping signature verification (dev mode only)");
            return true;
        }

        String epTrid        = params.getOrDefault("ep_trid", "");
        String merRef        = params.getOrDefault("mer_ref", "");
        String total         = params.getOrDefault("total", "");
        String providedHash  = params.getOrDefault("hash", "");

        String raw      = epTrid + merRef + total + epochSecretKey;
        String expected = md5Hex(raw);

        boolean valid = expected.equalsIgnoreCase(providedHash);
        if (!valid) {
            log.warn("Epoch hash mismatch — possible replay or misconfiguration");
        }
        return valid;
    }

    /**
     * SegPay postback verification.
     * Signature = Base64(HMAC-SHA256(rawBody, secretKey))
     * Provided in X-Segpay-Signature header.
     */
    public boolean verifySegPay(String rawBody, String signatureHeader) {
        if (segpaySecretKey.isBlank()) {
            log.warn("SegPay secret key not configured — skipping signature verification (dev mode only)");
            return true;
        }
        if (signatureHeader == null || signatureHeader.isBlank()) {
            log.warn("SegPay webhook missing X-Segpay-Signature header");
            return false;
        }

        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(segpaySecretKey.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] computed = mac.doFinal(rawBody.getBytes(StandardCharsets.UTF_8));
            String expected = Base64.getEncoder().encodeToString(computed);

            // Constant-time comparison to prevent timing attacks
            return MessageDigest.isEqual(
                    expected.getBytes(StandardCharsets.UTF_8),
                    signatureHeader.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            log.error("SegPay HMAC computation failed", e);
            return false;
        }
    }

    private String md5Hex(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] digest = md.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(32);
            for (byte b : digest) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("MD5 not available", e);
        }
    }
}
