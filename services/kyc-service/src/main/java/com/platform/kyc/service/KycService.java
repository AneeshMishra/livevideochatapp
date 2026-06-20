package com.platform.kyc.service;

import com.platform.kyc.api.dto.AdminDecisionRequest;
import com.platform.kyc.api.dto.Submit2257Request;
import com.platform.kyc.domain.*;
import com.platform.kyc.event.KycEvent;
import com.platform.kyc.event.KycEventPublisher;
import com.platform.kyc.exception.DuplicateApplicationException;
import com.platform.kyc.exception.KycApplicationNotFoundException;
import com.platform.kyc.repository.KycApplicationRepository;
import com.platform.kyc.repository.KycDocumentRepository;
import com.platform.kyc.repository.Record2257Repository;
import com.platform.kyc.vendor.KycVendor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class KycService {

    private final KycApplicationRepository applicationRepo;
    private final KycDocumentRepository documentRepo;
    private final Record2257Repository record2257Repo;
    private final KycVendor kycVendor;
    private final StorageService storageService;
    private final EncryptionService encryptionService;
    private final KycEventPublisher eventPublisher;

    @Value("${app.kyc.validity-days:730}")
    private int validityDays;

    @Value("${app.kyc.provider:MOCK}")
    private String provider;

    // ── Application lifecycle ─────────────────────────────────────────────────

    @Transactional
    public KycApplication startApplication(UUID applicantId, ApplicantType type) {
        // Enforce one active application per applicant at a time.
        List<KycStatus> activeStatuses = List.of(
            KycStatus.PENDING, KycStatus.SUBMITTED, KycStatus.UNDER_REVIEW, KycStatus.APPROVED);
        if (applicationRepo.existsByApplicantIdAndStatusIn(applicantId, activeStatuses)) {
            throw new DuplicateApplicationException(
                "An active KYC application already exists for this applicant. " +
                "Use GET /api/v1/kyc/applications/me to check current status.");
        }

        VendorProvider vendorProvider = VendorProvider.valueOf(provider.toUpperCase());
        KycApplication application = KycApplication.create(applicantId, type, vendorProvider);

        // Create a session with the vendor and capture the redirect URL.
        var session = kycVendor.createSession(applicantId, type.name());
        application.setVendorSessionId(session.sessionId());
        application.setVendorVerificationUrl(session.verificationUrl());

        KycApplication saved = applicationRepo.save(application);
        log.info("KYC application {} created for applicant {} ({})", saved.getId(), applicantId, type);
        return saved;
    }

    @Transactional(readOnly = true)
    public KycApplication getMyApplication(UUID applicantId) {
        return applicationRepo.findActiveByApplicantId(applicantId)
            .orElseThrow(() -> new KycApplicationNotFoundException(
                "No active KYC application found. Please start one first."));
    }

    @Transactional(readOnly = true)
    public KycApplication getApplicationById(UUID applicationId) {
        return applicationRepo.findById(applicationId)
            .orElseThrow(() -> new KycApplicationNotFoundException(applicationId));
    }

    // ── Document upload ───────────────────────────────────────────────────────

    @Transactional
    public KycDocument uploadDocument(UUID applicantId, DocumentType docType, MultipartFile file) throws IOException {
        KycApplication application = applicationRepo.findActiveByApplicantId(applicantId)
            .orElseThrow(() -> new KycApplicationNotFoundException(
                "No active KYC application found. Please start one first."));

        if (application.getStatus() != KycStatus.PENDING) {
            throw new IllegalStateException(
                "Documents can only be uploaded while the application is in PENDING status.");
        }

        String encryptedRef = storageService.upload(applicantId, docType.name(), file);
        KycDocument document = KycDocument.create(
            application, docType, encryptedRef,
            file.getContentType(), file.getSize());

        KycDocument saved = documentRepo.save(document);
        log.info("Document {} uploaded for application {}", docType, application.getId());
        return saved;
    }

    // ── Submit for review ─────────────────────────────────────────────────────

    @Transactional
    public KycApplication submitForReview(UUID applicantId) {
        KycApplication application = applicationRepo.findActiveByApplicantId(applicantId)
            .orElseThrow(() -> new KycApplicationNotFoundException(
                "No active KYC application found."));

        if (application.getStatus() != KycStatus.PENDING) {
            throw new IllegalStateException(
                "Application cannot be submitted from status: " + application.getStatus());
        }

        application.submit();

        // Tell the vendor. MOCK returns true (immediately resolved); real vendors return false.
        boolean immediatelyResolved = kycVendor.submitForReview(application.getVendorSessionId());

        if (immediatelyResolved) {
            // MOCK auto-approve path
            application.approve(validityDays);
            applicationRepo.save(application);
            eventPublisher.publish(new KycEvent.KycApproved(
                applicantId, application.getApplicantType().name(),
                application.getVerifiedAt(), application.getExpiresAt()));
            log.info("[MOCK] Auto-approved KYC application {} for applicant {}", application.getId(), applicantId);
        } else {
            application.markUnderReview();
            applicationRepo.save(application);
            eventPublisher.publish(new KycEvent.KycSubmitted(
                applicantId, application.getApplicantType().name(),
                application.getVendorProvider().name(), application.getSubmittedAt()));
        }

        return application;
    }

    // ── Webhook resolution (called by webhook controllers) ────────────────────

    @Transactional
    public void handleWebhookResult(String vendorSessionId, boolean approved, String reason) {
        KycApplication application = applicationRepo.findByVendorSessionId(vendorSessionId)
            .orElseThrow(() -> new KycApplicationNotFoundException(
                "No application found for vendor session: " + vendorSessionId));

        if (approved) {
            application.approve(validityDays);
            applicationRepo.save(application);
            eventPublisher.publish(new KycEvent.KycApproved(
                application.getApplicantId(),
                application.getApplicantType().name(),
                application.getVerifiedAt(),
                application.getExpiresAt()));
            log.info("KYC approved via webhook for application {}", application.getId());
        } else {
            application.reject(reason);
            applicationRepo.save(application);
            eventPublisher.publish(new KycEvent.KycRejected(
                application.getApplicantId(),
                application.getApplicantType().name(),
                reason,
                Instant.now()));
            log.warn("KYC rejected via webhook for application {}: {}", application.getId(), reason);
        }
    }

    // ── Admin operations ──────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public Page<KycApplication> listApplications(KycStatus status, ApplicantType type, Pageable pageable) {
        if (status != null) return applicationRepo.findByStatusOrderByCreatedAtDesc(status, pageable);
        if (type != null)   return applicationRepo.findByApplicantTypeOrderByCreatedAtDesc(type, pageable);
        return applicationRepo.findAllByOrderByCreatedAtDesc(pageable);
    }

    @Transactional
    public KycApplication adminApprove(UUID applicationId) {
        KycApplication application = applicationRepo.findById(applicationId)
            .orElseThrow(() -> new KycApplicationNotFoundException(applicationId));

        application.approve(validityDays);
        applicationRepo.save(application);
        eventPublisher.publish(new KycEvent.KycApproved(
            application.getApplicantId(),
            application.getApplicantType().name(),
            application.getVerifiedAt(),
            application.getExpiresAt()));
        log.info("ADMIN manually approved KYC application {}", applicationId);
        return application;
    }

    @Transactional
    public KycApplication adminReject(UUID applicationId, AdminDecisionRequest request) {
        KycApplication application = applicationRepo.findById(applicationId)
            .orElseThrow(() -> new KycApplicationNotFoundException(applicationId));

        application.reject(request.reason());
        applicationRepo.save(application);
        eventPublisher.publish(new KycEvent.KycRejected(
            application.getApplicantId(),
            application.getApplicantType().name(),
            request.reason(),
            Instant.now()));
        log.warn("ADMIN rejected KYC application {}: {}", applicationId, request.reason());
        return application;
    }

    // ── 2257 Record management ────────────────────────────────────────────────

    @Transactional
    public Record2257 create2257Record(UUID applicationId, Submit2257Request request) {
        KycApplication application = applicationRepo.findById(applicationId)
            .orElseThrow(() -> new KycApplicationNotFoundException(applicationId));

        if (application.getApplicantType() != ApplicantType.BROADCASTER) {
            throw new IllegalArgumentException("2257 records are only for BROADCASTER applicants");
        }
        if (application.getStatus() != KycStatus.APPROVED) {
            throw new IllegalStateException("Cannot create 2257 record for unapproved application");
        }
        if (record2257Repo.existsByBroadcasterId(application.getApplicantId())) {
            log.info("Replacing existing 2257 record for broadcaster {}", application.getApplicantId());
            record2257Repo.findByBroadcasterId(application.getApplicantId())
                .ifPresent(r -> record2257Repo.deleteById(r.getId()));
        }

        Record2257 record = Record2257.create(
            application.getApplicantId(),
            applicationId,
            encryptionService.encrypt(request.legalName()),
            encryptionService.encrypt(request.dateOfBirth()),
            encryptionService.encrypt(request.address()),
            request.documentTypeCode(),
            encryptionService.encrypt(request.documentNumber()),
            request.issuingCountry(),
            application.getVerifiedAt()
        );

        Record2257 saved = record2257Repo.save(record);
        log.info("2257 record created for broadcaster {}", application.getApplicantId());
        return saved;
    }

    @Transactional(readOnly = true)
    public Record2257 get2257Record(UUID broadcasterId, UUID adminUserId) {
        Record2257 record = record2257Repo.findByBroadcasterId(broadcasterId)
            .orElseThrow(() -> new KycApplicationNotFoundException(
                "No 2257 record found for broadcaster " + broadcasterId));
        log.info("2257 RECORD ACCESS — broadcaster={} accessedBy={}", broadcasterId, adminUserId);
        return record;
    }

    public String generateDocumentUrl(String encryptedRef) {
        return storageService.generatePresignedUrl(encryptedRef);
    }
}
