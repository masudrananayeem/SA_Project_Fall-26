# MeterFlow V12 — Assignment Compliance Audit

**Audit date:** 16 November 2026  
**Compared against:** the Project 2 brief supplied in the conversation and the uploaded `MeterFlow_Full_Project_Masud_Rana_Nayeem_v11(1).zip`.  
**Purpose:** distinguish source-code evidence from partial implementations and acceptance evidence. This is a source review, not a claim that the application has passed runtime tests.

## Executive verdict

The uploaded V11 archive is a substantial academic prototype, but it does **not** fully satisfy the brief yet. V12 corrects one confirmed pricing-estimate defect (per-seat estimates now use the subscription seat count), adds a regression test for it, and replaces contradictory requirement notes with this consolidated audit. It does not claim that unimplemented architecture or unrun QA targets have been completed.

### Confirmed implementation vs acceptance

| Requirement | V11 source evidence | Verdict | Remaining work to meet the brief |
|---|---|---|---|
| FR1 ingestion | `UsageController`, `UsageService`, unique `idempotency_key`, 1,000-event request cap, late-event ledger | Partial | Make accepted event + late-adjustment ledger atomic; use PostgreSQL conflict-safe insert under concurrency; integrate pending late events into the next normal invoice; prove period-close boundary and 72-hour policy. |
| FR2 metrics | `MetricAggregation` supports SUM/COUNT/MAX/UNIQUE_COUNT/LATEST_VALUE and property filters | Partial | Persist metric definitions; use each configured aggregation/filter in billing, estimate and invoice lines. Current billing/estimate quantity path sums quantities for one plan metric. |
| FR3 pricing | `Plan.chargeDefinitionsJson`; `PricingService`; six configured strategies; currency on plan; allowance/minimum legacy calculation | Partial | Define and validate a stable public charge JSON schema; make allowance/minimum semantics apply predictably to composed charges; validate tier gaps/overlaps and currencies; test all boundaries. A new strategy currently requires registering it in the registry. |
| FR4 subscriptions | create/pause/resume/cancel, seats, trial end, scheduled plan change, immediate proration audit | Partial | Implement explicit lifecycle state machine; scheduled changes must align with billing period and proration ledger; automatically convert/expire trials; ensure paused/cancelled/past-due subscriptions cannot be billed incorrectly. |
| FR5 billing | draft invoice, finalise route, tax rate, invoice lines and source event IDs/count | Partial | Billing is a single transactional loop in `BillingService.run`, not Spring Batch. Implement chunk-oriented job, customer partitioning, job parameters, restart semantics, per-customer transaction boundaries, and a crash/restart integration test. Tax and invoice rendering visitors are not implemented as the stated pattern. |
| FR6 credits | prepaid credit account, top-up, credit note entity/API and credit application | Partial | Add auditable credit ledger entries, idempotent application, credit-note issuance/application/reversal rules, dispute/correction workflow, and automatic late-adjustment posting. |
| FR7 live spend | usage summary and estimate APIs; budget thresholds 50/80/100 and threshold deduplication | Partial | Cache/invalidate estimates and demonstrate refresh within 60 seconds; ensure estimate exactly matches the billing calculation (including seats, trial/proration, credits, tax, late adjustments); customer budget management and notification delivery need end-to-end tests. |
| FR8 dunning | persisted dunning case and day 1/3/7 retry state model; mock payment endpoints | Partial | Verify scheduled execution, idempotent retries, notifications at every step, payment recovery, and suspension/reactivation enforcement across subscription and API access. |
| FR9 webhooks | HMAC-SHA256, persisted delivery attempts, retry/backoff, manual replay | Partial | Harden SSRF against DNS rebinding and private/link-local IPv4/IPv6, pin/validate resolved destination at connection time, define delivery signing contract, add dead-letter handling and receiver integration tests. |
| FR10 plan versions | unique plan key/version; subscription plan ID; migration and simulation endpoints | Partial | Preserve immutable complete pricing/tax snapshots; simulator must select any historical version and replay the same source events; migration needs dry-run diff, validation and audit trail. |

## Quality attributes

| QA | Target | Current status | Evidence needed |
|---|---|---|---|
| QA1 | 1M events; 3% duplicates; 2% late; exact independent recomputation; zero double billing | Not demonstrated | Deterministic million-event fixture, independent oracle (not production pricing code), reconciliation by customer/period/event, late-adjustment inclusion, zero duplicate report and raw logs. |
| QA2 | 2,000 events/s, p95 ≤100 ms while billing | Not demonstrated | Run k6 against target PostgreSQL with concurrent billing, record host/DB configuration, throughput, p50/p95/p99, errors and raw output. Workload script alone is not proof. |
| QA3 | 10,000 customers in ≤10 minutes; kill/restart without duplicate invoices | Not met by current architecture | Implement partitioned restartable Spring Batch job, run 10k-customer benchmark, kill a job mid-chunk, restart same JobInstance, verify exactly one invoice per eligible customer. |
| QA4 | config-only new plan; new instructor-defined charge without modifying existing strategies | Partial | Demo persisted plan JSON using existing types, then add a separate strategy implementation via registry/DI without editing existing strategies; add boundary tests. The instructor's actual charge request is required. |
| QA5 | one-call invoice line traceability; estimate within 1% of final | Partial | Add invoice-line endpoint that returns source IDs/count + metric/aggregation + exact strategy/tier + allowance/credit/tax calculation; reconciliation test comparing same as-of event set and final invoice. |

