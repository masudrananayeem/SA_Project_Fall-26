# MeterFlow — Usage-Based Billing Engine

**Student:** Masud Rana Nayeem  
**Student ID:** 0242310005101849  
**Course:** CSE 444 — Software Architecture and Design Patterns  
**Section:** 64-K  
**Project:** Project 2 — MeterFlow

MeterFlow is a course-project implementation foundation for usage-based SaaS/API billing. It uses a Spring Boot modular monolith and a React customer/admin console. Version 2.1 adds an extensible pricing strategy registry, a reusable metric aggregation engine, subscription-aware live estimates, race-safe event idempotency, late-event validation, and clearer live dashboard metrics.

## Technology
- Java 21, Spring Boot 4.0.0, Spring Modulith, Maven
- Spring Data JPA, Spring Batch starter, Spring Security, Actuator, SpringDoc
- PostgreSQL 17, Redis 7
- React 19, TypeScript, Vite, Tailwind CSS
- JUnit 5, Spring Modulith tests, Testcontainers, k6

## Start with Docker

1. Install Docker Desktop.
2. Copy `.env.example` to `.env` and change all demo passwords.
3. Copy `frontend/.env.example` to `frontend/.env` only for local development, then run:

```bash
docker compose up --build
```

Open:
- Frontend: http://localhost:5173
- API: http://localhost:8080/api
- Swagger UI: http://localhost:8080/swagger-ui/index.html
- Health: http://localhost:8080/actuator/health

The Compose defaults are intentionally simple local-demo credentials. Change every password in `.env` before use. For the browser demo, configure `frontend/.env` or set `mf_user` and `mf_pass` in browser local storage. Do not deploy HTTP Basic credentials publicly.

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

## Version 2.1 reliability and dashboard additions

- PostgreSQL idempotent inserts use `ON CONFLICT DO NOTHING`, so concurrent retries of the same event key are counted as duplicates instead of crashing the entire request.
- Events older than 72 hours and events more than 5 minutes in the future are rejected with per-event reason details.
- Usage and invoice aggregation use half-open time windows (`>= start`, `< end`) to avoid double-counting events exactly on month boundaries.
- The overview fetches the selected demo customer’s event count and live spend estimate from the backend.
- Webhook secrets are excluded from JSON responses. Invoice finalisation is allowed only from `DRAFT`; repeated finalisation returns a conflict.
- Persistence/optimistic-lock conflicts return HTTP 409 Problem Details.

## Version 2.2 console and billing safeguards

- Subscription screen supports plan-version selection, date-range creation, pause, resume and cancel actions.
- Settings screen allows local demo Basic Auth credentials to be saved in browser storage; no default password is embedded in frontend code.
- Revenue chart is computed from actual invoice periods rather than placeholder sample bars.
- Webhook secret values are omitted from serialized API responses.
- Invoice finalisation is a one-way `DRAFT` → `FINALISED` transition, with repeat attempts returning HTTP 409.

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

## Architecture and course deliverables

- `docs/ARCHITECTURE.md` — module and runtime architecture
- `docs/ADR/` — architecture decision records
- `docs/UTILITY-TREE.md` — quality attribute scenarios
- `docs/PATTERN-JUSTIFICATION.md` — design-pattern rationale
- `docs/REQUIREMENTS-TRACEABILITY.md` — FR/QA implementation status and remaining work
- `docs/API.md` — API examples
- `docs/TESTING.md` — test strategy

## Honest implementation status

This is an improved course-project foundation, **not yet a fully verified production billing system**. Some critical assignment criteria remain incomplete: late-event adjustment handling, complete plan JSON persistence, subscription proration/trials/scheduled changes, tax configuration, real Spring Batch chunk/partition/restart flow, credit ledger/credit notes, budget threshold notifications, dunning retry scheduler, signed/replayable webhook delivery, historical plan simulator, and measured QA1–QA3/QA5 evidence. The exact status is maintained in `docs/REQUIREMENTS-TRACEABILITY.md`.

Do not submit QA targets as achieved until you have run the corresponding tests and retained the raw evidence. The project deliberately documents these gaps instead of fabricating results. Build and runtime verification is still pending in this environment; run the commands above on a machine with Maven, JDK 21, Docker and Node.js installed.


**Security note:** HTTP Basic authentication and browser-held credentials are suitable only for a local academic demo. Use short-lived tokens or secure sessions, TLS, secret rotation, and role-based authorization before any public deployment.

### Frontend local configuration
Copy `frontend/.env.example` to `frontend/.env`, then set a non-default local password matching `ADMIN_PASSWORD` in the root `.env`. Restart Vite after changing environment variables. Never commit either `.env` file.
