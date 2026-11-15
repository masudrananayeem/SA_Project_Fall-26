# MeterFlow — local run and test guide (Windows PowerShell)

## 1. Required tools

- Git
- Docker Desktop (start it before running the project)
- Node.js 20+ and npm
- Java 21 (only needed when running Spring Boot outside Docker)
- Maven 3.9+ (only needed for backend tests outside Docker)

Check installations:

```powershell
git --version
node --version
npm --version
docker --version
docker compose version
java -version
mvn -version
```

## 2. Recommended: run the complete local stack with Docker

Open PowerShell in the extracted `MeterFlow` folder:

```powershell
Copy-Item .env.example .env
# Edit .env and replace the local demo passwords before sharing the machine.
docker compose up --build -d
docker compose ps
docker compose logs -f backend
```

Open:

- Frontend: http://localhost:5173
- Backend health: http://localhost:8080/actuator/health
- Swagger UI: http://localhost:8080/swagger-ui.html
- OpenAPI JSON: http://localhost:8080/v3/api-docs

The local demo accounts are `admin` / `meterflow-admin` and `customer` / `meterflow-customer` unless changed in `.env`. These are local-only defaults. In the UI, open **Settings**, enter the account credentials, and save them. Do not put credentials in `VITE_*` variables; Vite bundles those into browser assets.

Stop without deleting the database:

```powershell
docker compose down
```

Stop and delete the local database volume (DESTRUCTIVE):

```powershell
docker compose down -v
```

## 3. Run frontend and backend separately (optional)

Keep PostgreSQL and Redis running from Compose, then start only those services:

```powershell
docker compose up -d postgres redis
```

Terminal 1 — backend (requires Java 21 and Maven):

```powershell
cd backend
mvn spring-boot:run
```

Terminal 2 — frontend:

```powershell
cd frontend
Copy-Item .env.example .env
npm install
npm run dev
```

Vite proxies `/api` requests to `http://localhost:8080` in development.

## 4. Backend automated tests

From the repository root:

```powershell
cd backend
mvn test
mvn verify
```

Some integration tests use Testcontainers and therefore need Docker running. A successful `mvn test` is not proof of the 1M-event or 2,000-events/sec quality targets; those require the dedicated load/reconciliation runs below.

## 5. Frontend build verification

```powershell
cd frontend
npm install
npm run build
```

The production build is written to `frontend/dist`.

## 6. API smoke tests

PowerShell example using the local admin account:

```powershell
$pair = 'admin:meterflow-admin'
$basic = [Convert]::ToBase64String([Text.Encoding]::ASCII.GetBytes($pair))
$headers = @{ Authorization = "Basic $basic" }
Invoke-RestMethod http://localhost:8080/actuator/health
Invoke-RestMethod http://localhost:8080/api/catalogue/plans -Headers $headers
```

Use Swagger UI for request schemas and the remaining API operations. Recommended manual scenario:

1. Sign in as admin in the UI Settings.
2. Confirm plans load.
3. Create a plan and confirm its version appears.
4. Create a subscription through Swagger for a test customer.
5. In **Usage Metering**, ingest a small batch (e.g. 10 events).
6. Submit the same idempotency keys again through Swagger and confirm duplicates are ignored.
7. Check the usage summary and estimate endpoints.
8. Run billing once; run it again for the same period and confirm no second invoice is created.
9. Inspect invoice lines and provenance endpoint to verify source event counts/IDs.
10. Test pause/resume/cancel, credits, budget thresholds, dunning retries and webhook replay through Swagger.
11. For partitioned billing, use `POST /api/invoices/batch/run?period=YYYY-MM` as ADMIN. It creates one worker partition per distinct active customer. If a job fails, retry with the same period so Spring Batch can restart the failed JobInstance; if it completed, use a new period or inspect the existing invoice set rather than expecting a second run.

Do not use real customer data for the demo. Use a dedicated test customer and a non-production database.

## 7. Quality-attribute test plan

- QA1: generate exactly 1,000,000 events with 3% duplicate submissions and 2% late arrivals. Compare invoice totals and event inclusion against an independent SQL/Python recomputation; assert no event is billed twice. Save generator seed, input manifest, output and reconciliation report.
- QA2: run `performance/ingest.js` against the deployed API with a controlled load profile; record throughput and p50/p95/p99 latency while a billing run executes. Do not report a pass until p95 is at most 100 ms at 2,000 events/sec.
- QA3: run billing for 10,000 customers, terminate the process during the run, restart and verify every customer has exactly one invoice and all partitions/chunks complete. A partitioned Spring Batch job is now present in source; the 10,000-customer kill/restart acceptance test and its raw evidence still must be executed before QA3 can pass.
- QA4: create a new plan using existing charge definitions without changing Java code; for the instructor's new charge type, implement it through an extension point and add contract tests proving existing strategies remain unchanged.
- QA5: call invoice-line provenance API and verify event count/price rule. Compare live estimates to final invoice for identical events and assert absolute relative error <= 1%.

Keep raw logs, CSV/JSON outputs, environment details and commit hash under `docs/quality-evidence/`. Do not fabricate evidence or mark a target as passed if it was not executed.

## 8. Troubleshooting

- `docker compose` cannot connect: start Docker Desktop and wait until Engine is ready.
- Port 5432/6379/8080/5173 already in use: stop the conflicting service or change the host-side port mapping in `docker-compose.yml`.
- Backend exits during startup: inspect `docker compose logs backend`; verify PostgreSQL health and `.env` values.
- Frontend shows 401: open Settings and enter the correct API username/password. Clear stale values by clearing site local storage.
- Frontend cannot reach API: verify `VITE_API_URL` and the backend health endpoint.
- Maven tests fail on Testcontainers: confirm Docker is running and has enough memory.


## 9. Metric aggregation API (FR2)

`GET /api/usage/aggregate` accepts `customerId`, `metric`, `from`, `to`, `operation` (`sum`, `count`, `maximum`, `unique_count`, `latest_value`), optional `distinctProperty`, and optional URL-encoded JSON `filters`. Example:

```powershell
$filters = [uri]::EscapeDataString('{"region":"ap-south"}')
Invoke-RestMethod "http://localhost:8080/api/usage/aggregate?customerId=customer&metric=api_calls&from=2026-11-01T00:00:00Z&to=2026-12-01T00:00:00Z&operation=sum&filters=$filters" -Headers $headers
```

The aggregation window is limited to 366 days. Property filters use exact string matching. `unique_count` counts distinct values of `distinctProperty`.


## V15 batch restart test
1. Start a clean local PostgreSQL/Redis stack and backend.
2. Seed at least 100 active customers with a plan and usage events.
3. Call `POST /api/invoices/batch/run?period=2026-11` as admin.
4. During execution, terminate the backend process (not the database) after some worker chunks have committed.
5. Restart backend and relaunch the same period.
6. Query invoices and verify at most one `(customer_id, period)` invoice per customer and all eligible customers completed.
7. Repeat with 10,000 customers for QA3 and record start/end timestamps, logs and SQL counts.

## JWT production smoke test
Configure an OIDC issuer that issues JWTs with `sub` equal to customer ID and `roles: ["CUSTOMER"]` or `roles: ["ADMIN"]`. Set `METERFLOW_OIDC_ISSUER_URI`, run with `SPRING_PROFILES_ACTIVE=prod`, then verify missing/invalid/expired tokens are rejected and role-scoped APIs deny cross-customer access. Local HTTP Basic credentials are not valid in production.
