# Mini-ATAM — MeterFlow

## Scope and assumptions
This lightweight Architecture Tradeoff Analysis Method (ATAM) evaluates the current modular-monolith design. It is an engineering analysis, not a substitute for a facilitated stakeholder workshop. Ratings below are qualitative hypotheses to validate during the course defence.

## Business drivers
1. Meter events must be accepted idempotently and billed exactly once.
2. Billing must remain explainable and recoverable after process failure.
3. New pricing models should be introduced without rewriting invoice orchestration.
4. Customer data must remain tenant-isolated.
5. A student/developer must be able to run the complete stack within an 8 GB machine budget.

## Architectural approaches
- Modular monolith with Spring Modulith boundaries for metering, catalogue, subscriptions, invoicing, payments and notifications.
- PostgreSQL as system of record; Redis for short-lived cache/coordination only.
- Unique idempotency and customer-period invoice constraints as correctness barriers.
- Strategy/Composite pricing, Builder invoice construction, State-based dunning, and outbox-style notification delivery.
- React/Vite frontend separated from the Spring Boot API.

## Quality attribute scenarios
| ID | Stimulus | Environment | Response | Measure / acceptance |
|---|---|---|---|---|
| S1 | Client retries an identical usage event | Concurrent ingestion | One event persists; retry is counted duplicate | Exactly one row per idempotency key |
| S2 | Billing process terminates midway | Billing period close | Restart resumes without duplicate invoice | One invoice per customer-period; no lost customer |
| S3 | 2,000 events arrive per second | Billing running concurrently | API remains responsive | p95 <= 100 ms (must be measured) |
| S4 | A new pricing model is configured | Runtime plan creation | Existing billing orchestration evaluates registered strategy | No edits to existing strategies for configuration-only plan |
| S5 | Customer requests invoice explanation | Invoice finalised | API returns rule, tax and source usage references | Every usage line has source event count and IDs |
| S6 | Customer A requests Customer B resource | Authenticated API | Request denied | HTTP 403/404 and no data disclosure |

## Trade-off points
| Decision | Benefit | Cost / risk | Mitigation |
|---|---|---|---|
| Modular monolith over microservices | Lower operational complexity; atomic billing transactions | Modules can become coupled | Modulith verification and package ownership |
| PostgreSQL as source of truth | Strong constraints and transactional ledger | Hot indexes / write contention at high ingestion | Batch inserts, partitioning and measured index tuning |
| Redis as auxiliary cache | Fast estimates and throttling potential | Cache invalidation and availability dependency | Never treat cache as billing ledger; rebuild from PostgreSQL |
| Synchronous invoice creation | Simple consistency for small workloads | Long transaction and poor restartability at 10k+ customers | Move to chunked Spring Batch job before QA3 acceptance |
| Basic Auth demo identity | Easy local evaluation | Not suitable for public multi-user production | Replace with OIDC/JWT and tenant-backed identity before public launch |

## Sensitivity points and risks
- Billing transaction duration and the number of events loaded per customer are highly sensitive to customer count and usage volume.
- The current billing loop is not a restartable chunk-oriented Spring Batch job; QA3 remains unproven.
- Schema evolution is not production-ready until a complete Flyway baseline and forward migrations are supplied.
- Webhook delivery requires SSRF controls (DNS pinning/revalidation, redirect blocking, egress policy) before public exposure.
- In-memory demo users are not a production identity store.

## Mini-ATAM conclusion
The modular-monolith approach is appropriate for the course-scale product and supports clear module ownership. The main architectural risks are operational recovery at scale, schema migration discipline, production identity, and outbound webhook security. These are release gates, not claimed solved outcomes.
