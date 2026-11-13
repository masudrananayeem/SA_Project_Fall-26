# ADR-002: PostgreSQL as Billing Source of Truth

- Status: Accepted
- Context: Billing requires durable unique constraints, transactional invoice creation and precise decimal arithmetic.
- Options: PostgreSQL; document database; in-memory store.
- Decision: PostgreSQL with JPA and DECIMAL columns.
- Consequences: Strong constraints and transactions; aggregation/index design and partitioning need load testing at one million events.
