-- MeterFlow V18 schema changes (PostgreSQL 15+). Applied by Flyway in the prod profile.
-- The Spring Modulith event_publication table is created by Modulith's own schema initialisation
-- (spring.modulith.events.jdbc.schema-initialization.enabled=true).

-- Plans: FR2 billable-metric definition (aggregation over the metric, optional property and filters).
ALTER TABLE plans ADD COLUMN aggregation VARCHAR(40) NOT NULL DEFAULT 'sum';
ALTER TABLE plans ADD COLUMN aggregation_property VARCHAR(255);
ALTER TABLE plans ADD COLUMN metric_filters_json VARCHAR(2000);

-- Invoices: plan version, quantity and seats used (late-usage adjustments), credit, lifecycle times.
ALTER TABLE invoices ADD COLUMN credit_applied NUMERIC(18,2) NOT NULL DEFAULT 0;
ALTER TABLE invoices ADD COLUMN tax_rule VARCHAR(255);
ALTER TABLE invoices ADD COLUMN plan_id UUID;
ALTER TABLE invoices ADD COLUMN billed_quantity NUMERIC(24,8);
ALTER TABLE invoices ADD COLUMN seat_count INTEGER;
ALTER TABLE invoices ADD COLUMN finalised_at TIMESTAMPTZ;
ALTER TABLE invoices ADD COLUMN paid_at TIMESTAMPTZ;
ALTER TABLE invoices ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
CREATE INDEX idx_invoices_status ON invoices(status);

-- Invoice lines: one line per charge, proration or late-usage adjustment; exact unit prices.
ALTER TABLE invoice_lines ADD COLUMN kind VARCHAR(40) NOT NULL DEFAULT 'USAGE';
ALTER TABLE invoice_lines ADD COLUMN line_name VARCHAR(255);
ALTER TABLE invoice_lines ADD COLUMN ref_value VARCHAR(255);
ALTER TABLE invoice_lines ALTER COLUMN unit_price TYPE NUMERIC(18,8);
ALTER TABLE invoice_lines ALTER COLUMN tax_rule SET DEFAULT 'VAT 0%';

-- Subscriptions: trial outcome is kept (the trial month is prorated) and may convert or expire.
ALTER TABLE subscriptions ADD COLUMN trial_status VARCHAR(255);
ALTER TABLE subscriptions ADD COLUMN trial_auto_convert BOOLEAN NOT NULL DEFAULT TRUE;
UPDATE subscriptions SET trial_status = 'TRIALING' WHERE trial_end IS NOT NULL AND trial_end > CURRENT_DATE;
UPDATE subscriptions SET trial_status = 'CONVERTED' WHERE trial_end IS NOT NULL AND trial_end <= CURRENT_DATE;

-- Plan changes: proration is posted to the invoice of the month the change took effect.
ALTER TABLE subscription_plan_changes ADD COLUMN kind VARCHAR(40) NOT NULL DEFAULT 'IMMEDIATE';
ALTER TABLE subscription_plan_changes ADD COLUMN explanation VARCHAR(500);
ALTER TABLE subscription_plan_changes ADD COLUMN invoice_id UUID;
CREATE INDEX idx_plan_changes_subscription ON subscription_plan_changes(subscription_id, invoice_id);

-- Late-usage ledger: PENDING -> INCLUDED (already on the original invoice) | INVOICED (next invoice) | IGNORED.
ALTER TABLE late_usage_adjustments ADD COLUMN invoice_id UUID;
ALTER TABLE late_usage_adjustments ADD COLUMN resolved_at TIMESTAMPTZ;
CREATE INDEX idx_late_adjustments_customer_period ON late_usage_adjustments(customer_id, billing_period, status);

-- Dunning lifecycle timestamps and optimistic locking.
ALTER TABLE dunning_cases ADD COLUMN past_due_at TIMESTAMPTZ;
ALTER TABLE dunning_cases ADD COLUMN suspended_at TIMESTAMPTZ;
ALTER TABLE dunning_cases ADD COLUMN resolved_at TIMESTAMPTZ;
ALTER TABLE dunning_cases ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
CREATE INDEX idx_dunning_cases_status ON dunning_cases(status);

-- In-app notifications (every billing, payment, dunning and budget event).
CREATE TABLE customer_notifications (
  id UUID PRIMARY KEY,
  customer_id VARCHAR(255) NOT NULL,
  event_type VARCHAR(100) NOT NULL,
  message VARCHAR(1000) NOT NULL,
  created_at TIMESTAMPTZ NOT NULL
);
CREATE INDEX idx_customer_notifications_customer ON customer_notifications(customer_id, created_at);

-- invoice_adjustments (manual late-usage invoices of V7-V17) is no longer written: late usage is now
-- billed as LATE_USAGE lines on the next invoice. The table is kept for historical records.
COMMENT ON TABLE invoice_adjustments IS 'Legacy (V7-V17). Late usage is billed as LATE_USAGE invoice lines since V18.';
