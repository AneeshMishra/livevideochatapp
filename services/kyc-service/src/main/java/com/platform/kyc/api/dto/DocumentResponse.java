package com.platform.kyc.api.dto;

import com.platform.kyc.domain.KycDocument;

import java.time.Instant;
import java.util.UUID;

public record DocumentResponse(
        UUID id,
        String documentType,
        String contentType,
        Long fileSizeBytes,
        Instant uploadedAt
) {
    public static DocumentResponse from(KycDocument doc) {
        return new DocumentResponse(
            doc.getId(),
            doc.getDocumentType().name(),
            doc.getContentType(),
            doc.getFileSizeBytes(),
            doc.getUploadedAt()
        );
    }
}
