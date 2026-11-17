# V19 changelog: admin console and customer portal (frontend monorepo)

## Frontend
- Replaced the single dark dashboard with an npm-workspaces monorepo (`frontend/`):
  - `apps/admin`: admin console (dark sidebar, indigo accent), http://localhost:5173.
  - `apps/client`: customer portal (top navigation, green accent), http://localhost:5174.
  - `packages/shared`:
    - API client: Basic Auth, error mapping, session expiry.
    - Login page, design system, toasts, drawers and modals.
    - Shared billing components: invoice view with per-line trace, estimate breakdown.
- Login first in both apps; a role check via `/api/me` routes users to the right app. The "use local demo account" button only appears for local (same-origin) builds.

### Admin console
- **Overview:** invoiced, collected, outstanding, active subscriptions and open dunning; revenue chart; dunning watchlist. A one-click **Prepare demo** subscribes `customer` and sends 1,800 events.
- **Customers:** customer 360 drawer with live spend breakdown, invoices, subscriptions, credit, budget, notifications, late-usage ledger and "bill now".
- **Pricing plans:**
  - Create a plan or publish a new version: simple pricing, or charge components (flat_fee, per_unit, per_seat, package, volume_tier, graduated_tier) with a tier editor.
  - Metric aggregation, property and filters; tax rate.
  - Version history; migration with dry-run impact, then apply.
- **Subscriptions:** create (trial, seats, period); change plan now (prorated) or at period end; seats; pause, resume, cancel; plan-change history.
- **Usage metering:**
  - Batch ingestion with now, spread or late timestamps; **re-send same batch** to show idempotency.
  - Aggregation query (sum, count, maximum, unique_count, latest_value) with filters; month-to-date summary.
- **Billing runs:** run a period, bill one customer (idempotent: CREATED then EXISTING), partitioned batch job and stale-run recovery, simulator and side-by-side version comparison.
- **Invoices:** search and status filter. The invoice drawer has:
  - Lines with **trace to events**.
  - Text preview and downloads (text, CSV).
  - Finalise, pay, collect, and simulate a failed payment (starts dunning).
- **Credits:** balance, top-up, credit notes.
- **Dunning:** state explanation, process retries "as of" +1/+3/+7/+14/+30 days, mark paid or failed, mock provider decline/succeed per customer.
- **Webhooks:** add an endpoint (generated signing secret), delivery log with payload, replay.

### Customer portal
- **Overview:**
  - Live estimate with a 30-second countdown refresh and the plan line breakdown.
  - Budget gauge.
  - Due-invoice banner with **Pay now**; recent invoices.
- **Usage:** month-to-date totals, 14-day daily chart, an explore query, and sending test usage for the customer's own ID.
- **Invoices:** amount due and paid; invoice drawer with trace, downloads and pay.
- **Budget & alerts:** set or change the monthly budget, 50/80/100 % thresholds, alert history.
- **Plan & credits:** subscription, prices, scheduled change, plan-change history, prepaid credit and credit notes.
- **Notifications:** bell with unread count and a full list.

## Backend
- `GET /api/me` (`shared.MeController`): username, roles, customer ID.
- Basic Auth entry point: requests with `X-Requested-With: XMLHttpRequest` get a JSON 401 without `WWW-Authenticate`, so there is no browser popup. Other clients still get the Basic challenge.
- CORS: default allowed origins are 5173 and 5174; `X-Requested-With` is an allowed header.
- Tests: `MeControllerTest`, `BasicEntryPointTest`.

## Delivery
- `frontend/Dockerfile` builds one app (`--build-arg APP=admin|client`) and serves it with nginx, which proxies `/api` to the backend.
- `docker-compose.yml`: services `admin` (5173) and `client` (5174) replace `frontend`. Use `docker compose up -d --build --remove-orphans` once to drop the old container.
- Vercel: two projects with Root Directory `frontend/apps/admin` and `frontend/apps/client` (see `docs/DEPLOYMENT.md`).
- CI builds both apps.
