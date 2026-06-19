-- Follow relationships: viewer → broadcaster
CREATE TABLE follows (
    id          UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    follower_id UUID        NOT NULL,   -- the viewer (references user_profiles.user_id)
    followee_id UUID        NOT NULL,   -- the broadcaster being followed
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT uq_follows_pair  UNIQUE (follower_id, followee_id),
    CONSTRAINT chk_no_self_follow CHECK (follower_id <> followee_id)
);

CREATE INDEX idx_follows_follower ON follows(follower_id);
CREATE INDEX idx_follows_followee ON follows(followee_id);
CREATE INDEX idx_follows_created  ON follows(created_at DESC);

-- User block list
CREATE TABLE block_entries (
    id          UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    blocker_id  UUID        NOT NULL,
    blocked_id  UUID        NOT NULL,
    reason      VARCHAR(255),
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT uq_block_pair    UNIQUE (blocker_id, blocked_id),
    CONSTRAINT chk_no_self_block CHECK (blocker_id <> blocked_id)
);

CREATE INDEX idx_block_blocker ON block_entries(blocker_id);
CREATE INDEX idx_block_blocked ON block_entries(blocked_id);
