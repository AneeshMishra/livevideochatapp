-- Per-user notification preferences: which channels are enabled per event type.
CREATE TABLE IF NOT EXISTS notification_preferences (
    id          UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id     UUID        NOT NULL,
    event_type  VARCHAR(60) NOT NULL,
    channel     VARCHAR(20) NOT NULL CHECK (channel IN ('EMAIL','PUSH','INAPP','WHATSAPP')),
    enabled     BOOLEAN     NOT NULL DEFAULT true,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_pref_user_event_channel UNIQUE (user_id, event_type, channel)
);

CREATE INDEX IF NOT EXISTS idx_pref_user ON notification_preferences (user_id);

-- FCM / APNs device tokens for push notifications.
CREATE TABLE IF NOT EXISTS device_tokens (
    id          UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id     UUID        NOT NULL,
    token       VARCHAR(500) NOT NULL,
    platform    VARCHAR(10) NOT NULL CHECK (platform IN ('FCM','APNS')),
    active      BOOLEAN     NOT NULL DEFAULT true,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_device_token UNIQUE (token)
);

CREATE INDEX IF NOT EXISTS idx_device_tokens_user ON device_tokens (user_id) WHERE active = true;

-- Immutable delivery audit log — every send attempt is recorded here.
CREATE TABLE IF NOT EXISTS notification_log (
    id          UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id     UUID        NOT NULL,
    event_type  VARCHAR(60) NOT NULL,
    channel     VARCHAR(20) NOT NULL,
    status      VARCHAR(20) NOT NULL CHECK (status IN ('SENT','FAILED','SKIPPED','RATE_LIMITED','DEDUPED')),
    title       VARCHAR(255),
    body        TEXT,
    metadata    JSONB,
    error       TEXT,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_notif_log_user ON notification_log (user_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_notif_log_status ON notification_log (status, created_at DESC);
