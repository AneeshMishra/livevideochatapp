-- Wallet: one row per user, keyed by userId from identity-auth-service
CREATE TABLE wallets (
    user_id    UUID    PRIMARY KEY,        -- FK to identity-auth-service.users (cross-service, no DB FK)
    balance    BIGINT  NOT NULL DEFAULT 0,
    version    BIGINT  NOT NULL DEFAULT 0, -- Hibernate optimistic-lock version column
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT chk_wallets_balance_non_negative CHECK (balance >= 0)
);

-- Seed the platform revenue wallet with a well-known UUID
-- (matches app.wallet.platform-wallet-id in application.yml)
INSERT INTO wallets (user_id, balance)
VALUES ('00000000-0000-0000-0000-000000000001', 0)
ON CONFLICT DO NOTHING;
