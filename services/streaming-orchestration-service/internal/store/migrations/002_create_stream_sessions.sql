-- Stream sessions: one row per broadcaster "go live → end stream" lifecycle.
-- Used for analytics (peak viewers, session duration) and revenue attribution.
CREATE TABLE IF NOT EXISTS stream_sessions (
    id              UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    room_id         UUID        NOT NULL REFERENCES rooms(id),
    broadcaster_id  UUID        NOT NULL,
    status          VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    started_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    ended_at        TIMESTAMPTZ,
    peak_viewers    BIGINT      NOT NULL DEFAULT 0,

    CONSTRAINT chk_session_status CHECK (status IN ('ACTIVE','ENDED'))
);

CREATE INDEX IF NOT EXISTS idx_sessions_room        ON stream_sessions(room_id, started_at DESC);
CREATE INDEX IF NOT EXISTS idx_sessions_broadcaster ON stream_sessions(broadcaster_id, started_at DESC);
