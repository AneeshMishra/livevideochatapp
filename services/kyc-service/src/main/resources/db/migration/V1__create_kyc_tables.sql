-- V1: KYC application lifecycle and document tracking

CREATE TABLE kyc_applications (
    id                      UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    applicant_id            UUID        NOT NULL,
    applicant_type          VARCHAR(20) NOT NULL,   -- VIEWER | BROADCASTER
    status                  VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    vendor_provider         VARCHAR(20) NOT NULL DEFAULT 'MOCK',
    vendor_session_id       VARCHAR(255),
    vendor_verification_url TEXT,
    rejection_reason        TEXT,
    verified_at             TIMESTAMPTZ,
    expires_at              TIMESTAMPTZ,
    submitted_at            TIMESTAMPTZ,
    created_at              TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at              TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT chk_applicant_type CHECK (applicant_type IN ('VIEWER','BROADCASTER')),
    CONSTRAINT chk_status         CHECK (status IN ('PENDING','SUBMITTED','UNDER_REVIEW','APPROVED','REJECTED','EXPIRED')),
    CONSTRAINT chk_vendor         CHECK (vendor_provider IN ('MOCK','VERIFF','YOTI','PERSONA'))
);

-- One active application per applicant (allow multiple historical ones after rejection/expiry)
CREATE UNIQUE INDEX uidx_kyc_applicant_active
    ON kyc_applications (applicant_id)
    WHERE status IN ('PENDING','SUBMITTED','UNDER_REVIEW','APPROVED');

CREATE INDEX idx_kyc_applicant_id  ON kyc_applications (applicant_id);
CREATE INDEX idx_kyc_status        ON kyc_applications (status);
CREATE INDEX idx_kyc_vendor_session ON kyc_applications (vendor_session_id) WHERE vendor_session_id IS NOT NULL;

-- ── Documents ────────────────────────────────────────────────────────────────
-- Stores encrypted S3 references — never the actual file content.
-- All fields that could identify a person are stored as encrypted S3 object keys.

CREATE TABLE kyc_documents (
    id              UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    application_id  UUID        NOT NULL REFERENCES kyc_applications(id) ON DELETE CASCADE,
    document_type   VARCHAR(30) NOT NULL,
    -- Encrypted S3 object key. Never log or expose this value.
    s3_ref_encrypted TEXT       NOT NULL,
    content_type    VARCHAR(100) NOT NULL,
    file_size_bytes BIGINT,
    uploaded_at     TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT chk_doc_type CHECK (document_type IN (
        'ID_FRONT','ID_BACK','SELFIE','MODEL_RELEASE_2257','PROOF_OF_AGE','ADDRESS_PROOF'
    ))
);

CREATE INDEX idx_kyc_docs_application ON kyc_documents (application_id);
