# CSE 444 — MeterFlow Master Criteria Audit (V23 final, 2026-11-19)

**Student:** Masud Rana Nayeem  
**Student ID:** 0242310005101849  
**Section:** 64-K (188106)  
**Project:** Project 2 — MeterFlow  
**Audit basis:** `CSE444_Course_Projects.docx` and `CSE444_Project_Assignments_Section_64K_188106.docx` supplied for this assignment.

> Status vocabulary: **Implemented** = present in source; **Partial** = some code exists but acceptance condition is not met; **Pending evidence** = must be executed and raw output retained; **External dependency** = instructor input or live defence is required. Documentation alone is not proof of implementation.

## V23 final status (supersedes the V18 table below)

Checked requirement by requirement against the source in this version. "Verified" means the item
was exercised against the running stack by `scripts/feature-test.ps1` or a unit test. "Run it"
means the mechanism is in place but needs a measured run on your machine (the command is given).

### Functional requirements

| ID | Requirement (short) | Status | Where in the code | Proof |
|---|---|---|---|---|
| FR1 | Batch ≤1,000 events, duplicates ignored, late ≤72 h after close → adjustment | Verified (V23.4: usage is only accepted while a subscription will invoice that month) | `UsageController.BatchInput @Size(max=1000)`, `JdbcUsageEventStore` (`ON CONFLICT DO NOTHING`), `UsageService.periodClose` + `LateUsageAdjustment` | `UsageServiceIngestionTest`; feature test "40 accepted, re-send = 40 duplicates", future/late events |
| FR2 | sum, count, max, unique count, latest; filters (region, endpoint) | Verified | `metering/aggregation/*`, `BillableUsageCalculator`, `GET /api/usage/aggregate` | `MetricAggregationTest`, `BillableMetricBillingTest`; feature test unique_count(endpoint)=4 |
| FR3 | flat, per unit, graduated, volume, package, per seat; allowance; minimum; BDT/USD | Verified | `catalogue/charges/*` (6 strategies + registry), `PricingService` | `ChargeStrategiesTest`, `PriceQuoteTest`, `PricingServiceTest` |
| FR4 | create, upgrade/downgrade (now + proration, or period end), pause, resume, cancel, trials | Verified | `SubscriptionState` (State), `PlanChangeService`, `SubscriptionLifecycleService` (+ `POST /api/subscriptions/lifecycle/run`); V23.2: upgrade of an already-invoiced (prepaid) month settled by `UpgradeChargeService` (balance due) or credit note (downgrade); closed months priced by the plan in force on their last day | `SubscriptionLifecycleTest`, `AccountStandingStateTest`, `PlanChangeSettlementTest`; feature test plan change / pause / resume / upgrade balance / downgrade credit |
| FR5 | Billing date → draft → finalise; each line has quantity, price rule, tax rule | Verified (V23.4: months in which a subscription was paused, blocked or cancelled are still invoiced once, `Subscription.billableIn`) | `AbstractBillingRun` (Template Method), `InvoiceBuilder`, batch job; billing date = 1st of month 00:30 UTC (Spring) and 00:15 UTC (Cloudflare cron), see ADR-008 | `BillingRunTest`; feature test "billed once (CREATED, then EXISTING)", invoice lines |
| FR6 | Prepaid credit used first; credit notes; late adjustments on next invoice | Verified | `CreditService.apply` before tax, `CreditNote`, late ledger read by the next billing run; V23 prepaid top-up | `CreditAccountTest`, `BillingRunTest.lateEvents…`; feature test top-up / credit note |
| FR7 | Live usage + estimate ≤1 min; budgets with 50/80/100% alerts | Verified | `LiveSpendService` (30 s cache, same quote as billing), `BudgetService` (after each batch + 60 s sweep), customer pages refresh 30 s | `LiveSpendServiceTest`, `BudgetThresholdTest`; feature test budget alerts |
| FR8 | Failed (mock) payment retried day 1, 3, 7 → past due → suspended → reactivated; notified | Verified | `DunningState.RETRY_DAYS = {1,3,7}`, `DunningCase`, mock provider, `BillingEventNotifier` | `DunningCaseTest`; feature test declined card → dunning → retries → paid |
| FR9 | invoice.finalised, payment.failed, usage.threshold_reached; HMAC; backoff; log; replay | Verified | `WebhookDeliveryService` (HMAC-SHA256 over `timestamp.body`, exponential backoff, delivery rows, replay endpoint) | feature test webhook register / delivery log; replay: `POST /api/webhooks/deliveries/{id}/replay` |
| FR10 | Edit = new version; subscriptions stay pinned; simulator for any version | Verified | `CatalogueController PUT` → new version, migration with dry run, `SimulationBillingRun` | `BillingRunTest.simulator…`; feature test v1 → v2, simulate versions |

