# Requirements Traceability Matrix

This matrix distinguishes implemented foundation from work still required before claiming full assignment compliance.

| Requirement | Current implementation | Remaining verification / extension |
|---|---|---|
| FR1 | Batch API, max 1,000, unique idempotency key, duplicate skip | 72-hour closed-period adjustment queue; race-safe insert conflict handling; late-event policy tests |
| FR2 | Metric sum aggregation foundation | Configurable aggregation definitions: count/max/unique/latest; JSON property filters |
| FR3 | Flat + per-unit, free allowance, minimum commitment; versioned plans | Graduated/volume tiers, package/1,000, per-seat, tax visitor, currency validation per subscription |
| FR4 | Create/pause/resume/cancel subscription | Mid-cycle upgrade/downgrade, proration, scheduled changes, trial scheduler and lifecycle state machine |
| FR5 | Draft invoice, finalise endpoint, invoice lines with price/tax explanation | Spring Batch chunk/partition job, tax configuration, per-customer billing dates, crash/restart proof |
| FR6 | No full implementation | Credit ledger, credit notes, balance application, adjustment line workflow |
| FR7 | Usage summary and estimate endpoint | Plan-rated estimate, Redis cache invalidation, budget API and 50/80/100 alerts |
| FR8 | Mock payment success endpoint | Failed-payment simulator, day 1/3/7 retry scheduler, past-due/suspended states and notifications |
| FR9 | Webhook endpoint registration | Event delivery worker, HMAC-SHA256 signatures, exponential retry, delivery log and manual replay |
| FR10 | Immutable plan version rows | Subscription migration and historical invoice simulator |
| QA1 | Idempotency foundation | 1M event generator, independent recomputation, late/duplicate test and zero-duplicate evidence |
| QA2 | No measured result included | k6 2,000 events/sec test; p95 <=100ms evidence |
| QA3 | Unique customer-period invoice constraint | Spring Batch restart/partition implementation and 10k customer benchmark |
| QA4 | Strategy + Composite charge foundation | Add all required charge types and instructor change-request extension test |
| QA5 | Invoice line explanation endpoint | Include event provenance IDs/count and compare estimate to final invoice within 1% |

## Important
The project source is an executable foundation and representative implementation. Do not state that unmet rows are complete. Add tests, raw output and charts before submitting a success claim.
