package com.platform.kyc.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "kyc_documents")
@Getter
@Setter
@NoArgsConstructor
public class KycDocument {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "application_id", nullable = false, updatable = false)
    private KycApplication application;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false, length = 30)
    private DocumentType documentType;

    /** AES-256-encrypted S3 object key. Never log or expose outside this service. */
    @Column(nullable = false, columnDefinition = "TEXT")
    private String s3RefEncrypted;

    @Column(nullable = false, length = 100)
    private String contentType;

    private Long fileSizeBytes;

    @Column(nullable = false, updatable = false)
    private Instant uploadedAt;

    @PrePersist
    private void prePersist() {
        uploadedAt = Instant.now();
    }

    public static KycDocument create(KycApplication application, DocumentType type,
                                     String encryptedRef, String contentType, long sizeBytes) {
        KycDocument doc = new KycDocument();
        doc.application = application;
        doc.documentType = type;
        doc.s3RefEncrypted = encryptedRef;
        doc.contentType = contentType;
        doc.fileSizeBytes = sizeBytes;
        return doc;
    }
}
