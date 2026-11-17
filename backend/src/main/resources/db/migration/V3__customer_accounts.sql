-- MeterFlow V20: self-service customer accounts (customer portal sign-up and admin-created logins).
-- The username is the customer ID used by every customer-scoped endpoint.
CREATE TABLE customer_accounts (
    id            UUID PRIMARY KEY,
    username      VARCHAR(60)  NOT NULL,
    password_hash VARCHAR(100) NOT NULL,
    display_name  VARCHAR(120) NOT NULL,
    email         VARCHAR(200) NOT NULL,
    company       VARCHAR(160),
    enabled       BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uk_customer_accounts_username UNIQUE (username)
);
