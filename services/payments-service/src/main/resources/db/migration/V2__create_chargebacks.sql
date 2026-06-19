-- Chargeback records: immutable audit log, one row per chargeback dispute
CREATE TABLE chargeback_records (
    id                     UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    payment_order_id       UUID          NOT NULL REFERENCES payment_orders(id),
    user_id                UUID          NOT NULL,
    provider_chargeback_id VARCHAR(255)  NOT NULL UNIQUE,  -- provider's dispute/chargeback ID
    chargeback_amount      NUMERIC(10,2) NOT NULL,
    currency_code          CHAR(3)       NOT NULL DEFAULT 'USD',
    reason                 VARCHAR(500),
    received_at            TIMESTAMPTZ   NOT NULL DEFAULT now(),
    resolved               BOOLEAN       NOT NULL DEFAULT FALSE
);

CREATE INDEX idx_cb_payment_order_id ON chargeback_records(payment_order_id);
CREATE INDEX idx_cb_user_id          ON chargeback_records(user_id);
CREATE INDEX idx_cb_resolved         ON chargeback_records(resolved) WHERE NOT resolved;
