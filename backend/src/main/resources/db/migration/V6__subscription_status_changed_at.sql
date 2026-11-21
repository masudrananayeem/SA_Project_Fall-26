-- V23.4: remember when a subscription left ACTIVE, so the month it was paused, blocked or cancelled
-- is still invoiced once (every accepted event is billed exactly once).
ALTER TABLE subscriptions ADD COLUMN status_changed_at DATE;
