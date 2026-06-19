-- Tip goals: collective token goals set by a broadcaster for their room
CREATE TABLE tip_goals (
    id              UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    broadcaster_id  UUID         NOT NULL,
    room_id         UUID         NOT NULL,
    title           VARCHAR(200) NOT NULL,
    target_tokens   BIGINT       NOT NULL,
    current_tokens  BIGINT       NOT NULL DEFAULT 0,
    status          VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    completed_at    TIMESTAMPTZ,
    version         BIGINT       NOT NULL DEFAULT 0,

    CONSTRAINT chk_tg_status        CHECK (status IN ('ACTIVE','COMPLETED','CANCELLED')),
    CONSTRAINT chk_tg_target        CHECK (target_tokens > 0),
    CONSTRAINT chk_tg_current       CHECK (current_tokens >= 0),
    CONSTRAINT chk_tg_current_lte   CHECK (current_tokens <= target_tokens)
);

-- Enforce only one active goal per broadcaster at a time (partial unique index)
CREATE UNIQUE INDEX uq_tg_broadcaster_active
    ON tip_goals(broadcaster_id)
    WHERE status = 'ACTIVE';

CREATE INDEX idx_tg_broadcaster_room ON tip_goals(broadcaster_id, room_id, status);
