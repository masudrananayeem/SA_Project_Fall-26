# Utility Tree and Mini-ATAM

| Quality attribute | Scenario | Measure | Sensitivity point | Trade-off |
|---|---|---|---|---|
| Accuracy | Duplicate event arrives twice | Count once | Unique idempotency key constraint | DB index write cost |
| Accuracy | Late event arrives after close | Adjustment on next bill | Period-close policy | More complex ledger |
| Performance | Ingestion while billing runs | p95 <=100ms at 2k events/s | DB connection pool and indexes | More resources |
| Recoverability | Billing worker killed mid-run | Restart without duplicate invoice | Checkpoint + unique invoice key | Batch metadata overhead |
| Transparency | Customer asks why charged | One-call line trace | Event provenance retention | Storage growth |
| Availability | Redis unavailable | Billing remains correct | Cache-aside only | Lower read throughput |
| Modifiability | New charge type requested | Add implementation without editing existing strategies | Charge registry/extension point | More abstractions |

### Mini-ATAM trade-offs
- Unique constraints and transactions improve correctness but increase contention under concurrent ingestion.
- Cache improves live-estimate latency but must be invalidated on event and subscription changes.
- Outbox gives reliable publication but introduces eventual consistency and retry operations.
- Partitioned batch improves throughput but needs deterministic partitioning and shared idempotency barriers.
