# ADR-007: Frontend monorepo with separate admin console and customer portal

## Status
Accepted (V19)

## Context
MeterFlow has two kinds of users with different jobs:

- **Billing operators (ADMIN)** configure plans, subscriptions, billing runs, credits, dunning and webhooks for every customer.
- **Customers (CUSTOMER)** only see their own usage, live spend, budget, invoices and credits.

V18 shipped one React page that showed the admin console to everybody and relied on the browser's
Basic Auth popup. Customers saw buttons they could not use (they got 403s), and the popup could not be styled or explained.

## Decision
1. The frontend is an **npm-workspaces monorepo** in `frontend/`:
   - `apps/admin`: admin console, port 5173 locally, its own Vercel project.
   - `apps/client`: customer portal, port 5174 locally, its own Vercel project.
   - `packages/shared`: typed API client, session handling, the login page, UI components, invoice and estimate views, design tokens.
2. The **backend stays a separate deployable** (Spring Boot modular monolith, ADR-001). The apps only talk to it over `/api`.
3. Both apps open on a **login page**. Credentials are checked with `GET /api/me`, which returns the roles and customer ID.
   The admin app accepts only ADMIN, the portal only CUSTOMER, and each links to the other.
4. Browser requests send `X-Requested-With: XMLHttpRequest`. The backend then answers a failed login with a JSON 401 and no `WWW-Authenticate` header, so no native popup appears.
5. Authorization stays on the server (method security and ownership guards). The UI only hides what a role cannot do.

## Consequences
- Positive:
  - Each audience gets a focused interface, and the two can be deployed, cached and scaled separately.
  - The shared package stops the API contract and money formatting from drifting between the apps.
  - Defence demo: the same backend serves both apps, and the authorization boundary is visible.
- Negative:
  - Two Vercel projects and two CORS origins to configure.
  - The shared package is consumed as TypeScript source, so both apps must use compatible React/TS versions; one lockfile pins them.
- Alternatives rejected:
  - One app with role-based routes: simpler to deploy, but it ships admin code to customers.
  - Two separate repositories: the shared code would have to be duplicated or published.
