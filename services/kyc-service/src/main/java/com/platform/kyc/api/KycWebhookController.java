package com.platform.kyc.api;

import com.platform.kyc.service.KycService;
import com.platform.kyc.vendor.KycVendor;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Receives webhook callbacks from KYC vendors when a verification decision is made.
 * These endpoints are PUBLIC (no JWT) because they are called by external vendors,
 * but each vendor's payload is HMAC-signed and the signature is verified in KycVendor.
 */
@RestController
@RequestMapping("/api/v1/kyc/webhooks")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "KYC Webhooks", description = "Vendor callback endpoints — HMAC signature verified")
public class KycWebhookController {

    private final KycVendor kycVendor;
    private final KycService kycService;

    @Operation(summary = "Veriff decision webhook")
    @PostMapping("/veriff")
    public ResponseEntity<Void> veriffWebhook(
            @RequestBody byte[] payload,
            @RequestHeader(value = "X-HMAC-SIGNATURE", required = false) String signature) {

        return handleWebhook(payload, signature, "VERIFF");
    }

    @Operation(summary = "Persona decision webhook")
    @PostMapping("/persona")
    public ResponseEntity<Void> personaWebhook(
            @RequestBody byte[] payload,
            @RequestHeader(value = "Persona-Signature", required = false) String signature) {

        return handleWebhook(payload, signature, "PERSONA");
    }

    @Operation(summary = "Yoti decision webhook")
    @PostMapping("/yoti")
    public ResponseEntity<Void> yotiWebhook(
            @RequestBody byte[] payload,
            @RequestHeader(value = "X-Yoti-Auth-Digest", required = false) String signature) {

        return handleWebhook(payload, signature, "YOTI");
    }

    private ResponseEntity<Void> handleWebhook(byte[] payload, String signature, String vendor) {
        try {
            Map<String, String> result = kycVendor.parseWebhook(payload, signature);
            String sessionId = result.get("sessionId");
            boolean approved = Boolean.parseBoolean(result.get("approved"));
            String reason = result.getOrDefault("reason", "");

            if (sessionId == null || sessionId.isBlank()) {
                log.warn("{} webhook missing sessionId — skipping", vendor);
                return ResponseEntity.badRequest().build();
            }

            kycService.handleWebhookResult(sessionId, approved, reason);
            return ResponseEntity.ok().build();

        } catch (SecurityException ex) {
            log.warn("{} webhook signature invalid: {}", vendor, ex.getMessage());
            return ResponseEntity.status(401).build();
        } catch (Exception ex) {
            log.error("{} webhook processing error: {}", vendor, ex.getMessage(), ex);
            return ResponseEntity.internalServerError().build();
        }
    }
}
