# ADR-006: Module events through the Spring Modulith publication registry

**Status:** Accepted (V18)

## Context
Ingestion called budget evaluation synchronously (slowing QA2), budgets and metering depended on
each other (a module cycle), and invoicing/payments/budgets called the webhook service directly.

## Decision
Modules publish domain events (`UsageRecorded`, `InvoiceFinalised`, `BudgetThresholdReached`,
`PaymentSucceeded`, `PaymentFailed`, `DunningStatusChanged`) with `ApplicationEventPublisher`
inside their transaction. `spring-modulith-starter-jdbc` writes each publication to the
`event_publication` table in the same transaction (transactional outbox). Listeners use
`@ApplicationModuleListener` (async, new transaction, after commit). Unfinished publications are
re-delivered on restart.

## Consequences
- Ingestion does only inserts; budget evaluation and notifications happen asynchronously.
- No module cycles: notifications depends on the event types, not the other way round.
- Listeners must be idempotent (budget thresholds are persisted; payment collection checks status).
- Delivery is at-least-once; a slow listener delays notifications, never ingestion or billing.
