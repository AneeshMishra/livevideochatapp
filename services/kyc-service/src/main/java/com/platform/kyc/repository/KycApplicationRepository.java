package com.platform.kyc.repository;

import com.platform.kyc.domain.ApplicantType;
import com.platform.kyc.domain.KycApplication;
import com.platform.kyc.domain.KycStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;
import java.util.UUID;

public interface KycApplicationRepository extends JpaRepository<KycApplication, UUID> {

    /** Returns the current active application for an applicant (any non-terminal status). */
    @Query("""
        SELECT a FROM KycApplication a
        WHERE a.applicantId = :applicantId
          AND a.status IN ('PENDING','SUBMITTED','UNDER_REVIEW','APPROVED')
        ORDER BY a.createdAt DESC
        """)
    Optional<KycApplication> findActiveByApplicantId(UUID applicantId);

    Optional<KycApplication> findByVendorSessionId(String vendorSessionId);

    Page<KycApplication> findAllByOrderByCreatedAtDesc(Pageable pageable);

    Page<KycApplication> findByStatusOrderByCreatedAtDesc(KycStatus status, Pageable pageable);

    Page<KycApplication> findByApplicantTypeOrderByCreatedAtDesc(ApplicantType type, Pageable pageable);

    boolean existsByApplicantIdAndStatusIn(UUID applicantId, java.util.List<KycStatus> statuses);
}
