-- V2: Immutable 18 USC §2257 compliance records
-- These records MUST NEVER be updated or deleted — legal immutability requirement.
-- All PII columns are AES-256 encrypted at the application layer before storage.
-- Access to this table is restricted to ADMIN role and audited at the application layer.

CREATE TABLE records_2257 (
    id                          UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    broadcaster_id              UUID        NOT NULL,
    kyc_application_id          UUID        NOT NULL REFERENCES kyc_applications(id),

    -- All PII fields encrypted with AES-256; key managed via KMS in production.
    legal_name_encrypted        TEXT        NOT NULL,
    date_of_birth_encrypted     TEXT        NOT NULL,
    address_encrypted           TEXT,

    -- Identity document details (also encrypted)
    document_type_code          VARCHAR(50) NOT NULL,   -- e.g. PASSPORT, DRIVERS_LICENSE, NATIONAL_ID
    document_number_encrypted   TEXT        NOT NULL,
    issuing_country             VARCHAR(3)  NOT NULL,   -- ISO 3166-1 alpha-3

    verified_at                 TIMESTAMPTZ NOT NULL,
    created_at                  TIMESTAMPTZ NOT NULL DEFAULT now()
    -- No updated_at — this record must be immutable
);

-- One active 2257 record per broadcaster (the most recent approved KYC)
CREATE UNIQUE INDEX uidx_2257_broadcaster ON records_2257 (broadcaster_id);

-- Audit log: every read of a 2257 record must be logged
CREATE TABLE records_2257_audit (
    id              UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    record_id       UUID        NOT NULL REFERENCES records_2257(id),
    accessed_by     UUID        NOT NULL,   -- admin user ID
    access_reason   TEXT,
    accessed_at     TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_2257_audit_record   ON records_2257_audit (record_id);
CREATE INDEX idx_2257_audit_accessed ON records_2257_audit (accessed_by);
