# C4 architecture views

## Context
```mermaid
flowchart LR
  Customer[Customer / SaaS product] -->|Usage events, portal actions| MeterFlow[MeterFlow Billing Platform]
  Admin[Billing operator] -->|Plans, subscriptions, invoices| MeterFlow
  MeterFlow -->|Signed events - planned| CustomerWebhook[Customer webhook endpoint]
  MeterFlow --> PostgreSQL[(PostgreSQL)]
  MeterFlow --> Redis[(Redis cache - optional path)]
```

## Container view
```mermaid
flowchart TB
  Browser[React + TypeScript Console] -->|HTTP Basic over local demo| API[Spring Boot REST API]
  API --> Modules[Spring Modulith modules]
  Modules --> DB[(PostgreSQL)]
  Modules -. future cache integration .-> Cache[(Redis)]
  Modules -. future reliable async delivery .-> Outbox[(Outbox / queue)]
```

## Component/module view
```mermaid
flowchart LR
  Metering[Metering: event API, usage repository, aggregation]
  Catalogue[Catalogue: plan, charge strategies, pricing]
  Subs[Subscriptions: lifecycle and plan binding]
  Invoice[Invoicing: billing service, builder, invoice lines]
  Payments[Payments: mock provider]
  Notify[Notifications: webhook endpoint registry]
  Metering --> Catalogue
  Subs --> Catalogue
  Invoice --> Metering
  Invoice --> Catalogue
  Invoice --> Subs
  Payments --> Invoice
  Notify -. planned domain events .-> Invoice
```

## Deployment view
Docker Compose runs frontend, backend, PostgreSQL and Redis for local development. Validate actual container health and resource usage on the target machine before claiming the 8 GB deployment constraint.

## Key boundaries and constraints
- Money is represented using `BigDecimal`; do not use binary floating point for billing.
- Usage windows should be half-open `[from, to)` to avoid boundary double counting.
- The usage idempotency key has a database uniqueness constraint; application pre-checks alone are not sufficient under concurrency.
- Current security is a local academic HTTP Basic demo. Customer ownership enforcement and production authentication are still required.
