# Quality evidence

Place raw, reproducible outputs under this directory, grouped by `qa1`, `qa2`, `qa3`, `qa4`, and `qa5`.

Each evidence folder should include:
- `README.md` with commit SHA, timestamp, environment, exact command and pass/fail decision;
- unmodified raw output (k6 JSON, Maven logs, SQL reconciliation output, etc.);
- a short analysis explaining deviations and limitations.

No benchmark is marked passed until it has actually been run. See `../QUALITY-ACCEPTANCE-RUNBOOK.md`.
