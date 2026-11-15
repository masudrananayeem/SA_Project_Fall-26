# MeterFlow V14 changelog

V14 is an additive upgrade over V13. Existing project modules, React UI, local Docker Compose setup, Cloudflare Worker/Container gateway, Vercel configuration, and earlier documentation are retained.

## Implemented in source

- Added a partitioned Spring Batch billing job: one partition per distinct active customer, with a transactional customer-period billing worker.
- Added ADMIN-only `POST /api/invoices/batch/run?period=YYYY-MM`.
- Kept the legacy synchronous `POST /api/invoices/run` route for compatibility and small demonstrations.
- Added `GET /api/usage/aggregate` for sum, count, maximum, unique count and latest value, with exact event-property filters and a bounded date range.
- Corrected `latest_value` to use the newest event timestamp instead of relying on database result ordering.
- Added aggregation tests and a service-level test for exact property filtering.
- Updated the React Usage Metering page with aggregation controls and changed its monthly billing action to call the partitioned batch endpoint.
- Changed the dashboard's default demo customer ID to `customer` to match the local demo customer account and scoped invoice loading to that customer.
- Added Windows PowerShell prerequisite, local-run and stop scripts.
- Updated API, local test, deployment and criteria documentation.

## Verification limits

- Source-level changes are present, but Maven/JDK 21/Docker were unavailable in the build environment; Java compilation and JUnit execution are not claimed.
- Frontend npm production build and Cloudflare/Vercel deployment were not executed.
- The partitioned batch job must still pass a real kill/restart test at 10,000 customers before QA3 can be marked passed.
- A complete Flyway baseline for every MeterFlow domain table remains outstanding. The production profile's Hibernate `validate` mode therefore requires the domain schema to be provisioned before deployment. Spring Batch metadata schema initialization is separate and does not replace this baseline.
- QA1, QA2, QA3 and QA5 remain pending raw evidence. Instructor-specific C10 and live defence C12 remain external tasks.

## Batch restart note

The Spring Batch job uses `period` as its identifying job parameter. A failed execution can be restarted with the same parameter value after the failure is resolved. A completed job instance is not intended to be launched a second time; the unique `(customer_id, period)` invoice constraint remains the final duplicate guard. The existing synchronous endpoint is retained only for compatibility and small local demos; use the batch endpoint for the architecture demonstration.
