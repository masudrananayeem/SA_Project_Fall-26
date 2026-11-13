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
| C1 | Technology, configuration, one-command local deployment, resource budget | Partial | Java 21, Spring Boot, PostgreSQL, Redis, Docker Compose and env templates are present. A clean-machine `docker compose up --build` and 8 GB resource check remain unverified. |
| C2 | Typed, validated configuration; profiles; no committed secrets | Partial | Env-based credentials and `.env.example` exist. Convert all settings to validated `@ConfigurationProperties`, add dev/test/prod profiles, and use a secret manager for deployed environments. |
| C3 | API validation, consistent errors, pagination, auth and authorization | Partial | Bean Validation, exception handler, HTTP Basic and method-security annotations on admin writes are present. Usage summaries/estimates and invoice/invoice-line reads now enforce customer username-to-customerId ownership; pagination and API contract tests remain. |
| C4 | Structured logs, metrics, traces, correlation IDs and health | Partial | Actuator health/metrics and UTC timestamps are configured. JSON logging, correlation propagation, tracing exporter and dashboard evidence remain. |
| C5 | Unit, integration, architecture, security and CI tests | Partial | Unit tests and Modulith/Testcontainers dependencies exist. Testcontainers tests, CI pipeline and passing test output are not yet verified. |
| C6 | Architecture verification and modular boundaries | Pending evidence | Spring Modulith module verification test exists; it must run successfully in a supported JDK/Maven environment. |
| C7 | C4 diagrams, ADRs, utility tree, six scenarios, mini-ATAM | Partial | Four ADRs and utility tree exist. See `ARCHITECTURE.md`; diagrams/scenarios and quantified trade-off analysis need instructor-aligned review. |
| C8 | Pattern selection and justification | Partial | Strategy/Composite/Builder are represented in code. Visitor, State, Template Method and Observer are not all integrated into end-to-end flows. |
| C9 | Quality-evidence report with raw reproducible measurements | Pending evidence | Evidence plan and k6 workload exist. No measured QA1–QA3/QA5 results are claimed. Record raw reports in `quality-evidence/`. |
| C10 | Instructor change request and response | External dependency | Await the instructor's actual change request. Do not fabricate one. Log request, design impact, implementation, tests and commit when received. |
| C11 | Git history, contribution/process record and AI usage log | Partial | `.gitignore` exists, but the delivered ZIP is not proof of repository history. Initialise Git locally and record genuine commits and AI use in `PROCESS-LOG.md`. |
| C12 | 15-minute live defence | External dependency | Prepare demo script and Q&A in `DEFENCE-GUIDE.md`; live performance can only be assessed during the defence. |

## B. Functional requirements FR1–FR10

| ID | Requirement | Updated status | Implemented / remaining |
|---|---|---|---|
| FR1 | Batch ingestion ≤1,000, idempotency, late events up to 72h after close | Partial | Batch validation, database unique idempotency key, duplicate-race handling and future timestamp tolerance exist. Late-arrival-after-period-close adjustment ledger is not implemented; current age check is relative to now. |
| FR2 | sum/count/max/unique/latest + property filters | Partial | Standalone aggregation utility and unit tests exist. Production query/metric definitions and aggregation-to-invoice wiring remain. |
| FR3 | flat, per-unit, graduated, volume, package, per-seat, allowance, minimum, BDT/USD | Partial | Strategy implementations and tests exist; persisted plan schema/API still exposes only flat + per-unit configuration. |
| FR4 | create, upgrade/downgrade, proration, pause/resume/cancel, trials | Partial | Create/pause/resume/cancel exist. Upgrade/downgrade proration, scheduled changes and trial lifecycle remain. |
| FR5 | draft/final invoice, explainable lines and configured tax | Partial | Draft invoice and explainable line are present. Tax is explicitly demo 0%; billing dates and restartable batch execution remain. |
| FR6 | prepaid credits, credit notes, late adjustments | Not implemented | Must add ledger with immutable entries, credit application and adjustment invoices. |
| FR7 | live estimate, budgets, 50/80/100 alerts, ≤1-minute refresh | Partial | Usage estimate endpoint and dashboard are present. Budget persistence, threshold crossing dedupe and notification delivery remain. |
| FR8 | mock payment failure, retries days 1/3/7, state transitions, notifications | Not implemented | Mock payment success only; dunning scheduler/state machine and notices remain. |
| FR9 | HMAC webhook, retries/backoff, delivery logs, manual replay | Partial | Endpoint registration and secret masking exist. Signed outbound delivery, delivery persistence, retry and replay remain. |
| FR10 | immutable plan versions, subscriptions pinned, migration, historical simulator | Partial | New plan version creation and subscription plan reference exist. Migration workflow and last-month simulation remain. |

## C. Quality attributes QA1–QA5

| ID | Acceptance target | Updated status | Required proof |
|---|---|---|---|
| QA1 | 1,000,000 events; 3% duplicates; 2% late; independent recomputation exact; zero double bill | Pending evidence | Deterministic fixture, independent oracle, reconciliation report and duplicate ledger. Late adjustments are not yet implemented. |
| QA2 | 2,000 events/sec, p95 ≤100 ms during billing | Pending evidence | k6 raw output, machine specs, database size, concurrent billing workload and p95 result. |
| QA3 | 10,000 customers ≤10 minutes; kill/restart with no duplicate invoice | Pending evidence | Spring Batch chunk/partition job and restart integration test are not implemented. |
| QA4 | Configuration-only plan; new charge type without changing existing strategy classes | Partial | Strategy interface and registry tests exist; configuration-driven persisted plan integration and instructor-defined extension demo remain. |
| QA5 | One-call invoice-line traceability; estimate within 1% of final | Partial | Invoice lines expose pricing explanation; event provenance and estimate-vs-final reconciliation test remain. |

## D. Code updates included in this revision

1. Added missing derived repository query methods used by `UsageService` so its current-month and metric-window calls have matching Spring Data repository declarations.
2. Enabled Spring method security and restricted plan creation, subscription mutations, invoice billing runs and invoice finalisation to the ADMIN role.
3. Corrected the frontend ingestion notice to display the backend's actual `rejected` response field.
4. Enforced customer ownership for usage summary/estimate and invoice/invoice-line reads; admin reads remain unrestricted.
4. Added this master criteria matrix and supporting architecture, evidence, process and defence documents.

## E. Release gate

Do not mark the project submission-ready until all remaining FRs are implemented, Maven tests and Docker build pass, the frontend production build passes, QA1–QA5 have reproducible evidence, and the external C10/C12 items are completed. This file intentionally reports honest status rather than claiming unrun tests passed.
