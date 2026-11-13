# ADR-003: Outbox-Backed Event Delivery Direction

- Status: Proposed for full implementation
- Context: Ingestion must be decoupled from billing and inter-module events must survive crashes.
- Options: Synchronous calls only; Kafka; transactional outbox with polling publisher.
- Decision: Use transactional outbox first to avoid broker operational overhead; Kafka can be introduced if measured throughput requires it.
- Consequences: Atomic database write and publication intent; publisher, retry and deduplication worker must be implemented and benchmarked.
