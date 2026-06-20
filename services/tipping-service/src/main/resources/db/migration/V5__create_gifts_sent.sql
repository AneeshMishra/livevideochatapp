-- Records of virtual gifts sent in rooms.
-- Logically similar to tips but richer — carries gift type, animation, and distinct Kafka event.

CREATE TABLE IF NOT EXISTS gifts_sent (
    id                  UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    sender_id           UUID        NOT NULL,
    recipient_id        UUID        NOT NULL,
    room_id             UUID        NOT NULL,
    gift_type_id        UUID        NOT NULL REFERENCES gift_types(id),
    gift_type_name      VARCHAR(100) NOT NULL,   -- denormalised snapshot
    animation_type      VARCHAR(50)  NOT NULL,   -- denormalised snapshot
    token_amount        BIGINT       NOT NULL CHECK (token_amount > 0),
    message             VARCHAR(500),
    idempotency_key     VARCHAR(255) NOT NULL,
    created_at          TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT uq_gifts_sent_idempotency UNIQUE (idempotency_key)
);

CREATE INDEX IF NOT EXISTS idx_gifts_sent_room        ON gifts_sent (room_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_gifts_sent_sender      ON gifts_sent (sender_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_gifts_sent_recipient   ON gifts_sent (recipient_id, created_at DESC);