V23 additions on top of the brief: prepaid (pay-first) and postpaid plans, automatic block after
2 unpaid invoices, payment ledger with live admin feed, days-on-plan analytics, usage breakdown and
API guide page (see `V23-CHANGELOG.md`).

### Quality attributes

| ID | Status | How to produce the evidence |
|---|---|---|
| QA1 accuracy (1M events, 3% dup, 2% late, exact to the paisa) | Verified (harness) | `.\scripts\backend-tests.ps1 -Qa` runs `Qa1AccuracyTest`; earlier raw result in `quality-evidence/QA1-ACCURACY.md` |
| QA2 2,000 events/s, p95 ≤100 ms during billing | Run it | `performance/ingest.js` with k6 against the local stack while a batch billing run is active (`QUALITY-ACCEPTANCE-RUNBOOK.md`) |
| QA3 10,000 customers ≤10 min, kill + restart without duplicates | Run it | `BATCH-BILLING.md` QA3 procedure (seed, `POST /api/invoices/batch/run`, `docker kill`, restart, re-run, count) |
| QA4 new plan = configuration; new charge type without editing existing classes | Verified | `ChargeRegistryExtensionTest`; feature test publishes plans by API only |
| QA5 one-call trace; estimate within 1% of final | Verified (mechanism) | `GET /api/invoices/lines/{lineId}/trace`; estimate and invoice share `PricingService.quoteForPeriod` (`LiveSpendServiceTest`) |

### Spring Boot architecture focus

| Item | Status |
|---|---|
| Spring Modulith modules + verification test + event publication registry | Done: 10 modules, no cycles (checked again for V23), `ArchitectureTests` (run with `backend-tests.ps1`), ADR-006 |
| Spring Batch: chunking, restartability, partitioning by customer | Done: `BillingBatchConfiguration` (8 partitions, chunk 25, checkpointed reader), stale-execution recovery |
| JPA on PostgreSQL, Money value object, optimistic locking, idempotency keys | Done: `Money`, `@Version` on subscriptions/invoices/dunning/credits, unique idempotency key and `(customer, period)` invoice key |
| Decoupled ingestion (outbox-backed queue) + cache for live estimates | Done: event publication registry (outbox), async listeners, Redis cache (simple cache on Cloudflare) |
| Strategy, Composite, Visitor, State, Template Method, Builder, Observer | Done: see `PATTERN-JUSTIFICATION.md` (V23 adds `PrepaidBillingRun` as a third Template Method subclass and two new subscription states) |

### What only you can supply
- C10 instructor change request: implement it when it is given (do not invent one).
- C11 Git history and AI-usage log: keep committing; update `PROCESS-LOG.md`.
- C12 live defence: demo script in `DEFENCE-GUIDE.md`.
- QA2 / QA3 timed runs: numbers depend on your machine; store the raw output in `quality-evidence/`.

## V18 status (history)

Status words: **Done** = implemented and covered by an executed test; **Implemented** = in source
and compiled, but its test needs Maven/Docker that was not available here; **Pending evidence** =
mechanism exists, a timed/measured run is still needed; **External** = needs the instructor or the
live defence.

### Functional requirements

