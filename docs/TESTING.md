# Testing and evidence

## Commands

| Command | What runs | Needs |
|---|---|---|
| `cd backend && mvn verify` | Unit tests, Spring Modulith `ArchitectureTests`, QA1 harness at 100,000 events | JDK 21, Maven |
| `mvn verify -Pintegration` | Above + `PostgresBillingIntegrationTest` (real PostgreSQL via Testcontainers) | Docker |
| `mvn test -Dtest=Qa1AccuracyTest -Dqa1.events=1000000 -DargLine=-Xmx3g` | QA1 at acceptance size | ~3 GB heap |
| `cd frontend && npm ci && npm run build` | TypeScript strict build | Node 22 |
| `k6 run -e BILLING_PERIOD=2026-10 performance/ingest.js` | QA2: 2,000 events/s with a billing job running | Running stack |

## Test map

| Area | Tests |
|---|---|
| Pricing (FR3, QA4) | `ChargeStrategiesTest`, `PriceQuoteTest`, `PricingServiceTest`, `ChargeRegistryExtensionTest` |
| Ingestion (FR1) | `UsageServiceIngestionTest` (72-hour window, in-batch duplicates, ledger, outbox event) |
| Aggregation (FR2) | `MetricAggregationTest`, `MetricAggregationEndpointLogicTest` |
| Live spend (FR7, QA5) | `LiveSpendServiceTest`, `BudgetThresholdTest` |
| Subscriptions (FR4) | `SubscriptionLifecycleTest` (states, trials, proration arithmetic) |
| Billing (FR5, FR6, FR10) | `BillingRunTest` (idempotency, late usage, proration, simulator, visitors), `InvoiceDocumentTest`, `InvoiceLineProvenanceTest`, `PartitionCustomerReaderTest` |
| Dunning (FR8) | `DunningCaseTest` |
| Architecture | `ArchitectureTests` (Modulith verify) |
| QA1 | `Qa1AccuracyTest` |
| Database path | `PostgresBillingIntegrationTest` (tag `integration`) |

`BillingTestWorld` wires the production `UsageService`, `InvoiceBillingRun` and
`SimulationBillingRun` to in-memory repositories, so billing rules are tested end to end without a
database; the database-specific behaviour is covered by the integration test.

## Executed so far
See `quality-evidence/raw/` for the offline run of 2026-11-16 (63 tests passed; QA1 at 1,000,000
events passed). That run used API stubs because Maven Central was unreachable; `mvn verify` with
the real libraries still has to be run (CI is configured to do so).

## Remaining evidence
- QA2: k6 output with machine spec, while the billing job runs.
- QA3: 10,000 customers, kill the JVM mid-run, restart, re-launch the same period, then
  `select customer_id, period, count(*) from invoices group by 1,2 having count(*) > 1` must be empty.
- QA5: one `GET /api/invoices/lines/{id}/trace` response and an estimate-vs-invoice comparison from a running stack.
