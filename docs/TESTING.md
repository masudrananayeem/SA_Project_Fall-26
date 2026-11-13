# Testing and evidence plan

## Unit/integration
```bash
cd backend && mvn test
```

Testcontainers is included for PostgreSQL integration tests. Add repository/service integration tests for idempotency races, period uniqueness, rollback and late-event handling.

## Load testing
Use `performance/ingest.js` with k6:
```bash
k6 run performance/ingest.js
```
Run against a warmed environment. Save raw output under `evidence/qa2/` and record machine, Docker limits, dataset and commit hash. This script is a starting workload, not proof of the target.

## Required evidence
- QA1: deterministic generated 1M-event dataset, 3% duplicate, 2% late, independent recomputation and duplicate invoice/event audit.
- QA2: k6 throughput and p95 with billing running concurrently.
- QA3: 10k customers, kill process at checkpoint, restart and reconcile invoice count.
- QA4: configuration-only new plan test and instructor-specified new charge type extension test.
- QA5: trace endpoint output and estimate-vs-final comparison report.

## V2.1 ingestion regression tests

`UsageServiceIngestionTest` covers three critical ingestion behaviours:

1. Events older than 72 hours are rejected and never persisted.
2. Existing idempotency keys are counted as duplicates while new recent events are accepted.
3. A database uniqueness collision caused by concurrent requests is treated as a duplicate rather than failing the entire request.

Run with `cd backend && mvn test`. The test suite has been added but could not be executed in the current authoring environment because Maven is not installed. Run the full suite before treating the build as verified.
