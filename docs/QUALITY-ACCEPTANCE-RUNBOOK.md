# Quality Acceptance Runbook

This runbook defines how to collect evidence. A command or script is not evidence of passing; attach the raw output, environment details and commit SHA after executing it.

## Common environment record
Record: commit SHA, date/time (UTC), OS, CPU model/core count, RAM, Java version, Maven version, Docker version, PostgreSQL version, Redis version, dataset/event generator seed, DB row counts, and exact command.

## QA1 — 1,000,000 usage events
1. Prepare a clean PostgreSQL volume and a fixed seed.
2. Generate one million logical events with 3% duplicate submissions and 2% late submissions. Keep a separate independent oracle file containing expected unique accepted event IDs and expected billable totals.
3. Submit in batches of at most 1,000.
4. Query persisted event IDs and independently recompute each customer/metric/period total from the oracle.
5. Confirm unique idempotency keys and zero double-billing; report accepted, duplicate, rejected-late, and adjustment counts.
6. Store generator input, API response log, SQL reconciliation output and summary in `docs/quality-evidence/qa1/`.

## QA2 — throughput and latency
Run `k6 run performance/ingest.js` against the target deployment. The script submits 10 events per request at 200 requests/sec (2,000 submitted events/sec) and sets p95/error-rate thresholds. Run a separate billing job concurrently to meet the requirement for the required test duration, while a billing run is active. Preserve the k6 JSON output and report p50/p90/p95/p99, error rate, accepted events/sec, DB CPU, memory and connection pool saturation. Pass only if p95 <= 100 ms and the agreed error budget is met.

## QA3 — 10,000 customer billing and recovery
1. Seed 10,000 active subscriptions and deterministic usage.
2. Start billing for one period and record elapsed wall-clock time.
3. Terminate the backend during the run, restart it, and resume the same period.
4. Reconcile customer-period invoices and invoice totals against an independent oracle.
5. Pass only if completion <= 10 minutes, every eligible customer has exactly one invoice, and no customer is silently skipped.
V15 now contains customer partitions, a checkpointed `ItemStreamReader`, chunk size 25 and invoice uniqueness. The 10,000-customer timing and forced-kill/restart acceptance still must be executed and retained as raw evidence.

## QA4 — extensibility
Create a new plan using only existing configured charge definitions and show its price calculation. Then implement one genuinely new `ChargeStrategy` in a separate class, register it, and run its boundary tests. Record which files changed. Configuration-only plan creation and code-level strategy extension are distinct acceptance demonstrations.

## QA5 — explainability and estimate accuracy
For each finalised invoice, retrieve invoice lines and their source event IDs/count. Independently recompute quantity and amount from those event IDs. Compare estimate and final invoice at the same as-of cutoff; calculate absolute percentage difference. Pass only if all required cases are within 1%.

## Evidence integrity
Never hand-edit a passing result. Store raw tool output, command, environment and commit SHA. Redact secrets and personal data. A failed run is useful evidence: record it, diagnose it, and rerun after a committed fix.