| ID | Status | Implementation | Test / evidence |
|---|---|---|---|
| FR1 ingestion | Done | `UsageService`, `JdbcUsageEventStore` (batch `ON CONFLICT DO NOTHING`), late ledger; 72 h after period close | `UsageServiceIngestionTest`, QA1 harness; DB path in `PostgresBillingIntegrationTest` (Implemented) |
| FR2 metrics | Done | Plan metric definition (aggregation, property, filters) applied by `BillableUsageCalculator` in billing, estimate, late usage and trace | `MetricAggregationTest`, `BillableMetricBillingTest` |
| FR3 pricing | Done | 6 charge strategies, free allowance (`includedUnits`), minimum commitment line, BDT/USD | `ChargeStrategiesTest`, `PriceQuoteTest` |
| FR4 subscriptions | Done | `SubscriptionState`, `PlanChangeService` (immediate+prorated / period end), pause/resume/cancel, trial convert/expire | `SubscriptionLifecycleTest`, `BillingRunTest.prorationIsPosted…` |
| FR5 billing run | Done | `AbstractBillingRun` template: draft → lines (quantity, price rule, tax rule) → finalise; monthly schedule | `BillingRunTest` |
| FR6 credits/corrections | Done | Credit applied before charging, credit notes, late usage on the next invoice | `BillingRunTest.lateEvents…`, `InvoiceDocumentTest`, QA1 |
| FR7 live spend | Done (source) | Same quote as billing, 30 s cache, budget check after each batch + 60 s sweep, 30 s customer page | `LiveSpendServiceTest`, `BudgetThresholdTest` |
| FR8 dunning | Done | `DunningState` day 1/3/7 → past due → suspended → reactivated; mock provider; notifications | `DunningCaseTest` |
| FR9 webhooks | Implemented | HMAC (timestamp.body), backoff, delivery log, replay; save bug fixed | needs running receiver |
| FR10 plan versions | Done | immutable versions (`PUT` = new version), pinned subscriptions, migration with dry run, simulator for any version | `BillingRunTest.simulator…` |

### Quality attributes

| ID | Status | Evidence |
|---|---|---|
| QA1 | Done (harness) | 1,000,000 events, exact paisa, no double billing – `quality-evidence/QA1-ACCURACY.md` |
| QA2 | Pending evidence | batch insert + async consumers implemented; run `performance/ingest.js` (includes concurrent billing job) |
| QA3 | Pending evidence | partitioned chunks, stale-execution recovery, skip policy, unique constraint; follow `BATCH-BILLING.md` QA3 procedure |
| QA4 | Done | `ChargeRegistryExtensionTest` – new type = new class only |
| QA5 | Done (mechanism) | trace endpoint; estimate and invoice share `PricingService.quoteForPeriod` + tax rule (`LiveSpendServiceTest`) |

### Architecture focus

| Item | Status |
|---|---|
| Spring Modulith modules + verification test | Implemented; module graph checked acyclic with no internal access (script); `ArchitectureTests` needs Maven |
| Event publication registry | Implemented (ADR-006) |
| Spring Batch chunking/partitioning/restart | Implemented (`BillingBatchConfiguration`, `BillingJobLauncher`) |
| JPA + Money + optimistic locking + idempotency keys | Done (`Money`, `@Version` on subscriptions/invoices/dunning/credits, unique keys) |
| Decoupled ingestion + cache | Done (outbox events, Redis cache with fallback) |
| Strategy, Composite, Visitor, State, Template Method, Builder, Observer | Done – see `PATTERN-JUSTIFICATION.md` |

### Common course criteria C1–C12 (changes in V18)
- C1/C5: build fixed (ADR-005); CI runs unit + Modulith + QA1, a Testcontainers job and the 1M QA1 run; frontend uses `npm ci` with a lock file.
- C7: ADR-005, ADR-006 added.
- C8: all seven required patterns are now in end-to-end flows.
- C9: QA1 raw evidence added; QA2/QA3 still need measured runs.
- C10 / C12: still external (instructor change request; live defence). Do not fabricate.
- C11: initialise Git and record real commits and AI usage in `PROCESS-LOG.md`.

### Still open
1. Run `mvn verify` and `mvn verify -Pintegration` with the real libraries and keep the logs.
2. QA2 k6 run and QA3 kill/restart run on the target machine; store raw output in `quality-evidence/`.
3. Boot 4 migration (ADR-005) – optional.

---

# History (V5–V17 notes, superseded by the table above)

## A. Common course criteria C1–C12