## Spring Boot architecture focus

| Required focus | Current source state | Work still required |
|---|---|---|
| Spring Modulith modules | Package areas exist and `@Modulithic` is enabled; architecture test exists | Run `ApplicationModules.verify()` in CI; enforce allowed dependencies; use module events and publication registry for reliable cross-module delivery. |
| Spring Batch | Dependency is present; application disables automatic job launch | No actual billing Job/Step/Reader/Processor/Writer, chunking or partitioner. Implement and test these. |
| JPA/PostgreSQL | JPA entities/repositories and PostgreSQL compose service | Add Flyway complete baseline and incremental migrations; remove `ddl-auto:update` from normal deployment; add DB concurrency/locking tests. |
| Money | `shared/Money.java` exists | Verify all monetary paths use it consistently; no mixed currency; exact scale/rounding tests. |
| Optimistic locking | `Subscription` has `@Version` | Add versions to mutable billing/credit/plan records where needed and prove conflict handling. |
| Idempotency | Unique event key and invoice customer-period unique constraint | Use atomic database operations and end-to-end concurrency/restart tests; late-adjustment ledger insert must be atomic with event acceptance. |
| Decoupled ingestion | PostgreSQL event persistence and late-adjustment ledger | No Kafka or durable outbox worker demonstrated. Add transactional outbox + publisher/consumer (or Kafka) with retry and replay. |
| Caching | Redis dependency and service available in Compose | No proven live-estimate cache/invalidation contract or ≤1-minute freshness evidence. |
| Patterns | Strategy/Composite/Builder represented; webhook observer-like dispatch | Visitor, explicit State and Template Method are not integrated as specified. Add only where they clarify real workflows and test them. |

## Common course criteria C1–C12

| ID | Status | What remains |
|---|---|---|
| C1 | Partial / unverified | Clean-machine one-command Compose startup and 8 GiB host/resource verification. |
| C2 | Partial | Typed validated configuration, complete Flyway schema management, secrets management and secure production identity. |
| C3 | Partial | API contract tests, consistent validation/error schemas, resource-level tenant authorization for every endpoint, pagination coverage and production auth. |
| C4 | Partial | Structured JSON logs, metrics dashboards, tracing/exporter and operational evidence. Correlation ID filter exists. |
| C5 | Pending verification | Run Maven tests, frontend production build and CI; add integration/security tests. |
| C6 | Pending verification | Execute Modulith verification and enforce package boundaries. |
| C7 | Partial | Four ADRs, C4 diagrams, utility tree and mini-ATAM exist; review against actual implementation and instructor rubric. |
| C8 | Partial | Strategy/Composite/Builder in code; integrate and justify Visitor/State/Template Method/Observer or document deliberate alternatives with instructor approval. |
| C9 | Pending evidence | Raw QA1–QA5 output and reproducible environment report. |
| C10 | External | Cannot complete until instructor provides the change request. |
| C11 | Partial / external | Preserve real Git commit history and complete genuine AI/process log; ZIP alone does not prove this. |
| C12 | External | Complete and deliver live 15-minute defence. |

## V12 code change

- `UsageService.estimate()` previously called `PricingService.calculate(plan, usage)` which defaults the seat quantity to one. That caused per-seat estimates to be wrong for subscriptions with multiple seats.
- It now calls `PricingService.calculate(plan, usage, subscription.getSeatCount())`.
- Added `UsageEstimateSeatPricingTest` to verify a seven-seat subscription passes seven seats to pricing.

## Build and verification status

This environment does not have Maven or Docker available, so this delivery cannot truthfully claim Java compilation, JUnit execution, PostgreSQL integration, Compose startup, or k6 benchmarks passed. The frontend archive does not include a package-lock file, so use `npm install` (not `npm ci`) unless you first generate and commit a lockfile. Run these commands on a machine with JDK 21, Maven 3.9+, Node.js 20+, and Docker:

```bash
cd backend
mvn clean verify

cd ../frontend
npm ci
npm run build

cd ..
docker compose config
docker compose up --build
```

Then execute the procedures in `QUALITY-ACCEPTANCE-RUNBOOK.md`. Save raw logs and environment details under `docs/quality-evidence/`.

## Recommended implementation order

1. **P0 — Data correctness:** atomic event + late-adjustment persistence; integration of late adjustments into the next invoice; independent event-level reconciliation.
2. **P0 — Billing reliability:** Spring Batch chunk/partition job, restart-safe idempotent invoice writer, 10k customer crash/restart test.
3. **P0 — Database lifecycle:** complete Flyway baseline and versioned migrations; clean install and upgrade tests.
4. **P1 — Estimate parity:** common rating pipeline for live estimate, simulation and final invoice; seats, aggregation, tax, credits, proration and adjustments included.
5. **P1 — Architecture:** transactional outbox and Modulith publication registry; module verification; reliable notifications.
6. **P1 — Security:** production OIDC/JWT/session identity, full tenant authorization, webhook SSRF/DNS-rebinding defence.
7. **P2 — Lifecycle and evidence:** trial/dunning schedulers, state transitions, pattern integration, QA1–QA5 benchmarks and raw evidence.
8. **Course-dependent:** instructor's C10 change request and C12 live defence.

**Submission warning:** V12 is an improved and more accurately audited project archive, not a fully verified completion of every FR/QA/course criterion. Do not submit the QA targets as achieved until the required tests have actually passed.
