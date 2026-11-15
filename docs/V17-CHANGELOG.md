# MeterFlow V17 changelog

## Implemented in source

1. Added scheduled budget refresh for the current UTC billing month. The default cadence is five minutes and can be configured with `meterflow.budgets.refresh-ms`; an initial delay is configurable with `meterflow.budgets.initial-delay-ms`.
2. Added period-specific usage estimate calculation. Budget evaluation for a supplied `YYYY-MM` now calculates usage in that month rather than relying on the live current-month estimate.
3. Hardened charge strategy validation: non-negative quantity/seat/charge constraints, non-empty charge definitions, tier price/bound validation and mandatory unbounded final tier for volume/graduated tiers.
4. Added regression tests for invalid tier configuration and negative usage.
5. Added the missing dunning creation-time accessor used by the retry schedule unit test.
6. Updated the master requirements matrix with these source changes and remaining acceptance gates.

## Not claimed as complete

- Maven test execution, PostgreSQL/Testcontainers integration, Docker build, frontend production build, k6 load tests and cloud deployment were not run in this environment.
- QA1–QA5 remain pending acceptance evidence.
- Late usage is persisted as an adjustment record but is not yet automatically posted into the next invoice.
- FR10 still needs immutable pricing snapshots and faithful historical replay.
- Instructor change request (C10) and live defence (C12) require external events.
