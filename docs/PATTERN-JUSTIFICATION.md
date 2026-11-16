# Design patterns (C8)

| Pattern | Where | Why | Evidence |
|---|---|---|---|
| Strategy | `catalogue.charges.ChargeStrategy`, `BuiltInCharges.*` (flat fee, per unit, per seat, package, volume tier, graduated tier) | Each charge type is priced by its own class; a plan is only configuration. | `ChargeStrategiesTest`, `PriceQuoteTest` |
| Open/closed registry | `ChargeStrategyRegistry` + `CatalogueConfiguration` (adds every `ChargeStrategy` bean) | QA4: a new type is a new `@Component`; no existing class changes. | `ChargeRegistryExtensionTest` |
| Composite | `ChargeComponent`, `ConfiguredCharge` (leaf), `ChargeComposite` (group) | A plan is a tree of charges; pricing the root returns every line. | `PriceQuoteTest.configuredChargesProduceOneLinePerChargeWithRules` |
| Template Method | `invoicing.AbstractBillingRun.bill()` (final skeleton) with `InvoiceBillingRun` and `SimulationBillingRun` | Billing and the FR10 simulator share the exact same steps; only persistence/credit/late steps differ. | `BillingRunTest` |
| Builder | `InvoiceBuilder` -> `InvoiceDocument` | Lines are added step by step; build() computes subtotal and tax consistently. | `InvoiceDocumentTest` |
| Visitor | `BillingLine` (sealed: UsageCharge, Proration, LateUsage) + `BillingLineVisitor`: `TaxVisitor`, `InvoiceRenderers.Text/Csv`, `InvoiceLineMapper` | Tax, rendering and persistence vary independently of the line types. | `InvoiceDocumentTest`, `BillingRunTest.renderingAndTaxUseVisitors` |
| State | `subscriptions.SubscriptionState`, `payments.DunningState` | Legal transitions live in the state; illegal ones throw. | `SubscriptionLifecycleTest`, `DunningCaseTest` |
| Observer | Module events + `@ApplicationModuleListener` (`BillingEventNotifier`, `BudgetService.on`, `PaymentService.on`) | Publishers do not know their subscribers; delivery is reliable through the registry. | ADR-006 |
| Value Object | `shared.Money` | Amounts are rounded to the currency's minor unit and never mixed across currencies. | `InvoiceDocumentTest.moneyRejectsCurrencyMixing` |
