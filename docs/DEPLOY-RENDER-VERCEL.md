# Go live: Neon (database) + Render (backend) + Vercel (admin and client)

```
Admin  (Vercel)  https://meterflow-admin.vercel.app  ┐
                                                     ├──>  Render  https://meterflow-api.onrender.com  ──>  Neon PostgreSQL
Client (Vercel)  https://meterflow-client.vercel.app ┘     (Spring Boot, Docker, profile "cloud")
```

All three have free plans. Do the steps in this order: each one needs a value from the one before.
Your real names (URLs) will differ; replace them everywhere.

## 0. Before you start
1. Run locally and see everything pass:
   - `.\scripts\backend-tests.ps1`;
   - `docker compose up -d --build`;
   - `.\scripts\smoke-test.ps1`;
   - `.\scripts\feature-test.ps1`.
2. Push the project to GitHub. `.env` is in `.gitignore`: never commit it.
   ```powershell
   git add .
   git commit -m "MeterFlow final"
   git push
   ```

## 1. Neon: the database (free, does not expire)
1. Go to https://neon.tech, sign in, and click **New project**:
   - name: `meterflow`;
   - region: **AWS Asia Pacific (Singapore)**.
2. Open **Connection details**:
   - turn **Connection pooling OFF**, so you get the *direct* host without `-pooler`;
   - choose the **Java / JDBC** format if offered.
3. Write down three values. Do not share them with anyone.
   - URL: `jdbc:postgresql://<host>/<database>?sslmode=require`
     (for example `jdbc:postgresql://ep-cool-name-123456.ap-southeast-1.aws.neon.tech/neondb?sslmode=require`)
   - Username (for example `neondb_owner`)
   - Password

The tables are created automatically on the first start (profile `cloud`, Hibernate `update`).

> Why not Render's own Postgres? A free Render Postgres is deleted 30 days after it is created.

## 2. Render: the backend
1. Go to https://render.com and sign in with GitHub.
2. Click **New → Blueprint**, then choose your repository. Render finds `render.yaml` and shows the service **meterflow-api** (Docker, free, Singapore).
3. Fill in the values it asks for:

   | Key | Value |
   |---|---|
   | `SPRING_DATASOURCE_URL` | the Neon JDBC URL from step 1 |
   | `SPRING_DATASOURCE_USERNAME` | Neon username |
   | `SPRING_DATASOURCE_PASSWORD` | Neon password |
   | `ADMIN_USERNAME` | e.g. `admin` |
   | `ADMIN_PASSWORD` | a strong password (this is the admin console login) |
   | `CUSTOMER_USERNAME` | e.g. `customer` (demo customer login) |
   | `CUSTOMER_PASSWORD` | another password |
   | `METERFLOW_CORS_ALLOWED_ORIGINS` | for now `http://localhost:5173,http://localhost:5174` (changed in step 4) |

4. Click **Apply**. The first build takes 5–10 minutes (Maven inside Docker).
5. When the status is **Live**, open `https://meterflow-api.onrender.com/actuator/health`. It must show `{"status":"UP"...}`. Copy this URL without `/actuator/health`: it is your **API URL**.

Notes about the free plan:
- **Sleeping.** The service sleeps after 15 minutes without requests. The next request wakes it, and that takes about a minute. The login page shows "The server is waking up…" while this happens.
- **Out of memory.** If the logs show the service being killed for running out of memory, change the instance type to **Starter** (Render dashboard → the service → Settings → Instance type).
- **Logs.** Render dashboard → meterflow-api → **Logs**.

## 3. Vercel: two frontends (admin and client)
Do this twice: once for `frontend/apps/admin`, once for `frontend/apps/client`.

1. Go to https://vercel.com, click **Add New → Project**, and import the same GitHub repository.
2. **Root Directory:**
   - for the admin project: click **Edit** and choose `frontend/apps/admin`;
   - for the client project: `frontend/apps/client`.

   The framework is detected as Vite. `vercel.json` already sets the install and build commands.
3. **Environment Variables:** add `VITE_API_URL` = your API URL from step 2 (e.g. `https://meterflow-api.onrender.com`, no trailing slash).
   - Never put a password in a `VITE_*` variable: these end up in the public JavaScript.
4. Click **Deploy**.
5. In the project's **Settings → Domains**, you can rename the domain, for example to `meterflow-admin.vercel.app` and `meterflow-client.vercel.app`.

## 4. Allow the two Vercel sites to call the API (CORS)
1. Render → meterflow-api → **Environment** → `METERFLOW_CORS_ALLOWED_ORIGINS`. Set it to both URLs, comma separated, with no spaces and no trailing slash:
   ```
   https://meterflow-admin.vercel.app,https://meterflow-client.vercel.app
   ```
2. Click **Save, rebuild and deploy** (or **Save and deploy**).

Without this step the browser blocks the calls and login says it cannot reach the server.

## 5. Daily billing jobs (free, with GitHub Actions)
Because the free service sleeps, the workflow `.github/workflows/daily-jobs.yml` wakes it every day at 06:15 Dhaka time. It runs:
- trials;
- prepaid renewals;
- dunning retries;
- the account-standing check;
- on the 1st of each month, the month-end billing run for the previous month.

To set it up:
1. GitHub repository → **Settings → Secrets and variables → Actions → New repository secret**. Add three secrets:
   - `METERFLOW_API_URL` = your API URL;
   - `METERFLOW_ADMIN_USERNAME`;
   - `METERFLOW_ADMIN_PASSWORD`.
2. Test it now: **Actions → MeterFlow daily jobs → Run workflow**. To bill a month by hand, type it in the box (e.g. `2026-11`).

## 6. Check the live system
1. Open the admin site and sign in with `ADMIN_USERNAME` / `ADMIN_PASSWORD`.
2. Open the client site, click **Create an account**, choose a plan and pay (test mode, no real money).
3. From PowerShell, run the smoke test against the live API:
   ```powershell
   .\scripts\smoke-test.ps1 -BaseUrl https://meterflow-api.onrender.com -Password <ADMIN_PASSWORD>
   ```
   The feature test also works, but it creates test plans and customers (names start with `ft-`). Run it before you add your demo data, or skip it.

## Troubleshooting

| Problem | Fix |
|---|---|
| Login says it cannot reach the server | Check that `VITE_API_URL` is exactly the Render URL (https, no slash), redeploy the Vercel project, and set CORS (step 4) |
| Login stays on "waking up" for more than 2 minutes | Render → Logs: usually a wrong database URL or password. The URL must start with `jdbc:postgresql://` and end with `?sslmode=require` |
| `Connection refused` / `password authentication failed` in the logs | Copy the Neon values again; use the direct host, not the pooler |
| The service restarts with an out-of-memory message | Switch the instance type to Starter |
| Changed a `VITE_` variable but nothing changed | Vercel only reads it at build time: **Deployments → Redeploy** |

## What stays secret
- **Secrets:** the Neon URL, username and password, and the admin and customer passwords. They go only into Render's Environment page and GitHub secrets. Never into Git, `VITE_*` variables or chat messages.
- **Not secret:** `VITE_API_URL`.
