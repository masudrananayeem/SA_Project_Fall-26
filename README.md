# MeterFlow — Usage-Based Billing Engine

**Student:** Masud Rana Nayeem  
**Student ID:** 0242310005101849  
**Course:** CSE 444 — Software Architecture and Design Patterns  
**Section:** 64-K  
**Project:** Project 2 — MeterFlow

MeterFlow is a course-project implementation foundation for usage-based SaaS/API billing. It uses a Spring Boot modular monolith and a React customer/admin console. Version 2 adds an extensible pricing strategy registry, a reusable metric aggregation engine, and subscription-aware live estimates.

## Technology
- Java 21, Spring Boot 4.0.0, Spring Modulith, Maven
- Spring Data JPA, Spring Batch starter, Spring Security, Actuator, SpringDoc
- PostgreSQL 17, Redis 7
- React 19, TypeScript, Vite, Tailwind CSS
- JUnit 5, Spring Modulith tests, Testcontainers, k6

## Start with Docker

1. Install Docker Desktop.
2. Copy `.env.example` to `.env` and change all demo passwords.
3. Run:

```bash
docker compose up --build
```

Open:
- Frontend: http://localhost:5173
- API: http://localhost:8080/api
- Swagger UI: http://localhost:8080/swagger-ui/index.html
- Health: http://localhost:8080/actuator/health

Demo credentials are `admin` / `meterflow-admin` and `customer` / `meterflow-customer`. They are local-demo credentials only. Do not deploy them publicly.

## Local development

Backend (requires JDK 21 and Maven 3.9+):

```bash
cd backend
mvn spring-boot:run
```

Frontend (requires Node.js 20+):

```bash
cd frontend
npm install
npm run dev
```

The frontend uses same-origin `/api` requests. When running Vite separately, set `VITE_API_URL` if your API is not available at the Vite origin and configure backend CORS/reverse proxy accordingly.

## Key API routes

| Method | Route | Purpose |
|---|---|---|
| POST | `/api/usage/events:batch` | Ingest up to 1,000 events |
| GET | `/api/usage/summary?customerId=demo-customer` | Current-month usage totals |
| GET | `/api/usage/estimate?customerId=demo-customer` | Estimate using active subscription plan |
| GET/POST | `/api/catalogue/plans` | List/create immutable plan versions |
| GET/POST | `/api/subscriptions` | List/create subscriptions |
| POST | `/api/invoices/run?period=YYYY-MM` | Generate period invoices |
| GET | `/api/invoices?customerId=demo-customer` | List invoices |
| GET | `/api/invoices/{id}/lines` | Explain invoice lines |
| POST | `/api/payments/mock/{invoiceId}/pay` | Mock payment |
| POST | `/api/webhooks` | Register webhook destination |

## Version 2 additions

- `catalogue/charges`: strategy registry for `flat_fee`, `per_unit`, `graduated_tier`, `volume_tier`, `package`, and `per_seat`. The registry is open for extension; new charge strategies can be added without editing existing strategy implementations.
- `metering/aggregation`: `sum`, `count`, `maximum`, `unique_count`, and `latest_value` with exact-match property filters.
- Live estimate now loads the customer's active subscription and its referenced plan version, then rates the current usage quantity using the existing persisted plan formula.
- Unit tests cover pricing strategies and metric aggregation.

## Testing and evidence

```bash
cd backend
mvn test
```

Run the k6 load test against a running local service:

```bash
k6 run performance/ingest.js
```

The script's 2,000 iteration/s arrival rate and submitted event count are a load-test workload, not evidence that the QA2 latency target is achieved. Record raw output, environment details and p95 latency in `docs/quality-evidence/` before making a performance claim.

## Latest criteria audit update

See `docs/CRITERIA-MASTER-STATUS.md` for the complete C1–C12, FR1–FR10 and QA1–QA5 status matrix. This revision also:
- Adds the missing Spring Data query declarations used by the live usage summary and estimate services.
- Enables Spring method security and restricts sensitive admin write operations to the ADMIN role.
- Corrects the frontend rejected-event count to match the backend response contract.
- Adds C4 architecture diagrams, a 15-minute defence plan and an honest AI/process log template.

These changes improve the foundation but do **not** complete all requirements. In particular, credits, dunning, tax configuration, Spring Batch restartability, full charge-strategy persistence, budget alerts, outbound webhook delivery/replay, plan simulation and QA benchmark evidence remain open. Ownership checks now protect the customer usage and invoice read routes; other resource ownership coverage and pagination remain open. See the master matrix for exact status.

## Architecture and course deliverables

- `docs/ARCHITECTURE.md` and `docs/ARCHITECTURE-C4.md` — module, runtime and C4 views
- `docs/CRITERIA-MASTER-STATUS.md` — complete C1–C12 / FR1–FR10 / QA1–QA5 audit
- `docs/PROCESS-LOG.md` — genuine work and AI-use log template
- `docs/DEFENCE-GUIDE.md` — 15-minute defence preparation
- `docs/ADR/` — architecture decision records
- `docs/UTILITY-TREE.md` — quality attribute scenarios
- `docs/PATTERN-JUSTIFICATION.md` — design-pattern rationale
- `docs/REQUIREMENTS-TRACEABILITY.md` — FR/QA implementation status and remaining work
- `docs/API.md` — API examples
- `docs/TESTING.md` — test strategy

## Honest implementation status

This is an improved course-project foundation, **not yet a fully verified production billing system**. Some critical assignment criteria remain incomplete: late-event adjustment handling, DB-race-safe idempotent ingestion, complete plan JSON persistence, subscription proration/trials/scheduled changes, tax configuration, real Spring Batch chunk/partition/restart flow, credit ledger/credit notes, budget threshold notifications, dunning retry scheduler, signed/replayable webhook delivery, historical plan simulator, and measured QA1–QA3/QA5 evidence. The exact status is maintained in `docs/REQUIREMENTS-TRACEABILITY.md`.

Do not submit QA targets as achieved until you have run the corresponding tests and retained the raw evidence. The project deliberately documents these gaps instead of fabricating results.


## V2.1 reliability improvements
- Enforces the specified 72-hour late-event window and a 5-minute future timestamp tolerance.
- Reports accepted, duplicate, and rejected event counts with rejection reasons.
- Uses the database unique idempotency constraint as the concurrency-safe final check.
- Uses half-open time ranges for usage aggregation and billing boundaries.
- Returns structured HTTP 409 responses for persistence and optimistic-lock conflicts.
- Removes the frontend's baked-in default password; configure `frontend/.env` from `frontend/.env.example`.

**Security note:** HTTP Basic authentication and browser-held credentials are suitable only for a local academic demo. Use short-lived tokens or secure sessions, TLS, secret rotation, and role-based authorization before any public deployment.

### Frontend local configuration
Copy `frontend/.env.example` to `frontend/.env`, then set a non-default local password matching `ADMIN_PASSWORD` in the root `.env`. Restart Vite after changing environment variables. Never commit either `.env` file.


### Additional billing workflows (V5)
The backend now includes prepaid credit balances and credit notes, budget threshold checks, persisted mock dunning cases, and durable HMAC-signed webhook deliveries with retry/replay APIs. See `docs/CRITERIA-MASTER-STATUS.md` for exact implementation boundaries. These are development implementations and still require integration/build/load verification before production use.
