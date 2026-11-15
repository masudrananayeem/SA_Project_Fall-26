# Spring Batch billing

`POST /api/invoices/batch/run?period=YYYY-MM` launches `meterflowBillingJob`. The manager partitions distinct active customer IDs across up to eight worker partitions. Each worker reads its customer slice through an `ItemStreamReader`; the current read index is saved in the Spring Batch `ExecutionContext`. Workers write in chunks of 25 customers, so committed chunks are checkpointed and a failed job can resume with the same identifying job parameter (`period`). `BillingService.runCustomer` is transactional and the database unique constraint `(customer_id, period)` is the final duplicate-invoice barrier.

A restart is only considered proven after a test deliberately terminates the process during a multi-chunk run, restarts with the same `period`, and verifies one invoice maximum per customer/period and full customer coverage. The local verification runbook contains this test.
