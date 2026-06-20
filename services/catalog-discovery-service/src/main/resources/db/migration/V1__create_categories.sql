CREATE TABLE IF NOT EXISTS categories (
    id            UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    name          VARCHAR(100) NOT NULL,
    slug          VARCHAR(100) NOT NULL,
    description   TEXT,
    display_order INT          NOT NULL DEFAULT 0,
    is_active     BOOLEAN      NOT NULL DEFAULT true,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT uq_categories_name UNIQUE (name),
    CONSTRAINT uq_categories_slug UNIQUE (slug)
);

-- Seed common categories for an adult live-streaming platform
INSERT INTO categories (name, slug, display_order) VALUES
    ('Featured',  'featured',  0),
    ('New',       'new',       1),
    ('Trending',  'trending',  2),
    ('Girls',     'girls',     3),
    ('Guys',      'guys',      4),
    ('Couples',   'couples',   5),
    ('Trans',     'trans',     6),
    ('Mature',    'mature',    7),
    ('Asian',     'asian',     8),
    ('Ebony',     'ebony',     9),
    ('Latina',    'latina',    10),
    ('MILF',      'milf',      11)
ON CONFLICT DO NOTHING;
