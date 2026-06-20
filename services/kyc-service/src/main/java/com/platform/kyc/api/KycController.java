package com.platform.kyc.api;

import com.platform.kyc.api.dto.*;
import com.platform.kyc.domain.ApplicantType;
import com.platform.kyc.domain.DocumentType;
import com.platform.kyc.domain.KycApplication;
import com.platform.kyc.domain.KycDocument;
import com.platform.kyc.service.KycService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.UUID;

/**
 * KYC endpoints for viewers and broadcasters.
 * All routes require authentication — the applicant ID is taken from the JWT subject.
 */
@RestController
@RequestMapping("/api/v1/kyc")
@RequiredArgsConstructor
@Tag(name = "KYC", description = "Age verification and 18 USC §2257 compliance")
@SecurityRequirement(name = "bearerAuth")
public class KycController {

    private final KycService kycService;

    // ── Viewer / Broadcaster — start an application ───────────────────────────

    @Operation(summary = "Start a KYC application (viewer: age verification; broadcaster: age + 2257)")
    @PostMapping("/applications")
    @ResponseStatus(HttpStatus.CREATED)
    public ApplicationResponse startApplication(
            @AuthenticationPrincipal String userId,
            @Valid @RequestBody StartApplicationRequest request) {

        UUID applicantId = UUID.fromString(userId);
        KycApplication app = kycService.startApplication(applicantId, request.applicantType());
        return ApplicationResponse.from(app);
    }

    @Operation(summary = "Get my current KYC application status")
    @GetMapping("/applications/me")
    public ApplicationResponse getMyApplication(@AuthenticationPrincipal String userId) {
        UUID applicantId = UUID.fromString(userId);
        return ApplicationResponse.from(kycService.getMyApplication(applicantId));
    }

    // ── Document upload ───────────────────────────────────────────────────────

    @Operation(summary = "Upload a KYC document (ID_FRONT, ID_BACK, SELFIE, MODEL_RELEASE_2257, etc.)")
    @PostMapping(value = "/applications/me/documents", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public DocumentResponse uploadDocument(
            @AuthenticationPrincipal String userId,
            @RequestParam("documentType") DocumentType documentType,
            @RequestParam("file") MultipartFile file) throws IOException {

        UUID applicantId = UUID.fromString(userId);
        KycDocument doc = kycService.uploadDocument(applicantId, documentType, file);
        return DocumentResponse.from(doc);
    }

    // ── Submit for review ─────────────────────────────────────────────────────

    @Operation(summary = "Submit the application for KYC review — returns verification URL for redirect")
    @PutMapping("/applications/me/submit")
    public ApplicationResponse submitForReview(@AuthenticationPrincipal String userId) {
        UUID applicantId = UUID.fromString(userId);
        return ApplicationResponse.from(kycService.submitForReview(applicantId));
    }

    // ── Broadcaster 2257 record submission ────────────────────────────────────

    @Operation(summary = "Submit 2257 performer record (BROADCASTER only, after KYC is APPROVED)")
    @PostMapping("/applications/me/record-2257")
    @ResponseStatus(HttpStatus.CREATED)
    public ApplicationResponse submit2257Record(
            @AuthenticationPrincipal String userId,
            @Valid @RequestBody Submit2257Request request) {

        UUID applicantId = UUID.fromString(userId);
        // Get the approved application to get its ID
        KycApplication app = kycService.getMyApplication(applicantId);
        kycService.create2257Record(app.getId(), request);
        return ApplicationResponse.from(app);
    }

    // ── Broadcaster — check another broadcaster's KYC (public — status only) ──

    @Operation(summary = "Check a broadcaster's KYC verification status (status field only)")
    @GetMapping("/broadcaster-status/{broadcasterId}")
    public BroadcasterKycStatusResponse getBroadcasterStatus(@PathVariable UUID broadcasterId) {
        KycApplication app = kycService.getMyApplication(broadcasterId);
        return new BroadcasterKycStatusResponse(broadcasterId, app.getStatus().name(), app.getVerifiedAt());
    }

    public record BroadcasterKycStatusResponse(UUID broadcasterId, String kycStatus,
                                                java.time.Instant verifiedAt) {}
}
