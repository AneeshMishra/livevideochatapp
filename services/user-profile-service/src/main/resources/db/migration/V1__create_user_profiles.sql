-- User profiles: one row per registered user, keyed by userId from identity-auth-service
CREATE TABLE user_profiles (
    user_id         UUID         PRIMARY KEY,   -- matches id from auth service, not generated here
    username        VARCHAR(50)  NOT NULL,
    display_name    VARCHAR(100),
    avatar_url      VARCHAR(512),
    bio             VARCHAR(500),
    language        VARCHAR(10)  NOT NULL DEFAULT 'en',
    country         CHAR(2),
    following_count INT          NOT NULL DEFAULT 0,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT uq_user_profiles_username UNIQUE (username),
    CONSTRAINT chk_following_count CHECK (following_count >= 0)
);

CREATE INDEX idx_user_profiles_username ON user_profiles(username);
