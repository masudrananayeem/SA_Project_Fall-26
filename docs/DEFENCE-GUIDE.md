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
- What happens if a billing run is interrupted? (Do not claim restartability until Spring Batch job and integration test exist.)
- How are historical prices protected from later plan edits?
- Which QA targets have measured evidence, and on what hardware?

## V18 demo script (about 10 minutes)
1. **Plans (FR3/FR10/QA4):** create plan `starter` v2 with `PUT /api/catalogue/plans/starter`; show `GET /api/catalogue/plans/charge-types`; explain `ChargeRegistryExtensionTest` (new type = one new class).
2. **Subscription (FR4):** create a subscription (Swagger), then `change-plan` with `IMMEDIATE` and show the proration delta.
3. **Ingestion (FR1/FR2):** Usage Metering page → ingest 500 events twice (second time all duplicates).
4. **Live spend (FR7):** Live Spend page → set a budget below the estimate → 50/80/100 % notifications appear within a minute.
5. **Billing (FR5/FR6):** Invoices → Run billing → open the invoice: one line per charge, price rule, tax rule; click **Trace** (QA5) and **Render**.
6. **Simulator (FR10):** Invoices → Plan version simulator → compare v1 and v2 for the month.
7. **Dunning (FR8):** `POST /api/payments/mock/customers/customer/outcome?succeed=false`, then on a finalised invoice click *Simulate failed payment*; Dunning page → run retries "as of" +1, +3, +7, +14 days → RETRYING → PAST_DUE → SUSPENDED; set outcome back to succeed and click *Pay* → REACTIVATED.
8. **Webhooks (FR9):** register an endpoint (e.g. a local receiver), show deliveries and replay.
9. **Architecture:** module diagram (no cycles), ADR-005/006, `PATTERN-JUSTIFICATION.md`, QA1 evidence.