| ID | Expected deliverable | Updated status | Evidence / remaining work |
|---|---|---|---|
| C1 | Technology, configuration, one-command local deployment, resource budget | Improved / pending verification | Compose has PostgreSQL+Redis+backend+frontend, persistent `pgdata`, health-gated dependencies and 5 GiB aggregate memory ceilings. Clean-machine startup and 8 GiB host verification remain unrun. See `DEPLOYMENT.md`. |
| C2 | Typed, validated configuration; profiles; no committed secrets | Partial | Dev/test/prod profile files now exist; prod requires environment secrets and uses Hibernate `validate`. Typed `@ConfigurationProperties`, secret-manager integration and complete migration provisioning remain. |
| C3 | API validation, consistent errors, pagination, auth and authorization | Improved / partial | Subscription lists and plan-change history now enforce customer scope; invoices, usage, credits and budgets have ownership checks. Admin operations remain role-protected. Paginated `/api/subscriptions/page` and `/api/invoices/page` routes are now available (max 100); persistent identity, API contract tests and webhook tenant self-service remain. |
| C4 | Structured logs, metrics, traces, correlation IDs and health | Partial | Actuator health/metrics and UTC timestamps are configured. Correlation propagation is now implemented with `X-Correlation-ID` and SLF4J MDC; JSON logging, tracing exporter and dashboard evidence remain. |
| C5 | Unit, integration, architecture, security and CI tests | Improved / pending verification | GitHub Actions workflow now runs Maven verify and frontend build. Unit/architecture tests and Testcontainers dependencies exist; workflow execution and real integration coverage are not yet verified. |
| C6 | Architecture verification and modular boundaries | Pending evidence | Spring Modulith module verification test exists; CI must demonstrate it passes in a supported JDK/Maven environment. |
| C7 | C4 diagrams, ADRs, utility tree, six scenarios, mini-ATAM | Partial | Four ADRs and utility tree exist. See `ARCHITECTURE.md`; C4 diagrams and six quality scenarios are documented; mini-ATAM trade-offs and sensitivity points are in `MINI-ATAM.md`; instructor-aligned review remains. |
| C8 | Pattern selection and justification | Partial | Strategy/Composite/Builder are represented in code. Visitor, State, Template Method and Observer are not all integrated into end-to-end flows. |
| C9 | Quality-evidence report with raw reproducible measurements | Pending evidence | Evidence plan and k6 workload exist. No measured QA1–QA3/QA5 results are claimed. V10 adds a reproducible runbook. Record raw reports in `quality-evidence/`. |
| C10 | Instructor change request and response | External dependency | Await the instructor's actual change request. Do not fabricate one. Log request, design impact, implementation, tests and commit when received. |
| C11 | Git history, contribution/process record and AI usage log | Partial | `.gitignore` exists, but the delivered ZIP is not proof of repository history. Initialise Git locally and record genuine commits and AI use in `PROCESS-LOG.md`. |
| C12 | 15-minute live defence | External dependency | Prepare demo script and Q&A in `DEFENCE-GUIDE.md`; live performance can only be assessed during the defence. |

## B. Functional requirements FR1–FR10

| ID | Requirement | Updated status | Implemented / remaining |
|---|---|---|---|
| FR1 | Batch ingestion ≤1,000, idempotency, late events up to 72h after close | Partial | Batch validation, unique idempotency key, period-close-based 72h acceptance and late-usage adjustment ledger exist. Late adjustments are currently recorded as separate adjustment records, not automatically merged into a next-cycle invoice; QA1-scale proof remains. |
| FR2 | sum/count/max/unique/latest + property filters | Implemented (source-level) | Aggregation endpoint/service supports the required operations and property filters; unit and endpoint-logic tests are present. Execution evidence still belongs in CI. |
| FR3 | flat, per-unit, graduated, volume, package, per-seat, allowance, minimum, BDT/USD | Improved / partial | Six strategy types are persisted/evaluated; negative quantities/amounts and malformed or unbounded tier definitions are rejected. Allowance semantics across multiple charge components and exhaustive BDT/USD boundary tests remain. |
| FR4 | create, upgrade/downgrade, proration, pause/resume/cancel, trials | Improved / partial | Added idempotent daily trial-expiry transition alongside scheduled plan changes. Full period-boundary billing/proration, overlapping subscriptions and lifecycle integration tests remain. |
| FR5 | draft/final invoice, explainable lines and configured tax | Partial | Draft/finalise, pricing lines and plan tax rate exist. Restartable Spring Batch billing, event-level provenance is now stored on invoice lines; estimate-vs-final reconciliation remains. |
| FR6 | prepaid credits, credit notes, late adjustments | Partial | Prepaid balance, top-up, credit notes, invoice credit application and late-usage adjustment ledger exist. Refund/reversal/dispute lifecycle and automatic late-adjustment invoice integration remain. |
| FR7 | live estimate, budgets, 50/80/100 alerts, ≤1-minute refresh | Improved / partial | Budget persistence, threshold dedupe and webhook alerts implemented; current-period budgets now refresh every five minutes and estimate by requested period. The assignment’s ≤1-minute target, concurrency and end-to-end delivery still need measured verification. |
| FR8 | mock payment failure, retries days 1/3/7, state transitions, notifications | Improved / partial | Retry worker now pauses an active subscription after the day-7 terminal failure and reactivates a paused subscription on recorded payment success; webhook state notices remain. Scheduler/provider integration tests and exact day-boundary acceptance remain. |
| FR9 | HMAC webhook, retries/backoff, delivery logs, manual replay | Partial | HMAC-SHA256 outbound delivery, durable logs, retry/backoff and replay implemented; security hardening and end-to-end tests remain. |
| FR10 | immutable plan versions, subscriptions pinned, migration, historical simulator | Partial | Plan migration and historical simulation endpoints exist. Immutable pricing snapshots, dry-run migration diff and faithful replay across historical versions remain. |

