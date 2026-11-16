# Requirements traceability (V18)

| Req | Main source | Tests |
|---|---|---|
| FR1 ingestion | `metering/UsageController`, `UsageService`, `JdbcUsageEventStore`, `LateUsageAdjustment` | `UsageServiceIngestionTest`, `Qa1AccuracyTest`, `PostgresBillingIntegrationTest` |
| FR2 billable metrics | `catalogue/Plan.defineMetric`, `metering/BillableUsageCalculator`, `metering/aggregation/MetricAggregation` | `MetricAggregationTest`, `MetricAggregationEndpointLogicTest`, `BillableMetricBillingTest` |
| FR3 pricing | `catalogue/charges/*` (Strategy, registry, Composite), `PricingService`, `PriceQuote` | `ChargeStrategiesTest`, `PriceQuoteTest`, `PricingServiceTest` |
| FR4 subscriptions | `subscriptions/SubscriptionState`, `Subscription`, `PlanChangeService`, `SubscriptionLifecycleService` | `SubscriptionLifecycleTest`, `BillingRunTest.prorationIsPosted…` |
| FR5 billing run | `invoicing/AbstractBillingRun`, `InvoiceBillingRun`, `InvoiceBuilder`, `TaxVisitor`, `batch/*` | `BillingRunTest`, `InvoiceDocumentTest`, `PartitionCustomerReaderTest` |
| FR6 credits, corrections | `credits/CreditService`, `AbstractBillingRun.addLateUsage` | `BillingRunTest.lateEvents…`, `CreditAccountTest`, `Qa1AccuracyTest` |
| FR7 live spend, budgets | `metering/LiveSpendService`, `budgets/BudgetService`, `shared/CacheConfig`, frontend `LiveSpendPage` | `LiveSpendServiceTest`, `BudgetThresholdTest` |
| FR8 dunning | `payments/DunningState`, `DunningCase`, `PaymentService`, `MockPaymentProvider` | `DunningCaseTest` |
| FR9 webhooks | `notifications/WebhookDeliveryService`, `BillingEventNotifier`, `WebhookController` | (needs a receiver; manual) |
| FR10 plan versions | `catalogue/CatalogueController` (PUT = new version), `subscriptions/PlanChangeService.migrate`, `invoicing/SimulationBillingRun` | `BillingRunTest.simulator…` |
| QA1 | whole ingest → bill path | `Qa1AccuracyTest` (1M run in `quality-evidence/`) |
| QA2 | `JdbcUsageEventStore`, async module listeners | `performance/ingest.js` (to run) |
| QA3 | `BillingBatchConfiguration`, `BillingJobLauncher` | `PartitionCustomerReaderTest`, `BillingRunTest` (idempotency); timed run pending |
| QA4 | `ChargeStrategyRegistry`, `CatalogueConfiguration` | `ChargeRegistryExtensionTest` |
| QA5 | `BillingService.trace`, `LiveSpendService` | `LiveSpendServiceTest`, `BillingRunTest` |
