-- V2: Tip menu items and geo-block rules

CREATE TABLE tip_menu_items (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    broadcaster_id  UUID            NOT NULL REFERENCES broadcasters(id) ON DELETE CASCADE,
    label           VARCHAR(100)    NOT NULL,
    description     VARCHAR(300),
    token_price     INT             NOT NULL,
    sort_order      INT             NOT NULL DEFAULT 0,
    active          BOOLEAN         NOT NULL DEFAULT TRUE,

    CONSTRAINT chk_token_price CHECK (token_price > 0)
);

CREATE INDEX idx_tip_menu_broadcaster ON tip_menu_items(broadcaster_id, sort_order);

CREATE TABLE geo_block_rules (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    broadcaster_id  UUID            NOT NULL REFERENCES broadcasters(id) ON DELETE CASCADE,
    country_code    CHAR(2)         NOT NULL,
    type            VARCHAR(10)     NOT NULL DEFAULT 'BLOCK',

    CONSTRAINT uq_geo_block  UNIQUE (broadcaster_id, country_code),
    CONSTRAINT chk_geo_type  CHECK  (type IN ('BLOCK','ALLOW'))
);

CREATE INDEX idx_geo_block_broadcaster ON geo_block_rules(broadcaster_id);
