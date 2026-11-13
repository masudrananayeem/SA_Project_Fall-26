# ADR-001: Modular Monolith

- Status: Accepted
- Context: Course requires Spring Modulith and one-command local deployment.
- Options: Microservices; layered monolith; modular monolith.
- Decision: Spring Modulith modular monolith with business-capability packages.
- Consequences: Simple local transactions and deployment; module boundaries need automated verification; scale individual modules only if measurements justify it.
