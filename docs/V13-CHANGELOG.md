# V13 changelog

V13 is based on V12 and keeps its project modules, UI, documentation and audit.

## Added
- Cloudflare Containers Worker gateway for the existing Spring Boot Docker image (`cloudflare/index.ts`).
- Root Wrangler configuration (`wrangler.jsonc`) using the current Durable Object Container API configuration style.
- Root deployment package scripts for Wrangler.
- Windows PowerShell local run, test, API smoke-test and QA evidence instructions (`docs/LOCAL-RUN-AND-TEST.md`).
- Cloudflare Containers + managed PostgreSQL/Redis + Vercel deployment guide (`docs/DEPLOYMENT.md`).
- Configurable CORS allowlist for local Vite and hosted Vercel origins.
- Optional managed Redis password and TLS configuration for hosted environments.
- Browser Settings form to enter API Basic Auth credentials; removed the frontend example's baked-in demo username/password variables.
- Swagger links now use the configured API base URL rather than hard-coded localhost.

## Important limitations
- Cloudflare Containers requires an eligible Workers Paid plan and a Docker-compatible build environment.
- This is a single-container course/demo deployment adapter, not a validated horizontally-scaled production topology.
- A complete Flyway baseline is still missing; production profile uses `ddl-auto=validate`, so an empty hosted database will not start until a reviewed schema migration has been applied.
- The in-memory Basic Auth users are still demo identity, not production multi-tenant authentication.
- Maven/Docker were unavailable in the authoring environment and npm dependency installation timed out; Java tests, Docker build, Wrangler type-check/deploy and frontend build are not claimed as passed.
