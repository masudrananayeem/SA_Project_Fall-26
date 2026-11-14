# MeterFlow V8 changelog

Date: 2026-11-13

## Implemented in this iteration

- Period-close-based 72-hour late-event acceptance (V6) and a persisted `late_usage_adjustments` ledger (V8).
- Admin endpoint to inspect pending late usage by customer and billing period.
- Separate `invoice_adjustments` records for incremental late usage charges; ledger rows move to `INVOICED` after the adjustment record is created.
- Configurable charge definitions are evaluated by the production pricing service. Per-seat charges receive the subscription's seat count.
- Plan tax rate can be configured from 0% to 100%; invoice tax is calculated on the post-credit subtotal.
- Subscription seat count and trial end; scheduled plan changes with daily activation; immediate plan change with remaining-period proration audit; plan-change history.
- Admin migration of active subscriptions from a selected plan version to the latest version, with an audit entry.
- Read-only billing simulation endpoint and PAID invoice state on successful mock payment.

## Important limitations

- Billing is still a synchronous idempotent service, not a fully implemented Spring Batch chunk/partition/restart job.
- Late-adjustment billing uses the customer's currently active plan and rejects mixed/non-current metrics; it does not reconstruct a historical plan snapshot automatically.
- Proration is recorded as an audit delta, but is not automatically posted to a customer balance or invoice.
- Trial support is a simple trial-end date; trial conversion notifications and more complex trial lifecycle states are not implemented.
- Payment processing remains MOCK; no live payment gateway is configured.
- The historical simulation uses the current active subscription/plan; it is not a complete replay engine for historical plan versions.
- QA1–QA3 load/performance evidence and full integration tests must be run in an environment with Maven, PostgreSQL, Redis and Docker.
- C10 requires the instructor's actual change request; C12 requires the student's live defence.
