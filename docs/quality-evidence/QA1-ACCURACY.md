# QA1 accuracy evidence

**Target:** 1,000,000 events with 3 % duplicates and 2 % late arrivals produce invoices that match
an independent recomputation to the last paisa, with zero events billed twice.

## Harness
`backend/src/test/java/com/meterflow/qa/Qa1AccuracyTest.java` (size via `-Dqa1.events`, default 100,000).

- 1,000,000 submitted events: 970,000 unique, 30,000 re-submitted duplicates (same key and payload),
  20,000 late (after the September close; 10,000 before the September billing run, 10,000 after it,
  within 72 hours). 1,000 customers on four plan shapes (legacy flat + per unit + minimum,
  graduated tiers, volume tiers + seats, flat + package with free units), BDT and USD.
  Integer and fractional quantities. Fixed random seed.
- Events pass through the production `UsageService.ingest` (late detection, ledger), and
  `InvoiceBillingRun` bills September and then October (which carries late-usage lines).
- The oracle in the test prices each customer's de-duplicated September usage with its own code
  (it does not call `PricingService` or the charge strategies).

## Checks
1. accepted events = unique events, duplicates ignored = 30,000;
2. for every customer, September invoice subtotal + September late-usage lines on the October
   invoice = oracle price of all unique September events (exact `BigDecimal` comparison);
3. the union of source event ids on all lines has exactly 970,000 ids and no id appears twice;
4. no late-ledger entry is left pending.

## Result (2026-11-16)
Passed three times with identical totals (raw output: `raw/qa1-1m-events-2026-11-16.txt`):

```
QA1 submitted=1000000 unique=970000 duplicates=30000 late=20000 customers=1000 invoices=2000 septemberTotalCharges=709364.54 elapsedMs=4958
PASS Qa1AccuracyTest.invoicesMatchIndependentRecomputationAndNoEventIsBilledTwice
```

Environment: Intel Xeon 2.1 GHz, 2 vCPU, 7 GiB, OpenJDK 21.0.12.

## Scope and limits
- Repositories were in-memory test doubles with the same uniqueness rule as the database; the
  production code was compiled against API stubs because Maven Central was not reachable.
- The database path (`ON CONFLICT DO NOTHING`, unique invoice constraint) is covered by
  `PostgresBillingIntegrationTest` (`mvn verify -Pintegration`, needs Docker) – not run here.
- CI runs the same harness at 1,000,000 events with real libraries (`.github/workflows/ci.yml`).
