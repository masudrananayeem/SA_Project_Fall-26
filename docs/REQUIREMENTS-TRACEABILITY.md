# Requirements Traceability Matrix

This matrix distinguishes implemented foundation from work still required before claiming full assignment compliance.

| Requirement | Current implementation | Remaining verification / extension |
|---|---|---|
| FR1 | Batch API max 1,000; PostgreSQL `ON CONFLICT DO NOTHING` makes idempotency race-safe; events older than 72h and timestamps over 5 minutes ahead are rejected with per-event reasons | Late-event adjustment/credit workflow and concurrent integration tests remain |
| FR2 | New `MetricAggregation` engine supports sum/count/maximum/unique_count/latest_value and exact property filters; Usage summary still uses SUM for billable quantity | Persisted metric definitions, event-time ordering for latest-value and JSON-path filter DSL remain to be connected |
| FR3 | Existing persisted plan supports flat/per-unit/free allowance/minimum; new extensible charge registry supports flat_fee, per_unit, graduated_tier, volume_tier, package and per_seat | ChargeDefinition registry is a pricing-engine extension/test harness; persist arbitrary charge definitions on plan, connect plan API and tax visitor before end-to-end claim |
| FR4 | Create/pause/resume/cancel subscription | Mid-cycle upgrade/downgrade, proration, scheduled changes, trial scheduler and lifecycle state machine |
| FR5 | Draft invoice, finalise endpoint, invoice lines with price/tax explanation | Spring Batch chunk/partition job, tax configuration, per-customer billing dates, crash/restart proof |
| FR6 | No full implementation | Credit ledger, credit notes, balance application, adjustment line workflow |
| FR7 | Estimate endpoint rates usage against the active subscription plan; summary endpoint returns current-month usage; dashboard displays live event count and estimated spend | Redis cache invalidation, budget API and 50/80/100 alert delivery remain |
| FR8 | Mock payment success endpoint | Failed-payment simulator, day 1/3/7 retry scheduler, past-due/suspended states and notifications |
| FR9 | Webhook endpoint registration | Event delivery worker, HMAC-SHA256 signatures, exponential retry, delivery log and manual replay |
| FR10 | Immutable plan version rows | Subscription migration and historical invoice simulator |
| QA1 | Database-enforced idempotency and explicit 72-hour late-event policy | 1M event generator, independent recomputation, concurrent duplicate test and zero-double-bill evidence |
| QA2 | No measured result included | k6 2,000 events/sec test; p95 <=100ms evidence |
| QA3 | Unique customer-period invoice constraint | Spring Batch restart/partition implementation and 10k customer benchmark |
| QA4 | Strategy registry includes six required charge types; new strategy can be registered without changing existing strategies | Persisted configuration-only plan rollout and instructor-specified new charge extension test remain |
| QA5 | Invoice line explanation endpoint | Include event provenance IDs/count and compare estimate to final invoice within 1% |

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
| Event ingestion | Enforces 72-hour late-arrival window, rejects timestamps more than 5 minutes in the future, reports per-event rejection reasons, and handles idempotency-key races using PostgreSQL `ON CONFLICT DO NOTHING` | `UsageService`, `UsageEvent` unique constraint; concurrent integration test still required |
| Time boundaries | Usage and billing queries use half-open intervals (`>= from`, `< to`) to prevent boundary events being counted in adjacent billing periods | `UsageEventRepository`, `UsageService`, `BillingService` |
| API validation | Bounds customer, metric and idempotency key lengths and quantity precision | `UsageController.EventInput` |
| Concurrency | API returns HTTP 409 for persistence uniqueness and optimistic-lock conflicts | `ApiExceptionHandler`; event-ingestion duplicates are counted instead of surfaced as errors |
| Frontend credentials | Removes the baked-in admin/password fallback; credentials must be supplied through local demo environment values or browser local storage | `frontend/src/api.ts`, `frontend/.env.example`; never put production secrets in `VITE_*` variables |

The V2.1 changes improve ingestion correctness and operational feedback, but do not by themselves satisfy the 2,000 events/sec or 1M-event acceptance targets. Those require repeatable load tests against the target PostgreSQL infrastructure.
