-- MeterFlow clean-install baseline. PostgreSQL 15+.
CREATE TABLE plans (
  id UUID PRIMARY KEY, plan_key VARCHAR(255) NOT NULL, version INTEGER NOT NULL,
  name VARCHAR(255) NOT NULL, currency VARCHAR(255) NOT NULL, active BOOLEAN NOT NULL DEFAULT TRUE,
  flat_fee NUMERIC(18,2) NOT NULL DEFAULT 0, unit_price NUMERIC(18,8) NOT NULL DEFAULT 0,
  metric VARCHAR(255) NOT NULL, free_allowance NUMERIC(18,2) NOT NULL DEFAULT 0,
  minimum_commitment NUMERIC(18,2) NOT NULL DEFAULT 0, charge_definitions_json VARCHAR(8000),
  tax_rate NUMERIC(7,4) NOT NULL DEFAULT 0,
  CONSTRAINT uk_plans_key_version UNIQUE(plan_key, version)
);
CREATE TABLE subscriptions (
  id UUID PRIMARY KEY, customer_id VARCHAR(255) NOT NULL, plan_id UUID NOT NULL,
  status VARCHAR(255) NOT NULL DEFAULT 'ACTIVE', period_start DATE NOT NULL, period_end DATE NOT NULL,
  seat_count INTEGER NOT NULL DEFAULT 1, trial_end DATE, scheduled_plan_id UUID,
  scheduled_change_date DATE, version BIGINT NOT NULL DEFAULT 0
);
CREATE INDEX idx_subscriptions_status ON subscriptions(status);
CREATE INDEX idx_subscriptions_customer ON subscriptions(customer_id);
CREATE TABLE usage_events (
  id UUID PRIMARY KEY, customer_id VARCHAR(255) NOT NULL, metric VARCHAR(255) NOT NULL,
  quantity NUMERIC(24,8) NOT NULL, timestamp TIMESTAMPTZ NOT NULL,
  idempotency_key VARCHAR(255) NOT NULL, properties_json VARCHAR(1000),
  CONSTRAINT uk_usage_idempotency UNIQUE(idempotency_key)
);
CREATE INDEX idx_usage_customer_metric_time ON usage_events(customer_id, metric, timestamp);
CREATE TABLE invoices (
  id UUID PRIMARY KEY, customer_id VARCHAR(255) NOT NULL, period VARCHAR(255) NOT NULL,
  status VARCHAR(255) NOT NULL DEFAULT 'DRAFT', currency VARCHAR(255) NOT NULL,
  subtotal NUMERIC(18,2) NOT NULL, tax NUMERIC(18,2) NOT NULL, total NUMERIC(18,2) NOT NULL,
  created_at TIMESTAMPTZ NOT NULL,
  CONSTRAINT uk_invoice_customer_period UNIQUE(customer_id, period)
);
CREATE INDEX idx_invoices_customer_created ON invoices(customer_id, created_at);
CREATE TABLE invoice_lines (
  id UUID PRIMARY KEY, invoice_id UUID NOT NULL, metric VARCHAR(255) NOT NULL,
  quantity NUMERIC(24,8) NOT NULL, unit_price NUMERIC(18,2) NOT NULL,
  amount NUMERIC(18,2) NOT NULL, price_rule VARCHAR(500) NOT NULL,
  tax_rule VARCHAR(500) NOT NULL DEFAULT 'VAT 0% (demo default)',
  source_event_ids_json TEXT NOT NULL DEFAULT '[]', source_event_count INTEGER NOT NULL DEFAULT 0
);
CREATE INDEX idx_invoice_line_invoice ON invoice_lines(invoice_id);
CREATE TABLE credit_accounts (
  id UUID PRIMARY KEY, customer_id VARCHAR(255) NOT NULL UNIQUE,
  balance NUMERIC(18,2) NOT NULL DEFAULT 0, currency VARCHAR(255) NOT NULL,
  version BIGINT NOT NULL DEFAULT 0
);
CREATE TABLE credit_notes (
  id UUID PRIMARY KEY, customer_id VARCHAR(255) NOT NULL, amount NUMERIC(18,2) NOT NULL,
  currency VARCHAR(255) NOT NULL, reason VARCHAR(500) NOT NULL, created_at TIMESTAMPTZ NOT NULL
);
CREATE INDEX idx_credit_notes_customer_created ON credit_notes(customer_id, created_at);
CREATE TABLE invoice_adjustments (
  id UUID PRIMARY KEY, customer_id VARCHAR(255) NOT NULL, period VARCHAR(255) NOT NULL,
  currency VARCHAR(255) NOT NULL, subtotal NUMERIC(18,2) NOT NULL, tax NUMERIC(18,2) NOT NULL,
  total NUMERIC(18,2) NOT NULL, created_at TIMESTAMPTZ NOT NULL, reason VARCHAR(500) NOT NULL
);
CREATE TABLE late_usage_adjustments (
  id UUID PRIMARY KEY, usage_event_id UUID NOT NULL UNIQUE, customer_id VARCHAR(255) NOT NULL,
  billing_period VARCHAR(255) NOT NULL, status VARCHAR(255) NOT NULL DEFAULT 'PENDING', created_at TIMESTAMPTZ NOT NULL
);
CREATE INDEX idx_late_adjustments_customer_status ON late_usage_adjustments(customer_id, status);
CREATE TABLE usage_budgets (
  id UUID PRIMARY KEY, customer_id VARCHAR(255) NOT NULL, period VARCHAR(255) NOT NULL,
  amount NUMERIC(18,2) NOT NULL, currency VARCHAR(255) NOT NULL,
  notified VARCHAR(255) NOT NULL DEFAULT '', updated_at TIMESTAMPTZ NOT NULL,
  CONSTRAINT uk_usage_budgets_customer_period UNIQUE(customer_id, period)
);
CREATE TABLE webhooks (
  id UUID PRIMARY KEY, customer_id VARCHAR(255) NOT NULL, endpoint VARCHAR(1000) NOT NULL,
  secret VARCHAR(255) NOT NULL, active BOOLEAN NOT NULL DEFAULT TRUE
);
CREATE TABLE webhook_deliveries (
  id UUID PRIMARY KEY, webhook_id UUID NOT NULL, event_type VARCHAR(255) NOT NULL,
  payload VARCHAR(4000) NOT NULL, status VARCHAR(255) NOT NULL DEFAULT 'PENDING',
  attempts INTEGER NOT NULL DEFAULT 0, last_response VARCHAR(1000),
  next_attempt_at TIMESTAMPTZ, created_at TIMESTAMPTZ NOT NULL
);
CREATE INDEX idx_webhook_delivery_due ON webhook_deliveries(status, next_attempt_at);
CREATE TABLE dunning_cases (
  id UUID PRIMARY KEY, invoice_id UUID NOT NULL UNIQUE, customer_id VARCHAR(255) NOT NULL,
  status VARCHAR(255) NOT NULL DEFAULT 'RETRYING', attempt INTEGER NOT NULL DEFAULT 0,
  next_attempt_at TIMESTAMPTZ, created_at TIMESTAMPTZ NOT NULL
);
CREATE TABLE subscription_plan_changes (
  id UUID PRIMARY KEY, subscription_id UUID NOT NULL, from_plan_id UUID NOT NULL,
  to_plan_id UUID NOT NULL, prorated_delta NUMERIC(18,2) NOT NULL,
  currency VARCHAR(255) NOT NULL, effective_date DATE NOT NULL
);