## C. Quality attributes QA1–QA5

| ID | Acceptance target | Updated status | Required proof |
|---|---|---|---|
| QA1 | 1,000,000 events; 3% duplicates; 2% late; independent recomputation exact; zero double bill | Pending evidence | Deterministic fixture, independent oracle, reconciliation report and duplicate ledger. Late-event ledger exists; independent oracle reconciliation and automatic next-cycle invoice integration still require verification. |
| QA2 | 2,000 events/sec, p95 ≤100 ms during billing | Pending evidence | k6 raw output, machine specs, database size, concurrent billing workload and p95 result. |
| QA3 | 10,000 customers ≤10 minutes; kill/restart with no duplicate invoice | Pending evidence | Restart-safe chunk/partition billing job and kill/restart integration test are not yet demonstrated. |
| QA4 | Configuration-only plan; new charge type without changing existing strategy classes | Partial | Strategy interface and registry tests exist; configuration-driven persisted plan integration and instructor-defined extension demo remain. |
| QA5 | One-call invoice-line traceability; estimate within 1% of final | Partial | Invoice lines now expose source event IDs/count and pricing explanation; estimate-vs-final <=1% reconciliation test remains. |

## D. Code updates included in this revision

1. Added missing derived repository query methods used by `UsageService` so its current-month and metric-window calls have matching Spring Data repository declarations.
2. Enabled Spring method security and restricted plan creation, subscription mutations, invoice billing runs and invoice finalisation to the ADMIN role.
3. Corrected the frontend ingestion notice to display the backend's actual `rejected` response field.
4. Enforced customer ownership for usage summary/estimate and invoice/invoice-line reads; admin reads remain unrestricted.
4. Added this master criteria matrix and supporting architecture, evidence, process and defence documents.

## V10 implementation update (2026-11-15)
- QA5: invoice lines now store the source usage-event IDs and event count used to calculate the line. `InvoiceLineProvenanceTest` checks this contract. A production database migration for the new columns must be included before production rollout.
- C4: added `CorrelationIdFilter` to propagate `X-Correlation-ID` and expose it in SLF4J MDC.
- C7: added `MINI-ATAM.md` with six quality scenarios, trade-offs, sensitivity points and architectural risks.
- C9: added `QUALITY-ACCEPTANCE-RUNBOOK.md` with exact QA1–QA5 evidence procedures. No benchmark results are claimed.
- Updated requirements traceability and evidence folder guidance.

## E. Release gate

Do not mark the project submission-ready until all remaining FRs are implemented, Maven tests and Docker build pass, the frontend production build passes, QA1–QA5 have reproducible evidence, and the external C10/C12 items are completed. This file intentionally reports honest status rather than claiming unrun tests passed.


