package com.platform.kyc.api;

import com.platform.kyc.api.dto.*;
import com.platform.kyc.domain.ApplicantType;
import com.platform.kyc.domain.KycApplication;
import com.platform.kyc.domain.KycStatus;
import com.platform.kyc.domain.Record2257;
import com.platform.kyc.service.EncryptionService;
import com.platform.kyc.service.KycService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/kyc/admin")
@RequiredArgsConstructor
@Tag(name = "KYC Admin", description = "Administrative KYC review and 2257 record access")
@SecurityRequirement(name = "bearerAuth")
@PreAuthorize("hasRole('ADMIN')")
public class KycAdminController {

    private final KycService kycService;
    private final EncryptionService encryptionService;

    @Operation(summary = "List all KYC applications with optional filters")
    @GetMapping("/applications")
    public Page<ApplicationResponse> listApplications(
            @RequestParam(required = false) KycStatus status,
            @RequestParam(required = false) ApplicantType applicantType,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        Page<KycApplication> results = kycService.listApplications(
            status, applicantType, PageRequest.of(page, Math.min(size, 100)));
        return results.map(ApplicationResponse::from);
    }

    @Operation(summary = "Get a specific KYC application by ID")
    @GetMapping("/applications/{id}")
    public ApplicationResponse getApplication(@PathVariable UUID id) {
        return ApplicationResponse.from(kycService.getApplicationById(id));
    }

    @Operation(summary = "Manually approve a KYC application (overrides vendor decision)")
    @PutMapping("/applications/{id}/approve")
    public ApplicationResponse approve(@PathVariable UUID id) {
        return ApplicationResponse.from(kycService.adminApprove(id));
    }

    @Operation(summary = "Manually reject a KYC application with a reason")
    @PutMapping("/applications/{id}/reject")
    public ApplicationResponse reject(
            @PathVariable UUID id,
            @Valid @RequestBody AdminDecisionRequest request) {

        return ApplicationResponse.from(kycService.adminReject(id, request));
    }

    // ── 2257 Record access ────────────────────────────────────────────────────
    // CRITICAL: Every access to a 2257 record is audit-logged in the service layer.

    @Operation(summary = "Read the 2257 compliance record for a broadcaster (ADMIN only — audited)")
    @GetMapping("/records/2257/{broadcasterId}")
    public Record2257Response get2257Record(
            @PathVariable UUID broadcasterId,
            @AuthenticationPrincipal String adminUserId) {

        Record2257 record = kycService.get2257Record(broadcasterId, UUID.fromString(adminUserId));
        return Record2257Response.from(record, encryptionService);
    }

    @Operation(summary = "Generate a short-lived signed URL to view a KYC document (ADMIN only)")
    @GetMapping("/applications/{id}/documents/{documentId}/url")
    public ResponseEntity<DocumentUrlResponse> getDocumentUrl(
            @PathVariable UUID id,
            @PathVariable UUID documentId) {

        // Retrieve the application and find the document's encrypted ref
        KycApplication app = kycService.getApplicationById(id);
        return app.getDocuments().stream()
            .filter(d -> d.getId().equals(documentId))
            .findFirst()
            .map(doc -> {
                String url = kycService.generateDocumentUrl(doc.getS3RefEncrypted());
                return ResponseEntity.ok(new DocumentUrlResponse(documentId, url, 300));
            })
            .orElse(ResponseEntity.notFound().build());
    }

    public record DocumentUrlResponse(UUID documentId, String url, int expiresInSeconds) {}
}
