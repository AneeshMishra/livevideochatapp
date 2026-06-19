-- Immutable ledger: every credit/debit is appended here, never updated or deleted
CREATE TABLE wallet_transactions (
    id                  UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    wallet_id           UUID        NOT NULL,
    amount              BIGINT      NOT NULL,       -- positive=credit, negative=debit
    transaction_type    VARCHAR(30) NOT NULL,
    balance_after       BIGINT      NOT NULL,       -- running balance snapshot for audit
    idempotency_key     VARCHAR(255) NOT NULL,      -- caller-provided deduplication key
    reference_id        UUID,                       -- tipId, orderId, sessionId …
    reference_type      VARCHAR(50),
    counterpart_wallet_id UUID,                     -- the other wallet in a transfer
    description         VARCHAR(500),
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT uq_wallet_tx_idempotency_key UNIQUE (idempotency_key),
    CONSTRAINT chk_wallet_tx_type CHECK (transaction_type IN (
        'PURCHASE', 'TIP_SENT', 'TIP_RECEIVED', 'PLATFORM_FEE',
        'PRIVATE_SHOW_DEBIT', 'PRIVATE_SHOW_CREDIT',
        'CONTENT_PURCHASE', 'CONTENT_SALE',
        'SUBSCRIPTION_DEBIT', 'SUBSCRIPTION_CREDIT',
        'REFUND', 'ADJUSTMENT'
    ))
);

CREATE INDEX idx_wallet_tx_wallet_id       ON wallet_transactions(wallet_id);
CREATE INDEX idx_wallet_tx_idempotency_key ON wallet_transactions(idempotency_key);
CREATE INDEX idx_wallet_tx_reference_id    ON wallet_transactions(reference_id) WHERE reference_id IS NOT NULL;
CREATE INDEX idx_wallet_tx_created_at      ON wallet_transactions(created_at DESC);
