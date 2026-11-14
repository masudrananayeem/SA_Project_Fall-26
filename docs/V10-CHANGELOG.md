# MeterFlow V10 changelog — 2026-11-15

## Implemented in source
- Invoice-line provenance: source usage event IDs and event count are stored on each billing line and populated by the billing path.
- Correlation IDs: validated `X-Correlation-ID` is echoed to the caller and made available in SLF4J MDC for request-scoped log correlation.
- Added unit coverage for invoice-line provenance constructor/getter contract.

## Documentation and evidence
- Added mini-ATAM analysis with six quality attribute scenarios, architectural trade-offs and sensitivity points.
- Added QA1–QA5 acceptance runbook and evidence integrity requirements.
- Updated criteria status and requirements traceability.

## Explicitly not claimed complete
- Restartable chunk-oriented Spring Batch billing / 10k-customer guarantee.
- Complete Flyway schema baseline and production migration path.
- Production-grade user identity and tenant provisioning.
- Complete webhook SSRF/DNS-rebinding defence.
- QA1–QA5 measured acceptance results.
- Instructor change request (C10) and live defence (C12).

## Verification
Source-level archive and syntax checks only are available in this delivery environment. Maven tests, Docker startup, frontend build and load tests must be executed in CI or on the project machine before submission.
