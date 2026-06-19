-- Tip ledger: immutable record of every token tip sent
CREATE TABLE tips (
    id                  UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    sender_id           UUID          NOT NULL,
    recipient_id        UUID          NOT NULL,
    room_id             UUID          NOT NULL,
    token_amount        BIGINT        NOT NULL,
    message             VARCHAR(500),
    tip_menu_item_id    UUID,                       -- null for free-form tips
    status              VARCHAR(20)   NOT NULL DEFAULT 'PENDING',
    idempotency_key     VARCHAR(255)  NOT NULL,
    failure_reason      VARCHAR(255),
    sender_display_name VARCHAR(100),               -- denormalised snapshot for Kafka event fan-out
    created_at          TIMESTAMPTZ   NOT NULL DEFAULT now(),
    completed_at        TIMESTAMPTZ,
    version             BIGINT        NOT NULL DEFAULT 0,

    CONSTRAINT uq_tip_idempotency_key UNIQUE (idempotency_key),
    CONSTRAINT chk_tip_status         CHECK (status IN ('PENDING','COMPLETED','FAILED')),
    CONSTRAINT chk_tip_token_amount   CHECK (token_amount > 0),
    CONSTRAINT chk_tip_self_tip       CHECK (sender_id <> recipient_id)
);

CREATE INDEX idx_tip_sender_id    ON tips(sender_id, status);
CREATE INDEX idx_tip_recipient_id ON tips(recipient_id, status);
CREATE INDEX idx_tip_room_id      ON tips(room_id, status, created_at DESC);
CREATE INDEX idx_tip_created_at   ON tips(created_at DESC);
