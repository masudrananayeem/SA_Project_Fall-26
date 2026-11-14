-- MeterFlow V10 additive schema change.
-- Apply to an existing PostgreSQL database before deploying V10 with
-- spring.jpa.hibernate.ddl-auto=validate.
ALTER TABLE invoice_lines
  ADD COLUMN IF NOT EXISTS source_event_ids_json TEXT NOT NULL DEFAULT '[]';
ALTER TABLE invoice_lines
  ADD COLUMN IF NOT EXISTS source_event_count INTEGER NOT NULL DEFAULT 0;
