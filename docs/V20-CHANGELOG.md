# V20 changelog: self-service accounts, admin customer view, fluid layout

## Customer self-service
- **Sign-up** in the customer portal ("Create an account"): company, name, email, customer ID (username, suggested from the company name) and password.
  - The account signs in automatically and lands on **Choose your plan**.
  - The username *is* the customer ID used by every customer-scoped endpoint.
  - Sign-up cannot claim a customer ID that already has subscriptions.
- **Plans** page: catalogue cards for the latest version of every active plan, with readable prices.
  - With no subscription: subscribe (seats asked for per-seat plans).
  - With one: switch plan from next month or now (prorated), or move to the newest version of the current plan.
- **Account** page: edit the profile (name, email, company), cancel the subscription, see the prices that apply and the plan-change history, prepaid credit and credit notes.
- **Overview** shows a "Choose a plan to get started" card for customers without a subscription.

## Admin: every customer, every bill
- **Customers** page merges three sources: sign-ups, admin-created logins, and customer IDs that only appear on subscriptions or invoices.
  - Per customer: contact, company, plan, status, **this month's live estimate**, total billed, outstanding, login status and join date.
  - Totals: customers, active subscriptions, billed to date, outstanding.
  - Search, plus filters: all, active, no plan, owes money.
- **New customer**: create a portal login (temporary password) and optionally subscribe in one step.
- Customer drawer: profile, enable/disable login, subscribe if no plan, and the existing tabs (live spend, invoices, account, activity).

## Backend
- New module `accounts`:
  - `CustomerAccount` entity (table `customer_accounts`, Flyway `V3__customer_accounts.sql` for prod) and `CustomerAccountRepository`.
  - `AccountService`: sign-up, admin create, enable/disable, profile; BCrypt hashing; username rules and reserved names.
  - `AccountUserDetailsService` (non-prod): configured admin + demo customer + stored accounts. Replaces the in-memory user bean in `SecurityConfig`.
  - `AccountController`:
    - `POST /api/auth/signup` (public).
    - `GET`/`PUT /api/account`.
    - `GET`/`POST /api/customers` and `POST /api/customers/{username}/enabled?value=` (ADMIN).
- Subscriptions:
  - `POST /api/subscriptions/self` (CUSTOMER): subscribe yourself, one live subscription.
  - `change-plan` and `cancel` now also allowed for the owning customer; admins can still act on any subscription.
- Tests: `AccountServiceTest` (hashing, lowercase IDs, customer-ID takeover blocked, reserved/invalid names, weak passwords).
- Smoke test: new step that signs up, checks `/api/me`, self-subscribes, verifies a 403 on another customer's invoices, and checks the admin customer list.

## Responsive, fluid UI
- Fluid type and spacing (`clamp()`), wider content limits (admin 1680 px, portal 1440 px), grids that never overflow (`minmax(min(100%, …))`).
- Admin sidebar becomes an off-canvas menu under 1024 px; portal navigation collapses into a menu under 1180 px.
- Under 760 px:
  - Two-column stat tiles, compact tables with less important columns hidden.
  - Modals become bottom sheets; tab bars and tables scroll horizontally; forms stack.
- Checked with an automated browser run at 390, 768, 1024 and 1920 px on every admin and portal page: no horizontal overflow.
