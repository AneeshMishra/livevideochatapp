-- Private show sessions: one row per viewer-broadcaster pairing for a single show.
CREATE TABLE IF NOT EXISTS private_show_sessions (
    id              UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    viewer_id       UUID        NOT NULL,
    broadcaster_id  UUID        NOT NULL,
    room_id         UUID        NOT NULL,
    show_type       VARCHAR(20) NOT NULL CHECK (show_type IN ('PRIVATE', 'SPY', 'GROUP')),
    status          VARCHAR(20) NOT NULL DEFAULT 'ACTIVE'
                                CHECK (status IN ('ACTIVE', 'PAUSED', 'COMPLETED')),
    rate_per_minute BIGINT      NOT NULL CHECK (rate_per_minute > 0),
    billed_minutes  INTEGER     NOT NULL DEFAULT 0,
    total_tokens    BIGINT      NOT NULL DEFAULT 0,
    started_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    ended_at        TIMESTAMPTZ,
    end_reason      VARCHAR(30) CHECK (end_reason IN (
                        'VIEWER_ENDED', 'BROADCASTER_ENDED',
                        'INSUFFICIENT_FUNDS', 'STREAM_ENDED')),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_pshow_viewer     ON private_show_sessions (viewer_id, started_at DESC);
CREATE INDEX IF NOT EXISTS idx_pshow_broadcaster ON private_show_sessions (broadcaster_id, started_at DESC);
CREATE INDEX IF NOT EXISTS idx_pshow_room       ON private_show_sessions (room_id);
CREATE INDEX IF NOT EXISTS idx_pshow_status     ON private_show_sessions (status) WHERE status IN ('ACTIVE', 'PAUSED');

-- Immutable audit log of every per-minute charge.
-- Idempotency key = sessionId + minute_number (handled in app via wallet service).
CREATE TABLE IF NOT EXISTS billing_ticks (
    id              UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    session_id      UUID        NOT NULL REFERENCES private_show_sessions(id),
    viewer_id       UUID        NOT NULL,
    broadcaster_id  UUID        NOT NULL,
    tokens_charged  BIGINT      NOT NULL CHECK (tokens_charged > 0),
    minute_number   INTEGER     NOT NULL,
    wallet_tx_id    UUID,
    billed_at       TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT uq_billing_tick_session_minute UNIQUE (session_id, minute_number)
);

CREATE INDEX IF NOT EXISTS idx_billing_ticks_session ON billing_ticks (session_id, minute_number);
