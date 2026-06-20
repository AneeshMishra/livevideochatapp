package com.platform.kyc.api.dto;

import com.platform.kyc.domain.Record2257;
import com.platform.kyc.service.EncryptionService;

import java.time.Instant;
import java.util.UUID;

/**
 * Decrypted 2257 record — only returned to ADMIN role.
 * Never cache or log this object.
 */
public record Record2257Response(
        UUID id,
        UUID broadcasterId,
        String legalName,
        String dateOfBirth,
        String address,
        String documentTypeCode,
        String documentNumber,
        String issuingCountry,
        Instant verifiedAt,
        Instant createdAt
) {
    public static Record2257Response from(Record2257 r, EncryptionService enc) {
        return new Record2257Response(
            r.getId(),
            r.getBroadcasterId(),
            enc.decrypt(r.getLegalNameEncrypted()),
            enc.decrypt(r.getDateOfBirthEncrypted()),
            enc.decrypt(r.getAddressEncrypted()),
            r.getDocumentTypeCode(),
            enc.decrypt(r.getDocumentNumberEncrypted()),
            r.getIssuingCountry(),
            r.getVerifiedAt(),
            r.getCreatedAt()
        );
    }
}
