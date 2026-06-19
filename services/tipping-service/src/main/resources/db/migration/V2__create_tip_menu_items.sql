-- Tip menu items: broadcaster-defined actions with a token price
CREATE TABLE tip_menu_items (
    id              UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    broadcaster_id  UUID         NOT NULL,
    title           VARCHAR(100) NOT NULL,
    description     VARCHAR(300),
    token_price     BIGINT       NOT NULL,
    position        INT          NOT NULL DEFAULT 0,   -- display order (ascending)
    active          BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT chk_tmi_token_price CHECK (token_price > 0)
);

CREATE INDEX idx_tmi_broadcaster_active ON tip_menu_items(broadcaster_id, active, position);
