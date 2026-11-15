# MeterFlow V15 — Final source audit

**Audit date:** 2026-11-16  
**Baseline:** user-provided V14 archive  
**Target:** CSE 444 Project 2 — MeterFlow

## What changed in V15

1. Spring Batch billing now uses up to eight customer partitions, a restartable `ItemStreamReader` checkpoint, and chunk commits of 25 customer records. A unit test checks reader checkpoint/resume. The database unique constraint `(customer_id, period)` remains the idempotency guard.
2. Added Flyway V1 clean-install PostgreSQL baseline covering all 14 current MeterFlow domain tables. Production enables Flyway and Hibernate schema validation. Existing V14 databases are not auto-adopted; they require a separately reviewed forward migration.
3. Production security now validates OAuth2/OIDC JWTs through the configured issuer. The JWT subject must equal the MeterFlow customer ID; the `roles` claim must include `ADMIN` or `CUSTOMER`. Local/demo continues to use HTTP Basic.
4. Frontend API helper supports Bearer tokens for production manual smoke testing while retaining Basic Auth for local demo. A complete browser OIDC Authorization Code + PKCE flow is not included.
5. Production webhook dispatch requires HTTPS, an exact host allowlist, rejects IP literals and private/non-public DNS answers, and does not follow redirects. A controlled egress proxy/firewall is still required to prevent DNS rebinding at socket-connect time.
6. k6 workload now targets 2,000 submitted events/sec using 200 requests/sec × 10 events/request, with p95 and error-rate thresholds.
7. Cloudflare container environment propagation includes OIDC issuer and webhook host allowlist; Durable Object SQLite migration declaration added.
8. Added batch, migration, security, deployment and final audit documentation.

## Source-level checks performed

- ZIP extraction and source inventory completed.
- Maven POM parsed as XML.
- `application.yml`, `application-prod.yml`, `application-test.yml` parsed as YAML.
- frontend `package.json` and `wrangler.jsonc` parsed as JSON.
- Flyway baseline contains 14 `CREATE TABLE` statements corresponding to the current 14 domain entities.
- Archive will be checked with `unzip -t` and SHA-256 after packaging.

## Checks not performed here

- `mvn verify` / Java compilation / JUnit execution: Maven is not installed in this build environment.
- Docker Compose build/start and PostgreSQL Flyway validation: Docker is unavailable here.
- Frontend `npm run build`: dependencies were not installed in this environment.
- OIDC live issuer/token integration: requires a real identity-provider tenant and keys.
- Webhook egress proxy / DNS pinning: requires infrastructure outside this repository.
- Cloudflare Containers and Vercel deployment: requires the owner's accounts, Worker subdomain, plan entitlement, secrets, GitHub repository and live smoke tests.
- QA1, QA2, QA3, QA4, QA5: no performance or acceptance pass is claimed without raw reproducible evidence.

## Acceptance truth

V15 is a materially improved source package, not a claim that all acceptance criteria are already proven. QA1/QA2/QA3/QA5 require execution on the target environment. FR1, FR3–FR10 still have the partial/integration gaps listed in `CRITERIA-MASTER-STATUS.md`. C10 depends on the instructor's change request; C12 is the live defence. The production OIDC setup requires an issuer and claims configured by the project owner.
