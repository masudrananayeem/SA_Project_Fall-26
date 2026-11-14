# CSE 444 — MeterFlow Master Criteria Audit & Update

**Student:** Masud Rana Nayeem  
**Student ID:** 0242310005101849  
**Section:** 64-K (188106)  
**Project:** Project 2 — MeterFlow  
**Audit basis:** `CSE444_Course_Projects.docx` and `CSE444_Project_Assignments_Section_64K_188106.docx` supplied for this assignment.

> Status vocabulary: **Implemented** = present in source; **Partial** = some code exists but acceptance condition is not met; **Pending evidence** = must be executed and raw output retained; **External dependency** = instructor input or live defence is required. Documentation alone is not proof of implementation.

## A. Common course criteria C1–C12

| ID | Expected deliverable | Updated status | Evidence / remaining work |
|---|---|---|---|
| C1 | Technology, configuration, one-command local deployment, resource budget | Improved / pending verification | Compose has PostgreSQL+Redis+backend+frontend, persistent `pgdata`, health-gated dependencies and 5 GiB aggregate memory ceilings. Clean-machine startup and 8 GiB host verification remain unrun. See `DEPLOYMENT.md`. |
| C2 | Typed, validated configuration; profiles; no committed secrets | Partial | Dev/test/prod profile files now exist; prod requires environment secrets and uses Hibernate `validate`. Typed `@ConfigurationProperties`, secret-manager integration and complete migration provisioning remain. |
| C3 | API validation, consistent errors, pagination, auth and authorization | Improved / partial | Subscription lists and plan-change history now enforce customer scope; invoices, usage, credits and budgets have ownership checks. Admin operations remain role-protected. Pagination, persistent identity, API contract tests and webhook tenant self-service remain. |
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
| FR1 | Batch ingestion ≤1,000, idempotency, late events up to 72h after close | Partial | Batch validation, unique idempotency key, period-close-based 72h acceptance and late-usage adjustment ledger exist. Automatic adjustment-to-next-invoice workflow and QA1-scale proof remain. |
| FR2 | sum/count/max/unique/latest + property filters | Partial | Standalone aggregation utility and unit tests exist. Production query/metric definitions and aggregation-to-invoice wiring remain. |
| FR3 | flat, per-unit, graduated, volume, package, per-seat, allowance, minimum, BDT/USD | Partial | Six strategy types are persisted/evaluated and subscription seats are passed to pricing. Mixed-charge allowance/minimum composition and exhaustive currency/tier boundary tests remain. |
| FR4 | create, upgrade/downgrade, proration, pause/resume/cancel, trials | Partial | Trial end, seat count, scheduled plan changes, immediate prorated plan changes and history exist. Trial conversion/expiry worker and scheduled change billing lifecycle remain. |
| FR5 | draft/final invoice, explainable lines and configured tax | Partial | Draft/finalise, pricing lines and plan tax rate exist. Restartable Spring Batch billing, event-level provenance is now stored on invoice lines; estimate-vs-final reconciliation remains. |
| FR6 | prepaid credits, credit notes, late adjustments | Partial | Prepaid balance, top-up, credit notes, invoice credit application and late-usage adjustment ledger exist. Refund/reversal/dispute lifecycle and automatic late-adjustment invoice integration remain. |
| FR7 | live estimate, budgets, 50/80/100 alerts, ≤1-minute refresh | Partial | Budget persistence, threshold dedupe and webhook alerts implemented; email/SMS and independent refresh worker remain. |
| FR8 | mock payment failure, retries days 1/3/7, state transitions, notifications | Partial | Persisted dunning case, day 1/3/7 schedule, state transitions and webhook notices exist. Real provider adapter, scheduler verification and subscription/access suspension propagation remain. |
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
