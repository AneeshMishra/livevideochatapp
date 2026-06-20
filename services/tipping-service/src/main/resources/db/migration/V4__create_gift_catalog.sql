-- Gift catalog: broadcaster-platform virtual animated gifts
-- Admin-managed; prices in tokens; each gift triggers a specific on-screen animation.

CREATE TABLE IF NOT EXISTS gift_types (
    id              UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    name            VARCHAR(100) NOT NULL,                  -- e.g. "Rose", "Diamond", "Fire"
    slug            VARCHAR(100) NOT NULL,                  -- url-safe identifier
    description     VARCHAR(500),
    icon_url        TEXT,
    animation_type  VARCHAR(50)  NOT NULL DEFAULT 'NONE',   -- NONE | CONFETTI | FIREWORKS | HEARTS | CUSTOM
    token_price     BIGINT       NOT NULL CHECK (token_price > 0),
    display_order   INT          NOT NULL DEFAULT 0,
    is_active       BOOLEAN      NOT NULL DEFAULT true,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT uq_gift_types_slug UNIQUE (slug)
);

-- Seed standard gift catalog
INSERT INTO gift_types (name, slug, description, animation_type, token_price, display_order) VALUES
    ('Rose',         'rose',         'A classic rose gift',              'HEARTS',     25,   0),
    ('Heart',        'heart',        'Send some love',                   'HEARTS',     10,   1),
    ('Fire',         'fire',         'This stream is on fire!',          'CONFETTI',   50,   2),
    ('Diamond',      'diamond',      'Sparkle and shine',                'FIREWORKS',  500,  3),
    ('Crown',        'crown',        'All hail the broadcaster',         'FIREWORKS',  1000, 4),
    ('Rocket',       'rocket',       'Blast off into the leaderboard',   'CONFETTI',   200,  5),
    ('Ice Cream',    'ice-cream',    'Sweet and fun',                    'CONFETTI',   30,   6),
    ('Rainbow',      'rainbow',      'A colourful surprise',             'HEARTS',     150,  7),
    ('Lightning',    'lightning',    'Strike fast!',                     'CONFETTI',   75,   8),
    ('Trophy',       'trophy',       'You are number one!',              'FIREWORKS',  2000, 9)
ON CONFLICT DO NOTHING;

CREATE INDEX IF NOT EXISTS idx_gift_types_active ON gift_types (is_active, display_order);
