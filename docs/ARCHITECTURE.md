# Architecture

## Context
SaaS/API product operators configure plans and subscriptions. Customers send usage events. MeterFlow meters usage, rates it, produces explainable invoices and exposes spend information.

## Modules
- `metering`: ingestion, idempotency, usage aggregation.
- `catalogue`: plan versions and charge strategies.
- `subscriptions`: subscription lifecycle and plan binding.
- `invoicing`: billing orchestration, invoice builder, invoice lines.
- `payments`: mock payment adapter and payment lifecycle (extend with dunning state machine).
- `notifications`: webhook endpoint registry and delivery (extend with signed delivery/retries).
- `shared`: Money, security, validation/error handling and seed data.

## Key decisions
- Modular monolith rather than microservices: the assignment explicitly focuses on Spring Modulith and local Docker Compose operation.
- PostgreSQL as source of truth; unique idempotency and customer-period constraints are correctness barriers.
- Outbox-backed asynchronous integration is the selected target direction; Kafka is not required for this baseline.
- React SPA separates operator and customer-facing experiences in one frontend app.

## Billing sequence
1. API validates event batch and persists events using idempotency key.
2. Billing job selects active subscriptions for target period.
3. Plan version bound to each subscription is loaded.
4. Usage is aggregated for the plan metric.
5. Pricing strategy calculates charge after free allowance and minimum commitment.
6. Draft invoice and explainable line are persisted transactionally.
7. Invoice is finalised explicitly; mock payment can then be invoked.

## Reliability caveat
A unique constraint prevents duplicate customer-period invoice records. A full restartable Spring Batch job, durable outbox publisher and atomic insert-on-conflict ingestion should be added and proven under process termination before treating this as production-grade exactly-once billing.
