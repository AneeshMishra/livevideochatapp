-- V1: Core broadcaster and studio tables

CREATE TABLE studios (
    id                          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name                        VARCHAR(200)    NOT NULL,
    owner_id                    UUID            NOT NULL UNIQUE,
    default_revenue_split_percent INT           NOT NULL DEFAULT 40,
    created_at                  TIMESTAMPTZ     NOT NULL DEFAULT now(),
    updated_at                  TIMESTAMPTZ     NOT NULL DEFAULT now()
);

CREATE TABLE broadcasters (
    id                          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id                     UUID            NOT NULL UNIQUE,
    studio_id                   UUID            REFERENCES studios(id) ON DELETE SET NULL,
    display_name                VARCHAR(100)    NOT NULL,
    bio                         TEXT,
    avatar_url                  VARCHAR(500),
    status                      VARCHAR(20)     NOT NULL DEFAULT 'OFFLINE',
    kyc_status                  VARCHAR(20)     NOT NULL DEFAULT 'PENDING',
    -- Encrypted S3 reference; never log or expose outside KYC service
    kyc_document_ref            VARCHAR(1000),
    revenue_split_percent       INT             NOT NULL DEFAULT 50,
    created_at                  TIMESTAMPTZ     NOT NULL DEFAULT now(),
    updated_at                  TIMESTAMPTZ     NOT NULL DEFAULT now(),

    CONSTRAINT chk_status        CHECK (status IN ('OFFLINE','ONLINE','PRIVATE','GROUP','AWAY')),
    CONSTRAINT chk_kyc_status    CHECK (kyc_status IN ('PENDING','UNDER_REVIEW','APPROVED','REJECTED','EXPIRED')),
    CONSTRAINT chk_revenue_split CHECK (revenue_split_percent BETWEEN 1 AND 99)
);

CREATE TABLE stream_settings (
    id                              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    broadcaster_id                  UUID        NOT NULL UNIQUE REFERENCES broadcasters(id) ON DELETE CASCADE,
    title                           VARCHAR(200),
    tags                            VARCHAR(500),
    category                        VARCHAR(100),
    private_show_price_per_minute   INT         NOT NULL DEFAULT 30,
    group_show_price_per_minute     INT         NOT NULL DEFAULT 10,
    spy_show_price_per_minute       INT         NOT NULL DEFAULT 6,
    recording_enabled               BOOLEAN     NOT NULL DEFAULT FALSE,
    cam2cam_min_viewer_level        INT         NOT NULL DEFAULT 0,

    CONSTRAINT chk_private_price CHECK (private_show_price_per_minute > 0),
    CONSTRAINT chk_group_price   CHECK (group_show_price_per_minute  > 0),
    CONSTRAINT chk_spy_price     CHECK (spy_show_price_per_minute    > 0)
);

-- Indexes for the discovery query (online broadcasters by status)
CREATE INDEX idx_broadcasters_status   ON broadcasters(status);
CREATE INDEX idx_broadcasters_studio   ON broadcasters(studio_id);
CREATE INDEX idx_broadcasters_user_id  ON broadcasters(user_id);
