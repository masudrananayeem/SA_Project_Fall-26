# MeterFlow deployment: local check -> Cloudflare (backend) -> Vercel (frontend)

```
Browser ──> Vercel (React static site) ──HTTPS──> Cloudflare Worker ──> Cloudflare Container (Spring Boot, profile "cloud")
                                                                            └──> Neon / Supabase PostgreSQL (TLS)
```

## 0. Check locally first
1. `cd backend && mvn verify` – must end with `BUILD SUCCESS`.
2. `docker compose up --build -d` from the repository root, wait until `docker compose ps` shows the backend running.
3. `powershell -ExecutionPolicy Bypass -File scripts/smoke-test.ps1` – must print `ALL CHECKS PASSED`.

## 1. Database (Neon example, free tier)
1. neon.tech -> New project -> region close to you (e.g. Singapore).
2. Connection details -> choose the **direct** connection (not "pooled") and note host, database, user, password.
3. JDBC URL: `jdbc:postgresql://<host>/<database>?sslmode=require`
   (Example: `jdbc:postgresql://ep-cool-name-123456.ap-southeast-1.aws.neon.tech/neondb?sslmode=require`).

The `cloud` profile creates the tables on the first start (Hibernate `update`), plus the Spring Batch
and event-publication tables. No Redis is needed (`CACHE_TYPE=simple`).

## 2. Backend on Cloudflare Containers
Needs: Cloudflare account with Containers enabled (Workers paid plan), Docker Desktop running, Node 22.
From the repository root (where `wrangler.jsonc` is):

```powershell
npm install
npx wrangler login
npx wrangler secret put SPRING_DATASOURCE_URL          # the JDBC URL from step 1
npx wrangler secret put SPRING_DATASOURCE_USERNAME
npx wrangler secret put SPRING_DATASOURCE_PASSWORD
npx wrangler secret put ADMIN_USERNAME                 # e.g. admin
npx wrangler secret put ADMIN_PASSWORD                 # choose a strong password
npx wrangler secret put CUSTOMER_USERNAME              # e.g. customer
npx wrangler secret put CUSTOMER_PASSWORD
npx wrangler secret put METERFLOW_CORS_ALLOWED_ORIGINS # for now: http://localhost:5173
npx wrangler deploy
```

`wrangler deploy` builds `backend/Dockerfile` locally with Docker, pushes the image and deploys the
Worker. It prints the URL, e.g. `https://meterflow-api.<your-subdomain>.workers.dev`.
The first deploy can take several minutes before the container answers.

Check:
```powershell
curl https://meterflow-api.<your-subdomain>.workers.dev/healthz           # Worker only, instant
curl https://meterflow-api.<your-subdomain>.workers.dev/actuator/health   # starts the container: 1-3 min the first time
powershell -ExecutionPolicy Bypass -File scripts/smoke-test.ps1 -BaseUrl https://meterflow-api.<your-subdomain>.workers.dev -Password <ADMIN_PASSWORD>
```

Logs: `npx wrangler tail` (Worker) and the Containers page in the Cloudflare dashboard. `npx wrangler containers list` shows status.
The container sleeps after 30 minutes without requests; the next request starts it again (cold start).

## 3. Frontend on Vercel
1. Push the project to GitHub (or use `npx vercel` from `frontend/`).
2. Vercel -> Add New Project -> import the repository -> **Root Directory: `frontend`**
   (framework Vite; `frontend/vercel.json` sets `npm ci`, `npm run build`, output `dist`).
3. Environment variable: `VITE_API_URL = https://meterflow-api.<your-subdomain>.workers.dev` (no trailing slash).
4. Deploy and note the URL, e.g. `https://meterflow-xyz.vercel.app`.

`VITE_API_URL` is baked in at build time: after changing it, redeploy on Vercel.

## 4. Connect them (CORS)
```powershell
npx wrangler secret put METERFLOW_CORS_ALLOWED_ORIGINS   # https://meterflow-xyz.vercel.app,http://localhost:5173
npx wrangler deploy                                      # restarts the container with the new value
```
Open the Vercel URL -> **Settings** in the app -> enter the admin user/password -> save. The Overview page should load data.

## Troubleshooting
| Symptom | Cause / fix |
|---|---|
| Browser console: "blocked by CORS policy" | `METERFLOW_CORS_ALLOWED_ORIGINS` must equal the exact Vercel origin (https, no trailing slash); redeploy the Worker. |
| 401 in the app | Wrong user/password in the app's Settings (they are the Cloudflare secrets). |
| "did not become healthy within 240 seconds" | Usually the database: wrong JDBC URL/password, or a pooled Neon URL. Check container logs in the dashboard. |
| Requests from Vercel go to `vercel.app/api/...` | `VITE_API_URL` was not set at build time; set it and redeploy. |
| Spring Boot killed / restarting | Instance too small; the Worker uses `standard-1` (4 GiB). |

## Production profile (optional)
`SPRING_PROFILES_ACTIVE=prod` (Worker secret) switches to OIDC JWT login and Flyway `validate`
(V1 + V2 migrations). It needs an identity provider (`METERFLOW_OIDC_ISSUER_URI`) issuing a `roles`
claim; the demo frontend uses Basic Auth, so keep `cloud` for the course demo.
