# MeterFlow V11 changelog — 2026-11-15

## Implemented
- Added paginated subscription collection API `/api/subscriptions/page`.
- Added paginated invoice collection API `/api/invoices/page`.
- Page bounds are validated (page >= 0, size 1..100); existing collection routes are preserved for frontend compatibility.
- Added additive PostgreSQL SQL migration for V10 invoice provenance columns.
- Added database backup/migration deployment procedure.
- Added production security checklist, including secret configuration, network restrictions, demo-auth caveats and webhook SSRF release gates.
- Updated criteria matrix and API documentation.

## Not claimed complete
- No restartable Spring Batch job has been implemented.
- No complete Flyway baseline has been generated.
- In-memory demo authentication is not production-grade.
- Webhook DNS rebinding cannot be fully prevented by the current one-time hostname validation.
- No Maven/Docker/frontend build or QA benchmark is claimed as passing.
- C10 and C12 remain instructor/live activities.
