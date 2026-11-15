# 15-minute MeterFlow defence guide

This is a preparation plan, not evidence that a live defence has occurred.

| Time | Demonstration | What to explain |
|---|---|---|
| 0:00–1:00 | Problem and success criteria | Exactly-once billing intent, traceable invoices, configuration-driven pricing. |
| 1:00–3:00 | Architecture | Modular monolith boundaries, PostgreSQL persistence, asynchronous/outbox direction and trade-offs. |
| 3:00–5:00 | Ingest events | Batch cap, validation, idempotency key, database uniqueness and duplicate response. |
| 5:00–7:00 | Pricing | Existing persisted plan model versus strategy registry; explicitly identify any not-yet-integrated charge types. |
| 7:00–9:00 | Subscription and invoice | Subscription lifecycle, draft/finalise and invoice line explanation. |
| 9:00–11:00 | Quality evidence | Show actual test output and raw k6 report only; never present a target as achieved without a run. |
| 11:00–13:00 | ADR and patterns | Explain rejected alternatives, consequences and implemented patterns. |
| 13:00–15:00 | Change request and questions | Demonstrate the instructor's actual request once received; discuss limitations honestly. |

## Likely questions
- Why a modular monolith instead of microservices?
- How does the database unique constraint protect against concurrent duplicate ingestion?
- Why are billing periods represented as half-open intervals `[start, end)`?
- What happens if a billing run is interrupted? (Explain the V14 chunk-oriented partitioned Spring Batch job, checkpointed customer reader and unique invoice constraint. Do not claim QA3 passes until the 10,000-customer forced-restart integration test is executed.)
- How are historical prices protected from later plan edits?
- Which QA targets have measured evidence, and on what hardware?
