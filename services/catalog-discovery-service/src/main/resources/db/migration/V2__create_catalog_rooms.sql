CREATE TABLE IF NOT EXISTS catalog_rooms (
    id                      UUID         PRIMARY KEY,
    broadcaster_id          UUID         NOT NULL,
    broadcaster_username    VARCHAR(100) NOT NULL,
    broadcaster_display_name VARCHAR(200),
    broadcaster_avatar_url  TEXT,
    title                   VARCHAR(500) NOT NULL DEFAULT '',
    category                VARCHAR(100),
    tags                    TEXT[]       NOT NULL DEFAULT '{}',
    status                  VARCHAR(20)  NOT NULL DEFAULT 'OFFLINE',
    delivery_mode           VARCHAR(20)  NOT NULL DEFAULT 'WEBRTC',
    viewer_count            BIGINT       NOT NULL DEFAULT 0,
    peak_viewer_count       BIGINT       NOT NULL DEFAULT 0,
    hls_playback_url        TEXT,
    thumbnail_url           TEXT,
    is_featured             BOOLEAN      NOT NULL DEFAULT false,
    geo_block_countries     TEXT[]       NOT NULL DEFAULT '{}',
    stream_started_at       TIMESTAMPTZ,
    created_at              TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at              TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT chk_room_status       CHECK (status IN ('LIVE', 'OFFLINE')),
    CONSTRAINT chk_room_delivery     CHECK (delivery_mode IN ('WEBRTC', 'LL_HLS'))
);

CREATE INDEX IF NOT EXISTS idx_catalog_rooms_broadcaster ON catalog_rooms (broadcaster_id);
CREATE INDEX IF NOT EXISTS idx_catalog_rooms_status      ON catalog_rooms (status);
CREATE INDEX IF NOT EXISTS idx_catalog_rooms_category    ON catalog_rooms (category) WHERE status = 'LIVE';
CREATE INDEX IF NOT EXISTS idx_catalog_rooms_viewer_count ON catalog_rooms (viewer_count DESC) WHERE status = 'LIVE';
CREATE INDEX IF NOT EXISTS idx_catalog_rooms_stream_started ON catalog_rooms (stream_started_at DESC) WHERE status = 'LIVE';
