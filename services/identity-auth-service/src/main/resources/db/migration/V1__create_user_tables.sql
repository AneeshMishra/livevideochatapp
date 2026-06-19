-- Users table: core identity record
CREATE TABLE users (
    id              UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    email           VARCHAR(255) NOT NULL,
    username        VARCHAR(50)  NOT NULL,
    password_hash   VARCHAR(255) NOT NULL,
    display_name    VARCHAR(100),
    status          VARCHAR(30)  NOT NULL DEFAULT 'ACTIVE',
    mfa_enabled     BOOLEAN      NOT NULL DEFAULT false,
    failed_login_attempts INT    NOT NULL DEFAULT 0,
    locked_until    TIMESTAMPTZ,
    last_login_at   TIMESTAMPTZ,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT uq_users_email    UNIQUE (email),
    CONSTRAINT uq_users_username UNIQUE (username),
    CONSTRAINT chk_users_status  CHECK (status IN (
        'PENDING_VERIFICATION', 'ACTIVE', 'SUSPENDED', 'BANNED'
    ))
);

-- Per-user role assignments
CREATE TABLE user_roles (
    user_id UUID        NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    role    VARCHAR(30) NOT NULL,
    PRIMARY KEY (user_id, role),
    CONSTRAINT chk_user_roles_role CHECK (role IN (
        'VIEWER', 'BROADCASTER', 'MODERATOR', 'STUDIO_OWNER', 'ADMIN'
    ))
);

CREATE INDEX idx_users_email    ON users(email);
CREATE INDEX idx_users_username ON users(username);
CREATE INDEX idx_users_status   ON users(status);
