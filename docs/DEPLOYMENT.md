# MeterFlow — local, Cloudflare Containers and Vercel deployment

## Deployment topology

- React + Vite frontend: Vercel static deployment (`frontend/`).
- Spring Boot Java 21 API: Cloudflare Containers, reached through a small Cloudflare Worker gateway (`cloudflare/index.ts`).
- Database: managed PostgreSQL (Neon, Supabase or another PostgreSQL provider).
- Redis: managed Redis (Upstash or another provider) if enabled/required by the selected environment.

**Important platform note:** a Spring Boot JVM application is not deployed as plain Workers JavaScript. The Cloudflare adapter runs the existing Spring Boot Docker image using Cloudflare Containers. Cloudflare Containers are a Workers Paid feature; a Docker-compatible build environment is required when building the image from the Dockerfile. The included configuration is a single-container starter deployment, not a horizontally scaled production billing cluster.

## A. Local run first

Follow `docs/LOCAL-RUN-AND-TEST.md`. Do not deploy until the local frontend, backend health endpoint, Swagger UI and basic billing scenario work.

## B. Prepare the hosted database

1. Create a PostgreSQL database with a provider that allows secure external connections from Cloudflare Containers.
2. Copy the provider's JDBC URL, username and password. Prefer TLS/SSL and a least-privilege database user.
3. Create a Redis database if the chosen application profile requires Redis. Record host and port; keep its password/TLS settings according to the provider.
4. **Schema blocker:** the repository currently has no complete Flyway baseline. Production profile uses `ddl-auto=validate`; a brand-new empty database will fail to start. Do not switch production to `ddl-auto=update` to bypass this. Before production, generate and review a complete schema migration from the current entity model, apply it to the hosted database, and verify backup/restore. The existing V10 SQL file is only an additive invoice-provenance migration, not a full baseline.

## C. Deploy backend on Cloudflare Containers

Prerequisites:

- Cloudflare account with Workers Paid plan and Containers access.
- Docker Desktop running locally (or use Cloudflare Workers Builds with the repository connected).
- Node.js/npm installed.
- A provisioned PostgreSQL schema as described above.

From the MeterFlow repository root:

```powershell
npm install
npx wrangler login
npx wrangler types
```

Set each secret. Run each command and paste the value when prompted; do not commit secrets:

```powershell
npx wrangler secret put SPRING_DATASOURCE_URL
npx wrangler secret put SPRING_DATASOURCE_USERNAME
npx wrangler secret put SPRING_DATASOURCE_PASSWORD
npx wrangler secret put ADMIN_USERNAME
npx wrangler secret put ADMIN_PASSWORD
npx wrangler secret put CUSTOMER_USERNAME
npx wrangler secret put CUSTOMER_PASSWORD
npx wrangler secret put SPRING_DATA_REDIS_HOST
npx wrangler secret put SPRING_DATA_REDIS_PORT
npx wrangler secret put SPRING_DATA_REDIS_PASSWORD
npx wrangler secret put SPRING_DATA_REDIS_SSL_ENABLED
npx wrangler secret put METERFLOW_CORS_ALLOWED_ORIGINS
```

For `METERFLOW_CORS_ALLOWED_ORIGINS`, enter the exact Vercel production origin, e.g. `https://your-meterflow.vercel.app` (no trailing slash). If you use a custom domain, include that exact origin too, comma-separated. Do not use `*` with authenticated APIs. For a TLS-enabled managed Redis provider, set `SPRING_DATA_REDIS_SSL_ENABLED=true` and enter its password; use an empty password only when the provider does not require one.

Deploy:

```powershell
npx wrangler deploy
npx wrangler containers list
npx wrangler containers images list
```

The first deployment can take several minutes while Cloudflare builds/publishes the container image and provisions the instance. Check Cloudflare Workers & Pages → Containers logs and health. Test:

```powershell
Invoke-RestMethod https://meterflow-api.<YOUR_WORKERS_SUBDOMAIN>.workers.dev/healthz
Invoke-RestMethod https://meterflow-api.<YOUR_WORKERS_SUBDOMAIN>.workers.dev/actuator/health
```

The first URL checks the Worker gateway; the second checks Spring Boot and its database. Swagger should be at `/swagger-ui.html` or `/swagger-ui/index.html` depending on Springdoc routing.

### Cloudflare deployment caveats

- Container outbound Internet access is enabled because the Java service must reach managed PostgreSQL/Redis. Restrict database network access where possible and use TLS.
- The Worker routes requests to one named Durable Object/container instance. This is intended as a course/demo deployment. It does not satisfy QA2/QA3 scale targets by itself.
- Keep the API URL private until the production schema, authentication, CORS, backups and load tests are reviewed.
- For reliable long-running billing and scheduled work, ensure the container lifecycle and job execution model are explicitly tested. Do not assume a container remains permanently warm.

## D. Deploy frontend on Vercel

1. Push the repository to GitHub.
2. In Vercel, choose **Add New → Project** and import the repository.
3. Set **Root Directory** to `frontend`.
4. Framework preset: **Vite**.
5. Build command: `npm run build`.
6. Output directory: `dist`.
7. Add environment variable for Production and Preview as appropriate:

```text
VITE_API_URL=https://meterflow-api.<YOUR_WORKERS_SUBDOMAIN>.workers.dev
```

Do not add API usernames, passwords, database credentials or tokens as `VITE_*` variables. Those values are public in the generated frontend bundle. The user enters the Basic Auth account in the MeterFlow Settings page; the browser stores it in local storage for this demo.

8. Deploy. After deployment, open the Vercel URL → Settings → enter the API username/password → Save API credentials.
9. Verify dashboard data loads and browser developer tools show no CORS or network errors.

Official Vercel Vite guide: https://vercel.com/docs/frameworks/frontend/vite

## E. Update and redeploy

Backend / Worker:

```powershell
npm install
npx wrangler deploy
```

Frontend: push changes to the connected Git branch; Vercel builds automatically. Or install Vercel CLI and run `vercel --prod` from `frontend/`.

## F. Release checklist

- [ ] Full Flyway baseline created, reviewed and applied.
- [ ] Hosted PostgreSQL backup and restore tested.
- [ ] Managed Redis connectivity/TLS verified.
- [ ] Unique production secrets stored as Cloudflare Worker secrets.
- [ ] Exact Vercel origin configured in CORS secret.
- [ ] No credentials embedded in frontend build.
- [ ] Production authentication upgraded beyond shared in-memory demo users/HTTP Basic.
- [ ] Cloudflare Container health, logs, restart and outbound DB connectivity verified.
- [ ] CI, integration, security and load tests executed.
- [ ] QA1–QA5 evidence captured; no unsupported pass claims.

## Platform references

- Cloudflare Containers overview: https://developers.cloudflare.com/containers/
- Cloudflare Containers getting started: https://developers.cloudflare.com/containers/get-started/
- Cloudflare container deployment: https://developers.cloudflare.com/containers/guides/deploy/
- Vercel Vite deployment: https://vercel.com/docs/frameworks/frontend/vite
- Vite environment variable security: https://vite.dev/guide/env-and-mode
