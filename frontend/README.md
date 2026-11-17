# MeterFlow frontend (monorepo)

```
frontend/
├── apps/
│   ├── admin/      Admin console  (ADMIN role)     http://localhost:5173
│   └── client/     Customer portal (CUSTOMER role)  http://localhost:5174
├── packages/
│   └── shared/     API client, session, Login page, UI kit, invoice + estimate views, styles
├── Dockerfile      builds one app: --build-arg APP=admin|client (nginx, proxies /api)
└── package.json    npm workspaces
```

The backend (Spring Boot) is separate (`../backend`). The apps only call `/api/**`.

## Run
```bash
npm ci                 # once, from this folder
npm run dev:admin      # admin console  -> http://localhost:5173
npm run dev:client     # customer portal -> http://localhost:5174
npm run build          # type-check + build both apps
```
The Vite dev server proxies `/api` to `http://localhost:8080`, so start the backend first
(`docker compose up -d` from the repository root, or `mvn spring-boot:run`).

Local demo accounts: `admin` / `meterflow-admin`, `customer` / `meterflow-customer`.
First time: sign in to the admin console and click **Prepare demo** on the Overview page.

## Environment (build time)
| Variable | Local | Vercel |
|---|---|---|
| `VITE_API_URL` | empty (proxy) | `https://meterflow-api.<you>.workers.dev` |
| `VITE_ADMIN_URL` | `http://localhost:5173` | admin Vercel URL (set on the client project) |
| `VITE_CLIENT_URL` | `http://localhost:5174` | client Vercel URL (set on the admin project) |

Never put credentials in `VITE_*` variables: they are bundled into the JavaScript.