## V5 implementation update (13 Nov 2026)
- FR6: Added customer credit accounts, prepaid top-ups, credit-note records, currency checks, pessimistic account locking, and credit application during invoice creation. Still partial: credit-note dispute lifecycle, refunds/reversals, and late-event adjustment ledger are not implemented.
- FR7: Added monthly customer budgets, 50/80/100% threshold evaluation, persisted one-time threshold flags, and evaluation after usage ingestion. Still partial: alert delivery is webhook-based only; no email/SMS channel and no independent one-minute polling worker.
- FR8: Added persisted dunning cases, day-1/day-3/day-7 retry schedule, past-due/suspended/reactivated states, mock payment success/failure endpoints, and webhook notifications. Still partial: scheduled retries simulate failure rather than invoking a real payment provider adapter; account/subscription suspension is not yet propagated to all authorization and billing paths.
- FR9: Added durable webhook delivery rows, HMAC-SHA256 signatures, scheduled retries with exponential backoff, delivery logs, and manual replay endpoint. Still partial: endpoint allow-list/complete DNS-rebinding-safe SSRF controls, dead-letter operations, delivery metrics, and automated end-to-end receiver tests remain.
- Invoice finalisation now enqueues `invoice.finalised`; payment failure enqueues `payment.failed`; budget thresholds enqueue `usage.threshold_reached`.
- New unit tests cover credit consumption and dunning state transitions. They have not been executed in this environment because Maven is unavailable.


## V6 implementation update (2026-11-13)
- FR1: event lateness is now measured against the end of the event's UTC billing month plus 72 hours, rather than a rolling 72 hours from event timestamp. A full persisted late-adjustment ledger and separate adjustment invoice workflow remain outstanding.
- FR3: plans may now store charge-definition JSON and PricingService evaluates the registered flat-fee, per-unit, per-seat, package, volume-tier, and graduated-tier strategies. Seat quantity currently defaults to one; configurable subscription seat counts and allowance/commitment composition across mixed charges remain outstanding.
- Existing V5 credit, budget, dunning and webhook code remains in place; production-provider integration and full end-to-end verification are not claimed.

- V6 FR3 qualification: all six registered charge strategy types are now wired into `PricingService` when a plan includes `charges`; per-seat quantity remains fixed at one, and cross-strategy allowances/commitments are not fully modelled.
- V6 FR1 qualification: 72-hour acceptance is period-close-based; a persisted late-adjustment ledger and post-close adjustment invoice remain incomplete.


## V7 implementation update (2026-11-13)
- FR1: added a persistent late-usage adjustment ledger. Late events accepted after their UTC month closes create a pending adjustment record; admin can list pending adjustments by customer and period. Automatic adjustment-invoice creation remains outstanding.
- FR3: per-seat charge evaluation now receives subscription seat count; plan creation supports configurable tax rate.
- FR4: subscription creation supports trial end and seat count; admin can schedule plan changes, apply scheduled changes daily, execute immediate prorated plan changes, and inspect plan-change history.
- FR5/FR10: added a read-only historical billing simulation endpoint. Billing applies plan tax rate and prorates trial-period charges.
- Payment success now marks the invoice PAID and emits a payment.succeeded webhook.
- Performance benchmarks, complete batch restartability, real payment-provider integration, and automatic late-adjustment invoicing still require implementation and execution evidence.


## V8 implementation update (2026-11-13)
- FR1: added `late_usage_adjustments` ledger with unique usage-event reference, pending/invoiced state, admin listing, and separate adjustment record creation. The adjustment amount is calculated as the difference between pricing with and without the late events.
- FR3: seat-aware per-seat strategy is wired through subscription seat count. Tax rate is plan-configurable (0–100%) and tax is calculated on the post-credit invoice subtotal.
- FR4: trial end, seat count, scheduled plan changes, daily scheduled activation, immediate prorated plan changes, and audit history added. Proration is calculated on recurring base amount and recorded; it is not yet posted as a separate balance transaction.
- FR5/FR10: read-only historical simulation added; trial billing is skipped or prorated; tax is calculated. Simulation does not yet reproduce every historical plan version change.
- Remaining major gaps: Spring Batch chunk/partition/restart-safe billing; actual payment service provider; full historical plan migration simulation; benchmark raw evidence; instructor-specific C10; defence C12.

