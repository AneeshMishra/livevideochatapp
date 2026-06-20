package com.platform.kyc.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/**
 * Immutable 18 USC §2257 compliance record for a broadcaster.
 *
 * All PII columns (legalName, dateOfBirth, address, documentNumber) are stored
 * AES-256-encrypted. The encryption key is never stored alongside the data.
 *
 * This entity has NO @Setter and NO @PreUpdate — the record must never be modified
 * after creation. Create a new record when re-verification occurs.
 */
@Entity
@Table(name = "records_2257")
@Getter
@NoArgsConstructor
public class Record2257 {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, updatable = false)
    private UUID broadcasterId;

    @Column(nullable = false, updatable = false)
    private UUID kycApplicationId;

    /** Encrypted: AES-256(legalName) — never log */
    @Column(nullable = false, columnDefinition = "TEXT", updatable = false)
    private String legalNameEncrypted;

    /** Encrypted: AES-256(dateOfBirth ISO-8601) — never log */
    @Column(nullable = false, columnDefinition = "TEXT", updatable = false)
    private String dateOfBirthEncrypted;

    /** Encrypted: AES-256(full address) — nullable for some jurisdictions */
    @Column(columnDefinition = "TEXT", updatable = false)
    private String addressEncrypted;

    /** E.g. PASSPORT, DRIVERS_LICENSE, NATIONAL_ID */
    @Column(nullable = false, length = 50, updatable = false)
    private String documentTypeCode;

    /** Encrypted: AES-256(document number) — never log */
    @Column(nullable = false, columnDefinition = "TEXT", updatable = false)
    private String documentNumberEncrypted;

    /** ISO 3166-1 alpha-3 */
    @Column(nullable = false, length = 3, updatable = false)
    private String issuingCountry;

    @Column(nullable = false, updatable = false)
    private Instant verifiedAt;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    private void prePersist() {
        createdAt = Instant.now();
    }

    public static Record2257 create(UUID broadcasterId, UUID kycApplicationId,
                                    String legalNameEncrypted, String dateOfBirthEncrypted,
                                    String addressEncrypted, String documentTypeCode,
                                    String documentNumberEncrypted, String issuingCountry,
                                    Instant verifiedAt) {
        Record2257 r = new Record2257();
        r.broadcasterId = broadcasterId;
        r.kycApplicationId = kycApplicationId;
        r.legalNameEncrypted = legalNameEncrypted;
        r.dateOfBirthEncrypted = dateOfBirthEncrypted;
        r.addressEncrypted = addressEncrypted;
        r.documentTypeCode = documentTypeCode;
        r.documentNumberEncrypted = documentNumberEncrypted;
        r.issuingCountry = issuingCountry;
        r.verifiedAt = verifiedAt;
        return r;
    }
}
