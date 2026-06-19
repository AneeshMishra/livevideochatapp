-- Payment orders: one row per token purchase attempt
CREATE TABLE payment_orders (
    id                    UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id               UUID          NOT NULL,
    provider              VARCHAR(20)   NOT NULL,
    provider_order_id     VARCHAR(255),              -- transaction ID from provider (set on webhook)
    status                VARCHAR(20)   NOT NULL DEFAULT 'PENDING',
    token_amount          BIGINT        NOT NULL,    -- tokens to credit on success
    fiat_amount           NUMERIC(10,2) NOT NULL,    -- charged amount in fiat
    currency_code         CHAR(3)       NOT NULL DEFAULT 'USD',
    idempotency_key       VARCHAR(255)  NOT NULL,    -- unique per purchase attempt; also used by wallet for credit dedup
    provider_redirect_url VARCHAR(2048),             -- URL the client redirects the user to
    error_message         VARCHAR(500),
    raw_webhook_payload   TEXT,                      -- full provider postback stored for audit/replay
    version               BIGINT        NOT NULL DEFAULT 0,  -- optimistic lock
    created_at            TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at            TIMESTAMPTZ   NOT NULL DEFAULT now(),
    completed_at          TIMESTAMPTZ,

    CONSTRAINT uq_po_idempotency_key    UNIQUE (idempotency_key),
    -- Prevents double-crediting when the same provider webhook fires twice
    CONSTRAINT uq_po_provider_order     UNIQUE (provider, provider_order_id),
    CONSTRAINT chk_po_status   CHECK (status IN (
        'PENDING','PROCESSING','COMPLETED','FAILED','REFUNDED','CHARGEBACK'
    )),
    CONSTRAINT chk_po_provider CHECK (provider IN (
        'CCBILL','EPOCH','SEGPAY','VEROTEL','VENDO'
    )),
    CONSTRAINT chk_po_token_amount CHECK (token_amount > 0),
    CONSTRAINT chk_po_fiat_amount  CHECK (fiat_amount  > 0)
);

CREATE INDEX idx_po_user_id      ON payment_orders(user_id);
CREATE INDEX idx_po_status       ON payment_orders(status);
CREATE INDEX idx_po_created_at   ON payment_orders(created_at DESC);