- FR10 additional V8: admin can migrate active subscriptions from a specified plan version to the latest version, with a zero-delta plan-change audit record. This is an administrative migration operation; it does not yet offer a full dry-run diff or reconstruct every historical invoice from immutable pricing snapshots.


## V9 implementation update (2026-11-14)
- C1: Compose now defines PostgreSQL persistence, Redis health checks and per-service memory ceilings totalling 5 GiB. This is a configured ceiling, not benchmark evidence.
- C2: added `application-test.yml` and `application-prod.yml`. Production requires externally supplied credentials, disables Hibernate schema auto-update and hides server error messages. Full typed configuration and Flyway migrations remain open.
- C3: customer subscription list is scoped to the authenticated username; plan-change history verifies subscription ownership. Subscription writes continue to require ADMIN.
- C5: added GitHub Actions workflow for Maven verification and frontend build. It has not been executed here; no green CI claim is made.
- Added `DEPLOYMENT.md` covering local Docker startup, DB persistence, backup/restore, hosted deployment and production security checklist.

## V9 verification record
- ZIP integrity and source-level XML/YAML/JSON syntax checks are the only local static checks recorded for this delivery.
- Maven, Docker and frontend production build were not executed in this environment. All tests and QA1–QA5 performance acceptance remain **pending evidence** until run on a configured machine/CI runner.


## V11 implementation update (2026-11-15)
- C3: added paginated subscription and invoice collection endpoints; legacy list routes remain for existing frontend compatibility. Page size is capped at 100 and customer data remains tenant-scoped.
- C2 / deployment: added production security checklist and explicit database migration procedure. Production profile requires environment-provided secrets; the demo in-memory user store is still not production identity.
- Database: added an additive PostgreSQL SQL migration for V10 invoice provenance columns and backup/deploy instructions. This is not a complete Flyway baseline.
- Release gates unchanged: real Spring Batch restart/recovery, complete Flyway baseline, persistent OIDC/JWT identity, webhook DNS-rebinding/egress hardening, actual CI/test execution and QA benchmark evidence.


## V14 implementation update (2026-11-16)
- FR2: added authenticated `GET /api/usage/aggregate` with sum, count, maximum, unique_count and latest_value operations, exact property filters, distinct-property selection, and a bounded 366-day query window. Added service-level test for filtering.
- QA3 / Spring Batch focus: added partitioned customer billing job, worker step and ADMIN-only `/api/invoices/batch/run` endpoint. Each customer partition executes its own transactional billing operation; invoice uniqueness prevents duplicate customer-period invoices. This is implementation evidence only: 10,000-customer runtime, forced termination, restart and exactly-once reconciliation are still pending execution.
- Existing synchronous `/api/invoices/run` remains for backwards compatibility and small local demonstrations.
- Production profile now initializes Spring Batch metadata tables when absent. This does not replace the still-required complete Flyway baseline for all MeterFlow domain tables.
- No QA1/QA2/QA3/QA5 pass is claimed without measured raw evidence.


## V15 implementation update (2026-11-16)
- QA3 architecture: replaced the V14 one-customer tasklet partition with up to eight customer partitions, a checkpointed `ItemStreamReader`, chunk size 25, and a chunk writer. Each chunk commits through the Spring Batch transaction manager; `BillingService.runCustomer` joins the chunk transaction. The unique `(customer_id, period)` constraint remains the final duplicate guard. Added a reader checkpoint/resume unit test. **This does not prove the 10,000-customer/10-minute or kill/restart acceptance until run.**
- Database: added Flyway V1 clean-install PostgreSQL baseline for all current domain entities and enabled Flyway plus Hibernate schema validation in production. The migration is for a fresh database; existing V14 databases need a separately reviewed migration and cannot be blindly adopted. Spring Batch metadata tables are initialized by Boot's Batch initializer.
- Security: production profile now uses OAuth2 Resource Server JWT validation with an external OIDC issuer; local/dev keeps demo HTTP Basic. JWT `sub` must equal customer ID and `roles` must contain `ADMIN` or `CUSTOMER`. Frontend Settings accepts a bearer token for manual testing; full OIDC browser login/PKCE is not included.
- Webhooks: production requires HTTPS, an exact hostname allowlist, rejects IP literals and non-public DNS answers, and does not follow redirects. DNS answer checking is not socket-level DNS pinning; a controlled egress proxy/firewall is still required for strong DNS-rebinding protection.
- QA2 k6 workload corrected to 200 requests/sec × 10 events = 2,000 submitted events/sec and includes p95 <=100ms and error-rate thresholds. No benchmark has been run.
- Cloudflare configuration now passes OIDC and webhook allowlist settings into the container and declares a SQLite Durable Object migration. No Cloudflare or Vercel deployment has been executed from this environment.

