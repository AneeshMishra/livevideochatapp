-- Moderation service schema
-- Run on startup via run_migrations() in database.py

CREATE TABLE IF NOT EXISTS moderation_items (
    id                  UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    room_id             UUID,
    broadcaster_id      UUID,
    submitter_id        UUID,                        -- NULL for automated frame scans
    content_type        VARCHAR(20)  NOT NULL,        -- FRAME | IMAGE | TEXT | REPORT
    content_ref         TEXT,                        -- S3 key (for media) or raw text snippet
    ai_provider         VARCHAR(30),                 -- MOCK | REKOGNITION | HIVE_AI
    ai_result           JSONB,                       -- raw provider response
    ai_safe             BOOLEAN,
    ai_confidence       NUMERIC(5,4),
    status              VARCHAR(20)  NOT NULL DEFAULT 'PENDING',
    reviewed_by         UUID,
    review_notes        TEXT,
    is_csam_flagged     BOOLEAN     NOT NULL DEFAULT false,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    reviewed_at         TIMESTAMPTZ,

    CONSTRAINT chk_item_content_type CHECK (content_type IN ('FRAME','IMAGE','TEXT','REPORT')),
    CONSTRAINT chk_item_status       CHECK (status IN ('PENDING','AUTO_APPROVED','APPROVED','REJECTED','ESCALATED'))
);

CREATE TABLE IF NOT EXISTS reports (
    id                  UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    reporter_id         UUID        NOT NULL,
    room_id             UUID,
    broadcaster_id      UUID,
    reason              VARCHAR(50)  NOT NULL,
    description         TEXT,
    status              VARCHAR(20)  NOT NULL DEFAULT 'OPEN',
    moderation_item_id  UUID        REFERENCES moderation_items(id),
    resolved_by         UUID,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    resolved_at         TIMESTAMPTZ,

    CONSTRAINT chk_report_reason CHECK (reason IN
        ('CSAM','UNDERAGE','NON_CONSENSUAL','SPAM','HARASSMENT','HATE_SPEECH','ILLEGAL','OTHER')),
    CONSTRAINT chk_report_status CHECK (status IN ('OPEN','INVESTIGATING','RESOLVED','DISMISSED'))
);

CREATE TABLE IF NOT EXISTS dmca_notices (
    id                  UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    claimant_name       VARCHAR(200) NOT NULL,
    claimant_email      VARCHAR(200) NOT NULL,
    content_url         TEXT        NOT NULL,
    description         TEXT        NOT NULL,
    status              VARCHAR(20)  NOT NULL DEFAULT 'RECEIVED',
    room_id             UUID,
    broadcaster_id      UUID,
    handled_by          UUID,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    actioned_at         TIMESTAMPTZ,

    CONSTRAINT chk_dmca_status CHECK (status IN ('RECEIVED','PROCESSING','ACTIONED','REJECTED'))
);

-- Immutable audit trail — no DELETE or UPDATE on this table
CREATE TABLE IF NOT EXISTS audit_log (
    id                  BIGSERIAL   PRIMARY KEY,
    action              VARCHAR(60)  NOT NULL,  -- e.g. CONTENT_FLAGGED, CSAM_DETECTED, REPORT_RESOLVED
    actor_id            UUID,                   -- moderator or system
    target_id           UUID,
    target_type         VARCHAR(30),            -- moderation_item | report | dmca_notice
    details             JSONB,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_mod_items_status      ON moderation_items (status);
CREATE INDEX IF NOT EXISTS idx_mod_items_room        ON moderation_items (room_id);
CREATE INDEX IF NOT EXISTS idx_mod_items_broadcaster ON moderation_items (broadcaster_id);
CREATE INDEX IF NOT EXISTS idx_mod_items_csam        ON moderation_items (is_csam_flagged) WHERE is_csam_flagged = true;
CREATE INDEX IF NOT EXISTS idx_reports_status        ON reports (status);
CREATE INDEX IF NOT EXISTS idx_reports_broadcaster   ON reports (broadcaster_id);
CREATE INDEX IF NOT EXISTS idx_dmca_status           ON dmca_notices (status);
CREATE INDEX IF NOT EXISTS idx_audit_target          ON audit_log (target_id, target_type);
CREATE INDEX IF NOT EXISTS idx_audit_actor           ON audit_log (actor_id);
