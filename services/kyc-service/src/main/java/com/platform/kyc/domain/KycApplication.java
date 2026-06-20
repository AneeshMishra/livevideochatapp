package com.platform.kyc.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "kyc_applications")
@Getter
@Setter
@NoArgsConstructor
public class KycApplication {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, updatable = false)
    private UUID applicantId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false, length = 20)
    private ApplicantType applicantType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private KycStatus status = KycStatus.PENDING;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private VendorProvider vendorProvider;

    /** Session / case ID from the external KYC vendor. */
    @Column(length = 255)
    private String vendorSessionId;

    /** URL to redirect the applicant to for identity verification. */
    @Column(columnDefinition = "TEXT")
    private String vendorVerificationUrl;

    @Column(columnDefinition = "TEXT")
    private String rejectionReason;

    private Instant verifiedAt;
    private Instant expiresAt;
    private Instant submittedAt;

    @OneToMany(mappedBy = "application", cascade = CascadeType.ALL, fetch = FetchType.LAZY, orphanRemoval = true)
    private List<KycDocument> documents = new ArrayList<>();

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    @PrePersist
    private void prePersist() {
        createdAt = Instant.now();
        updatedAt = Instant.now();
    }

    @PreUpdate
    private void preUpdate() {
        updatedAt = Instant.now();
    }

    public static KycApplication create(UUID applicantId, ApplicantType type, VendorProvider provider) {
        KycApplication app = new KycApplication();
        app.applicantId = applicantId;
        app.applicantType = type;
        app.vendorProvider = provider;
        app.status = KycStatus.PENDING;
        return app;
    }

    public void approve(int validityDays) {
        this.status = KycStatus.APPROVED;
        this.verifiedAt = Instant.now();
        this.expiresAt = verifiedAt.plusSeconds((long) validityDays * 86400);
    }

    public void reject(String reason) {
        this.status = KycStatus.REJECTED;
        this.rejectionReason = reason;
    }

    public void submit() {
        this.status = KycStatus.SUBMITTED;
        this.submittedAt = Instant.now();
    }

    public void markUnderReview() {
        this.status = KycStatus.UNDER_REVIEW;
    }

    public boolean isActive() {
        return status == KycStatus.PENDING
            || status == KycStatus.SUBMITTED
            || status == KycStatus.UNDER_REVIEW
            || status == KycStatus.APPROVED;
    }
}
