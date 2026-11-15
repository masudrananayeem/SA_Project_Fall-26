# Requirements Traceability Matrix — V12

The detailed, authoritative comparison against the supplied Project 2 brief is
[`V12-ASSIGNMENT-COMPLIANCE-AUDIT.md`](V12-ASSIGNMENT-COMPLIANCE-AUDIT.md).
This file is retained as a quick implementation index. Status means source
presence, not acceptance-test success.

| Requirement | Source locations | Status |
|---|---|---|
| FR1 | `metering/UsageController`, `UsageService`, `LateUsageAdjustment` | Partial: late adjustments are recorded but not automatically included in the next regular invoice; atomicity and QA1 proof remain. |
| FR2 | `metering/aggregation/MetricAggregation` | Partial: aggregation utility exists; configured definitions are not wired end-to-end into billing. |
| FR3 | `catalogue/Plan`, `PricingService`, `catalogue/charges` | Partial: six charge types are configurable in plan JSON; allowance/minimum composition and strategy extension acceptance remain. |
| FR4 | `subscriptions/Subscription`, `SubscriptionLifecycleService` | Partial: basic lifecycle, trial date, seats, scheduled change and proration audit exist; automatic lifecycle and billing integration remain. |
| FR5 | `invoicing/BillingService`, `InvoiceBuilder`, `InvoiceLine` | Partial: invoice workflow exists; billing is not a restartable Spring Batch job. |
| FR6 | `credits/CreditService`, `CreditAccount`, `CreditNote` | Partial: prepaid credit and credit-note foundation exists; full correction/reversal and late adjustment integration remain. |
| FR7 | `metering/UsageService`, `budgets/BudgetService` | Partial: estimate and threshold logic exist; cache freshness and final-invoice parity are unproven. |
| FR8 | `payments/DunningService`, `DunningCase` | Partial: retry state model exists; scheduler, notifications and enforcement require integration tests. |
| FR9 | `notifications/WebhookDeliveryService` | Partial: HMAC, retry and replay foundation exists; SSRF hardening and end-to-end receiver tests remain. |
| FR10 | `catalogue/Plan`, `subscriptions/SubscriptionLifecycleService`, `invoicing/BillingService` | Partial: version records, migration and simulation exist; immutable snapshots and arbitrary historical replay remain. |
| QA1 | ingestion + adjustment ledger | Pending independent 1M-event reconciliation and zero-duplicate evidence. |
| QA2 | `performance/ingest.js` | Pending actual p95 ≤100 ms benchmark under concurrent billing. |
| QA3 | invoice unique constraint | Not met until restartable partitioned Spring Batch and 10k-customer kill/restart test exist. |
| QA4 | `catalogue/charges` | Partial: configured existing types exist; instructor-defined new strategy demonstration remains. |
| QA5 | `InvoiceLine` provenance | Partial: source IDs/count stored; one-call full pricing explanation and ≤1% estimate parity test remain. |

## V12 correction
`UsageService.estimate()` now passes `subscription.getSeatCount()` to the
pricing service. `UsageEstimateSeatPricingTest` covers this regression.

Do not use older historical status notes in this repository as evidence that
a requirement is fully accepted. Use the V12 audit as the current source of
truth and record actual test output in `docs/quality-evidence/`.
