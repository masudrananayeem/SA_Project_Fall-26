# Design Pattern Justification

| Pattern | Requirement | Participants | Alternative rejected |
|---|---|---|---|
| Strategy | FR3/QA4 pricing | `Charge`, `PerUnitCharge`, future tier strategies | Large switch statement |
| Composite | FR3 multiple charge components | `CompositeCharge`, `Charge` | Fixed plan-specific pricing class |
| Visitor | FR5 tax and invoice rendering | Planned `InvoiceVisitor`, `TaxVisitor`, invoice line model | Conditional formatting/tax code in entity |
| State | FR4/FR8 lifecycle | Planned subscription and dunning state objects | String status checks scattered across services |
| Template Method | FR5 repeatable billing phases | Planned `AbstractBillingStep` | Duplicated billing orchestration |
| Builder | FR5 invoice assembly | `InvoiceBuilder` | Large positional constructor |
| Observer | FR9 notifications | Planned domain events and webhook publisher | Direct HTTP call inside billing transaction |
| Repository | Persistence isolation | Spring Data repositories | EntityManager use throughout controllers |
