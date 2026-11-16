# Spring Batch billing

`POST /api/invoices/batch/run?period=YYYY-MM` launches `meterflowBillingJob`. The manager partitions distinct active customer IDs across up to eight worker partitions. Each worker reads its customer slice through an `ItemStreamReader`; the current read index is saved in the Spring Batch `ExecutionContext`. Workers write in chunks of 25 customers, so committed chunks are checkpointed and a failed job can resume with the same identifying job parameter (`period`). `BillingService.runCustomer` is transactional and the database unique constraint `(customer_id, period)` is the final duplicate-invoice barrier.

A restart is only considered proven after a test deliberately terminates the process during a multi-chunk run, restarts with the same `period`, and verifies one invoice maximum per customer/period and full customer coverage. The local verification runbook contains this test.

## V18 additions
- **Restart after a kill.** A killed JVM leaves the execution `STARTED`, which blocks a restart.
  `BillingJobLauncher` marks such executions (and their running step executions) `FAILED` at
  startup (`meterflow.billing.recover-stale-on-startup`, single-scheduler assumption) and on
  `POST /api/invoices/batch/recover`. Launching the same period again then resumes from the last
  committed chunk. A completed period returns `COMPLETED` without creating anything.
- **One bad customer does not stop the run.** `IllegalArgumentException`/`IllegalStateException`
  for a customer is skipped (limit 1,000) and logged by a `SkipListener`.
- **Per-customer work** is `InvoiceBillingRun.run` (Template Method): invoice, lines, ledger and
  proration updates, credit use and the `InvoiceFinalised` outbox event commit together.
- **Schedule.** 00:30 UTC on the 1st for the previous month (`meterflow.billing.cron`).

### QA3 procedure
1. Seed 10,000 customers with subscriptions and usage for the month (any script against `/api/usage/events:batch`).
2. `POST /api/invoices/batch/run?period=YYYY-MM`, and kill the backend (`docker kill`) after a few chunks.
3. Start the backend again (stale execution is recovered), repeat the same POST.
4. Check: `select count(*) from invoices where period='YYYY-MM'` = 10,000 and
   `select customer_id from invoices where period='YYYY-MM' group by customer_id having count(*)>1` returns no rows.
   Record total wall time from `BATCH_JOB_EXECUTION` start/end.
