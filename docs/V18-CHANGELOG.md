# MeterFlow V18 changelog (2026-11-16)

V18 closes the functional gaps listed as "partial" in V17, fixes two build blockers, and adds
executable evidence for QA1/QA4/QA5. Everything below is in source; what was and was not executed
is listed at the end.

## Build blockers fixed

1. **Backend did not compile.** `pom.xml` used Spring Boot 4.0.0, but the code uses Boot 3 APIs
   (Spring Batch 5 packages such as `org.springframework.batch.core.Job`, Jackson 2
   `com.fasterxml` types, Flyway auto-configured from `flyway-core`). Boot 4 moved all three.
   The build is now pinned to Spring Boot 3.5.16, Spring Modulith 1.4.14, springdoc 2.8.13
   (see ADR-005).
2. **Frontend did not build.** `tsc` failed on `import.meta.env` (missing `src/vite-env.d.ts`).
   Dependencies were `latest`; they are now pinned and `package-lock.json` is committed, so the
   CI `npm ci` cache works.

## Architecture

- **Module cycles removed** (Spring Modulith `verify()` would fail on them):
  `catalogue <-> subscriptions` (plan migration moved to `subscriptions.PlanMigrationController`)
  and `metering <-> budgets` (budgets now listen to a `UsageRecorded` event).
  `shared` no longer depends on `catalogue` (seed data moved into catalogue).
- **Event publication registry** (`spring-modulith-starter-jdbc`): `UsageRecorded`,
  `InvoiceFinalised`, `BudgetThresholdReached`, `PaymentSucceeded`, `PaymentFailed`,
  `DunningStatusChanged` are stored in the publishing transaction and delivered to
  `@ApplicationModuleListener`s after commit; incomplete publications are re-sent on restart.
- **Observer**: `notifications.BillingEventNotifier` turns every event into an in-app
  notification and HMAC-signed webhook deliveries. Billing, payments and budgets no longer call
  the webhook service directly.

## Functional requirements

| FR | Change |
|---|---|
| FR1 | Ingestion is one `INSERT ... ON CONFLICT DO NOTHING` batch (`JdbcUsageEventStore`); duplicates inside a batch and concurrent duplicates are handled by the unique key. Late events within 72 h of period close go to the ledger and are billed **automatically** (see FR6). |
| FR2 | Plans define the billable metric: `aggregation` (sum, count, maximum, unique_count, latest_value), `aggregationProperty` and `metricFilters`. `BillableUsageCalculator` applies it in billing, live estimate, late adjustments (re-aggregation for non-additive metrics) and trace. |
| FR3 | Charges are separate Strategy classes in a registry; plug-in strategies are Spring beans (QA4). Package charges honour `includedUnits`. Every charge produces an explainable line; the minimum commitment is an explicit top-up line. |
| FR4 | Subscription `State` enum (ACTIVE, PAUSED, PAUSED_DUNNING, CANCELLED, EXPIRED). Upgrade/downgrade `IMMEDIATE` (prorated) or `PERIOD_END`. Proration deltas are posted once to the invoice of the change month. Trials convert or expire (`trialAutoConvert`). |
| FR5 | Template Method billing run: draft invoice, one line per charge with quantity, price rule and tax rule, then finalise (`meterflow.billing.auto-finalise`). Scheduled billing date 00:30 UTC on the 1st. |
| FR6 | Late usage after an invoice becomes a `LATE_USAGE` line on the next invoice, priced as `price(billed + late) - price(billed)` with the original plan version. Late events that arrive before the invoice is generated are marked `INCLUDED`. Prepaid credit is applied after tax; credit notes add to the balance. |
| FR7 | Live estimate uses the billing quote and tax rule, DB-side `sum()`, Redis cache with 30 s TTL. Budgets are evaluated after every ingestion batch and swept every 60 s. Customer page refreshes every 30 s. |
| FR8 | Finalised invoices are collected through `MockPaymentProvider`. Dunning `State`: retries day 1, 3, 7 -> PAST_DUE -> SUSPENDED (subscription paused) -> REACTIVATED on payment. Customer notified at each step. `POST /api/dunning/process?asOf=` demonstrates the schedule. |
| FR9 | Signature now covers `timestamp.body` (`X-MeterFlow-Timestamp`) against replay. Fixed a bug where delivery attempts were never saved (self-invocation bypassed `@Transactional`). |
| FR10 | `PUT /api/catalogue/plans/{key}` creates a new version. Simulator prices any month under any plan version (`/api/invoices/simulate?planId=`, `/simulate/versions`). Migration has a dry run. |

## Quality attributes

- QA1: `Qa1AccuracyTest` – 1,000,000 events (3 % duplicates, 2 % late) through the real ingestion and billing code against an independent oracle. Passes (see `quality-evidence/QA1-ACCURACY.md`).
- QA3: stale `STARTED` job executions are marked FAILED on startup so a killed run restarts from the last chunk; invalid customers are skipped, not fatal.
- QA4: `ChargeRegistryExtensionTest` adds a new charge type without editing any pricing class.
- QA5: `GET /api/invoices/lines/{lineId}/trace` returns rule, tax rule, event ids and the quantity recomputed from the events in one call; estimate and invoice use the same quote.

## Executed in this environment

- `javac` of all main and test sources against API stubs (Maven Central was blocked here).
- 63 unit tests with offline JUnit/Mockito stand-ins: all pass (`quality-evidence/raw/`).
- QA1 harness at 1,000,000 events: passes, three identical runs.
- Frontend `npm ci && npm run build`: passes.

## Not executed here (must be run with Maven/Docker)

- `mvn verify` with the real Spring libraries, including `ArchitectureTests` (Modulith) and
  `PostgresBillingIntegrationTest` (Testcontainers, `-Pintegration`).
- Docker Compose start, k6 QA2 load test, QA3 10,000-customer kill/restart run.