## V15 final audit status (honest release gate)
| Requirement | V15 code status | Acceptance still requiring external execution/input |
|---|---|---|
| FR1 | Partial | 72h late ledger exists; automatic next-invoice late adjustment and 1M-event proof remain. |
| FR2 | Implemented in aggregation API | Verify property filter semantics and connect aggregation definitions to all billing calculations. |
| FR3 | Partial | Six charge strategies exist; verify mixed-charge allowance/minimum semantics and all boundaries. |
| FR4 | Partial | Subscription operations/trial/scheduled change exist; automatic trial conversion/expiry and lifecycle integration need completion. |
| FR5 | Partial | Draft/finalise and provenance exist; invoice lines still need full per-charge/tax breakdown and reconciliation. |
| FR6 | Partial | Credits/notes/late ledger exist; automatic late adjustment invoice integration and reversal lifecycle remain. |
| FR7 | Partial | Estimate/budget thresholds exist; refresh within one minute and estimate parity require measured tests. |
| FR8 | Partial | Dunning state schedule exists; retry scheduler, notifications and subscription suspension/reactivation need end-to-end verification. |
| FR9 | Partial | HMAC/log/retry/replay and production host allowlist exist; socket-level DNS pinning/egress enforcement and receiver tests remain. |
| FR10 | Partial | Versioning/migration/simulator exist; immutable snapshots and faithful arbitrary historical version replay need completion. |
| QA1 | Pending evidence | 1M-event oracle reconciliation and zero-double-bill proof. |
| QA2 | Pending evidence | Run corrected k6 workload concurrently with billing on documented hardware; capture p95 <=100ms. |
| QA3 | Implemented, pending acceptance | Run 10k customers, <=10 minutes, kill/restart and reconcile no duplicates. |
| QA4 | Partial | Instructor-defined new charge type and configuration-only demonstration. |
| QA5 | Partial / pending evidence | One-call provenance is present; <=1% estimate/final reconciliation is not proven. |
| Flyway | Implemented for clean install | Test V1 against a fresh PostgreSQL instance and plan migration for any existing V14 data. |
| Production OIDC/JWT | Implemented as resource-server integration | Configure issuer, claim mapping, keys, real tokens and verify access rules. No OIDC provider/account supplied. |
| Webhook SSRF | Improved, not fully closed | Production allowlist and DNS checks exist; deploy network egress proxy/firewall to pin destination IP. |
| Cloudflare/Vercel | Deployment configuration prepared | Requires user's Cloudflare/Vercel accounts, secrets, domain, paid Container entitlement and live smoke tests. |

Do not describe this ZIP as fully accepted or production deployed until every pending acceptance item is executed and evidence retained. C10 (instructor change request) and C12 (live defence) cannot be completed by source-code changes.


## V17 implementation update (2026-11-16)
- FR7: added a scheduled current-period budget refresh (default every 5 minutes, configurable) with per-budget exception isolation. Threshold notifications remain persisted/deduplicated at 50%, 80%, and 100%.
- FR7: added period-scoped usage estimation so evaluating a budget for a given `YYYY-MM` uses that month's events rather than silently reusing the current-month estimate.
- FR3: hardened the pricing strategy registry against negative quantities/seats/amounts, missing definitions, malformed tier prices/bounds and tier schedules without an unbounded final tier; added regression tests.
- FR8 test compatibility: exposed the persisted dunning case creation timestamp used by the retry schedule assertions.
- Verification limitation: Maven is not installed in the build container and the prior package-manager installation timed out; Docker is unavailable. Java is present, but no claim is made that Maven tests, PostgreSQL integration, frontend production build, k6 benchmarks or deployment passed.

## V17 release status
V17 is a further implementation revision, **not a fully accepted submission**. FR1, FR3–FR10 and QA1–QA5 still have outstanding implementation or execution gates documented above. C10 and C12 remain external dependencies.
