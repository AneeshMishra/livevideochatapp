package com.platform.kyc.repository;

import com.platform.kyc.domain.KycDocument;
import com.platform.kyc.domain.DocumentType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface KycDocumentRepository extends JpaRepository<KycDocument, UUID> {

    List<KycDocument> findByApplicationId(UUID applicationId);

    boolean existsByApplicationIdAndDocumentType(UUID applicationId, DocumentType documentType);
}
