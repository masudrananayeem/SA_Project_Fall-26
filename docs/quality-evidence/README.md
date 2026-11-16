# Quality evidence

| QA | Status | Evidence |
|---|---|---|
| QA1 accuracy | **Passed (harness, in-memory DB)** – 1,000,000 events, exact to the paisa, no event billed twice | `QA1-ACCURACY.md`, `raw/qa1-1m-events-2026-11-16.txt` |
| QA2 ingestion 2,000 ev/s, p95 ≤ 100 ms during billing | Pending – needs a running stack | `../../performance/ingest.js` |
| QA3 10,000 customers ≤ 10 min, kill/restart | Mechanism implemented and unit-tested; timed run pending | `../BATCH-BILLING.md`, `../QUALITY-ACCEPTANCE-RUNBOOK.md` |
| QA4 pricing agility | **Passed (unit test)** | `ChargeRegistryExtensionTest` in `raw/unit-tests-offline-2026-11-16.txt` |
| QA5 transparency | **Mechanism tested** (same quote for estimate and invoice; trace endpoint); live-stack sample pending | `LiveSpendServiceTest`, `BillingRunTest` |

Each run should record commit SHA, timestamp, environment, command and pass/fail. Nothing is marked
passed without raw output. See `../QUALITY-ACCEPTANCE-RUNBOOK.md`.
