-- Rooms: one row per broadcaster's live room.
-- A broadcaster may own multiple rooms (e.g., different show types).
CREATE TABLE IF NOT EXISTS rooms (
    id                  UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    broadcaster_id      UUID        NOT NULL,
    title               VARCHAR(200) NOT NULL DEFAULT '',
    status              VARCHAR(20) NOT NULL DEFAULT 'OFFLINE',
    delivery_mode       VARCHAR(20) NOT NULL DEFAULT 'WEBRTC',
    ingest_endpoint     TEXT        NOT NULL DEFAULT '',
    stream_key          TEXT        NOT NULL DEFAULT '',
    hls_playback_url    TEXT        NOT NULL DEFAULT '',
    webrtc_playback_url TEXT        NOT NULL DEFAULT '',
    provider            VARCHAR(50) NOT NULL DEFAULT 'MOCK',
    provider_channel_id TEXT        NOT NULL DEFAULT '',
    viewer_count        BIGINT      NOT NULL DEFAULT 0,
    peak_viewer_count   BIGINT      NOT NULL DEFAULT 0,
    promotion_threshold BIGINT      NOT NULL DEFAULT 1000,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    version             BIGINT      NOT NULL DEFAULT 0,

    CONSTRAINT chk_room_status        CHECK (status IN ('OFFLINE','LIVE')),
    CONSTRAINT chk_room_delivery_mode CHECK (delivery_mode IN ('WEBRTC','LL_HLS')),
    CONSTRAINT chk_room_provider      CHECK (provider IN ('IVS','LIVEKIT_CLOUD','MOCK')),
    CONSTRAINT chk_room_threshold     CHECK (promotion_threshold > 0)
);

CREATE INDEX IF NOT EXISTS idx_rooms_broadcaster ON rooms(broadcaster_id);
CREATE INDEX IF NOT EXISTS idx_rooms_status      ON rooms(status);
