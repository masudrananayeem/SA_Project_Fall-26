-- MeterFlow V23: prepaid plans (pay before use), payment ledger.
ALTER TABLE plans ADD COLUMN IF NOT EXISTS billing_mode VARCHAR(20) NOT NULL DEFAULT 'POSTPAID';

CREATE TABLE payment_records (
    id          UUID PRIMARY KEY,
    invoice_id  UUID,
    customer_id VARCHAR(255) NOT NULL,
    amount      NUMERIC(18, 2) NOT NULL,
    currency    VARCHAR(3)   NOT NULL,
    method      VARCHAR(30)  NOT NULL,
    kind        VARCHAR(20)  NOT NULL,
    status      VARCHAR(20)  NOT NULL,
    reference   VARCHAR(120),
    message     VARCHAR(300),
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX idx_payment_records_customer ON payment_records (customer_id, created_at);
CREATE INDEX idx_payment_records_created ON payment_records (created_at);
