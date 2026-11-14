# Requirements Traceability Matrix

This matrix distinguishes implemented foundation from work still required before claiming full assignment compliance.

| Requirement | Current implementation | Remaining verification / extension |
|---|---|---|
| FR1 | Batch API, max 1,000, unique idempotency key, duplicate skip | 72-hour closed-period adjustment queue; race-safe insert conflict handling; late-event policy tests |
| FR2 | New `MetricAggregation` engine supports sum/count/maximum/unique_count/latest_value and exact property filters; Usage summary still uses SUM for billable quantity | Persisted metric definitions, event-time ordering for latest-value and JSON-path filter DSL remain to be connected |
| FR3 | Existing persisted plan supports flat/per-unit/free allowance/minimum; new extensible charge registry supports flat_fee, per_unit, graduated_tier, volume_tier, package and per_seat | ChargeDefinition registry is a pricing-engine extension/test harness; persist arbitrary charge definitions on plan, connect plan API and tax visitor before end-to-end claim |
| FR4 | Create/pause/resume/cancel subscription | Mid-cycle upgrade/downgrade, proration, scheduled changes, trial scheduler and lifecycle state machine |
| FR5 | Draft invoice, finalise endpoint, invoice lines with price/tax explanation | Spring Batch chunk/partition job, tax configuration, per-customer billing dates, crash/restart proof |
| FR6 | No full implementation | Credit ledger, credit notes, balance application, adjustment line workflow |
| FR7 | Estimate endpoint now rates usage against an active subscription plan; summary endpoint returns current-month usage | Redis cache invalidation, budget API and 50/80/100 alert delivery remain |
| FR8 | Mock payment success endpoint | Failed-payment simulator, day 1/3/7 retry scheduler, past-due/suspended states and notifications |
| FR9 | Webhook endpoint registration | Event delivery worker, HMAC-SHA256 signatures, exponential retry, delivery log and manual replay |
| FR10 | Immutable plan version rows | Subscription migration and historical invoice simulator |
| QA1 | Idempotency foundation | 1M event generator, independent recomputation, late/duplicate test and zero-duplicate evidence |
| QA2 | No measured result included | k6 2,000 events/sec test; p95 <=100ms evidence |
| QA3 | Unique customer-period invoice constraint | Spring Batch restart/partition implementation and 10k customer benchmark |
| QA4 | Strategy registry includes six required charge types; new strategy can be registered without changing existing strategies | Persisted configuration-only plan rollout and instructor-specified new charge extension test remain |
| QA5 | Invoice lines now retain source event IDs as JSON and source-event count; a unit test covers persistence of this provenance contract | Verify schema migration in production and add estimate-vs-final reconciliation acceptance test within 1% |

## Important
The project source is an executable foundation and representative implementation. Do not state that unmet rows are complete. Add tests, raw output and charts before submitting a success claim.


## Version 2 improvement notes
- Added a pluggable charge strategy registry and unit tests for flat, per-unit, graduated, volume, package and per-seat pricing calculations.
- Added an aggregation engine with filters and tests for all five requested aggregation operations.
- Connected live estimate calculation to the active subscription and its immutable plan version.
- These additions improve the foundation but do not make every FR/QA complete. See each remaining item above.


## V2.1 hardening update

| Area | Improvement | Evidence / limitation |
|---|---|---|
| Event ingestion | Enforces 72-hour late-arrival window, rejects timestamps more than 5 minutes in the future, reports per-event rejection reasons, and handles idempotency-key races using the database unique constraint | `UsageService`, `UsageEvent` unique constraint; concurrent integration test still required |
| Time boundaries | Usage queries now use half-open intervals (`>= from`, `< to`) to prevent boundary events being counted in adjacent billing periods | `UsageEventRepository`, `UsageService` |
| API validation | Bounds customer, metric and idempotency key lengths and quantity precision | `UsageController.EventInput` |
| Concurrency | API returns HTTP 409 for uniqueness and optimistic-lock conflicts | `ApiExceptionHandler` |
| Frontend credentials | Removes the hard-coded default password; credentials can be supplied through local `.env` or browser local storage | `frontend/src/api.ts`, `frontend/.env.example`; Basic Auth is still demo-grade and should be replaced with token/session auth for production |

The V2.1 changes improve ingestion correctness and operational feedback, but do not by themselves satisfy the 2,000 events/sec or 1M-event acceptance targets. Those require repeatable load tests against the target PostgreSQL infrastructure.


## V10 implementation update (2026-11-15)
- QA5 improvement: invoice lines now persist `source_event_ids_json` and `source_event_count`; the billing path supplies the IDs of usage events used for the billable metric. A focused unit test covers the value-object/entity contract. This improves traceability but does not prove the 1% estimate reconciliation target.
- C4 improvement: `CorrelationIdFilter` propagates a validated `X-Correlation-ID` header and places it in SLF4J MDC for request log correlation. A JSON logging encoder/exporter and distributed tracing backend are not configured.
- C7 improvement: added `MINI-ATAM.md` with six quality attribute scenarios, trade-offs, sensitivity points and explicit risk analysis.
- C9 improvement: added `QUALITY-ACCEPTANCE-RUNBOOK.md` with reproducible QA1–QA5 procedures and evidence integrity rules. No results are fabricated; all QA performance targets remain unverified.
- C10/C12 remain external course activities and cannot be completed by code generation.
- Critical release gaps remain: restart-safe chunk-oriented Spring Batch billing, complete Flyway baseline/migrations, production identity (OIDC/JWT), webhook SSRF hardening, and real benchmark execution.
