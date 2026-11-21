# V23 changelog: prepaid and postpaid plans, live payments, auto-block, clear API usage

## 1. Postpaid and prepaid plans
- Every plan now has a **billing mode**:
  - `POSTPAID` (use first, pay after): the default, the old behaviour.
  - `PREPAID` (pay first, then use).
- The mode is set in the admin "Plans" page and is shown as a badge everywhere: plan cards, subscriptions, account, invoices.
- Existing databases get a new seeded plan `pro-prepaid` (Pro Prepaid Package, 1500 BDT flat, 100,000 API calls included, 15% tax).
- **Prepaid flow**
  1. The customer picks a prepaid plan.
  2. The subscription starts as `PENDING_PAYMENT` (shown as "Awaiting payment").
  3. A package invoice for the current month is issued right away; usage is refused until it is paid.
  4. The customer can pay now (card, mobile wallet or bank) or later.
  5. After payment the subscription becomes `ACTIVE` and usage is accepted.
  6. Each new month a renewal job issues the next package invoice. If it is not paid, the subscription goes back to "Awaiting payment". Admins can run this job by hand.
  7. The month-end billing run does not bill a prepaid month twice.
- **Postpaid flow**
  1. The customer picks a postpaid plan and starts using it immediately.
  2. When subscribing, they may optionally pay an advance (prepaid credit) that is applied to future invoices.

## 2. Automatic block after 2 unpaid months (postpaid)
- A postpaid customer with **2 or more unpaid invoices** is set to `BLOCKED` ("Blocked (unpaid)"); usage events are rejected with a clear reason.
- Paying enough invoices to go below the limit unblocks the account automatically.
- **When the check runs:**
  - after every finalised invoice;
  - after every payment;
  - every hour;
  - on demand from the admin Payments page.
- The limit is configurable with `METERFLOW_BLOCK_AFTER_UNPAID` (default 2).
- Notifications `account.blocked` and `account.unblocked` are raised.

## 3. Payments the admin can see live
- Every payment attempt is recorded in a ledger (`payment_records`), including:
  - invoice payments and prepaid credit top-ups;
  - successes and failures;
  - the method used (card, wallet, bank, admin, card on file).
- New admin **Payments** page:
  - live feed, refreshed every 8 seconds, with new payments highlighted;
  - totals for today, the last 7 days and the last 30 days;
  - a filter;
  - the account-standing list with "Run check now" and "Run prepaid renewal".
- A **payment bell** in the admin top bar pops up a toast when a customer pays online and counts unseen payments.
- The overview page gains:
  - an "Unpaid / blocked" figure;
  - a "Customers per plan" distribution;
  - "Latest payments".

## 4. Who is on which package, and for how long
- New `GET /api/subscriptions/analytics`. For each subscription it returns the plan, billing mode, status, the date the current plan started, **days on plan** and **days as customer**.
- **Admin pages that use it:**
  - Subscriptions page: "On this plan" and "Customer for" columns, plus an "Unpaid / blocked" filter.
  - Plans page: per plan, the number of subscribers, average and longest days on the plan, and how many are unpaid.

## 5. Client payment experience
- **Choosing a plan:** a confirmation dialog explains whether the plan is pay-first or pay-later and offers **Pay now** or **Pay later**.
- **Pay dialog:** the customer picks card, mobile wallet or bank and confirms.
- **Prepaid status is shown in:**
  - the dashboard banner;
  - the Invoices page (banner plus payment history);
  - the Account page (billing mode, status and "Add prepaid credit").

## 6. "Which API am I using, and how?"
- New client page **API**:
  - the endpoint with a copy button;
  - ready-to-paste cURL, JavaScript and Python examples;
  - what each event field means;
  - a plain-words explanation of how the current plan bills usage.
- **Usage breakdown** (`GET /api/usage/breakdown`), shown on the client API page and in the admin customer drawer (tab "API usage"):
  - events per metric (with a "billed" badge on the metric the plan charges for);
  - a per-day chart;
  - the share of each property value (for example which endpoint or region was called);
  - the latest events.
- When usage is refused, the reason is shown (prepaid not paid / blocked / suspended).

## 7. Interface polish
- Inter font, softer background accent, decorative stat cards, sticky table headers.
- Better small-screen layout: no horizontal scrolling at 390 px.

## Tests
- **Backend unit tests:**
  - `PrepaidBillingTest`;
  - `AccountStandingStateTest`;
  - `UsageStandingGateTest`.
- **`scripts/feature-test.ps1` adds two sections:**
  - **Prepaid:** pay before use, the 1150.00 package invoice, usage refused then accepted, the payment ledger, the admin summary, top-up and renewal.
  - **Postpaid auto-block:** two unpaid invoices → BLOCKED, usage refused, paying one unblocks, plus the analytics and breakdown endpoints.

## V23 final: criteria audit and deployment readiness
- `docs/CRITERIA-MASTER-STATUS.md` has a new V23 table. It checks FR1–FR10, QA1–QA5 and the Spring Boot architecture focus one by one, naming the code and the test for each item.
- New `POST /api/subscriptions/lifecycle/run` (ADMIN) runs the trial and scheduled-plan-change sweep on demand. It is idempotent.
- The Cloudflare Worker has a daily cron trigger (`15 0 * * *`). It wakes the sleeping container and runs these jobs (ADR-008):
  - trials;
  - prepaid renewals;
  - dunning retries;
  - account standing;
  - on the 1st, the month-end billing run.
- New `scripts/backend-tests.ps1` runs the backend unit and Modulith tests in Docker (no local Maven needed) and stores the log in `docs/quality-evidence/`.
- `feature-test.ps1` adds a check of the daily-job endpoints.
- Fixed a compile error in `PaymentService` (ambiguous `log.info`).
- `.gitignore` now also ignores `.wrangler/` and `.dev.vars`.

## V23.2: balance due after a plan upgrade, and a billing fix

### What the customer sees
- **Before confirming a plan change**, the dialog shows exactly what it costs:
  - **Prepaid package already paid this month (upgrade):** "To pay now for the rest of the month" with the breakdown:
    - already paid this month;
    - new plan for the remaining N of D days;
    - minus the old plan for the same days;
    - VAT;
    - the price from next month.
  - **Downgrade:** the unused part goes to the credit balance (credit note) and is used on the next invoice.
  - **Postpaid:** nothing to pay now; the month-end invoice charges each plan only for the days it was active.
  - **Prepaid ↔ postpaid:** only from the 1st of next month (an immediate switch is refused with a clear message).
- **After an upgrade**, the pay dialog opens immediately ("Pay the rest for your upgrade"). "Pay later" is allowed.
- **New "Amount due now" card** on Overview, Invoices and Account:
  - unpaid invoices and upgrade balances, each with a Pay button;
  - paid this month;
  - credit balance;
  - for postpaid plans, the month's estimate minus credit, which is what will be paid at month end.
- **The status banner** says "Plan upgraded: X still to pay … please pay by <date>".
- **If an upgrade balance stays unpaid for 3 days** (`METERFLOW_UPGRADE_GRACE_DAYS`), the prepaid service waits for payment (usage refused) until it is paid. Paying it resumes the service at once.
- **Invoices page:** "Plan upgrades" table, and payment history labelled "Plan upgrade balance".

### What the admin sees
- **Payments page:**
  - a "Plan upgrade balances" list (due ones, with "mark as paid");
  - live-feed rows labelled "Plan upgrade balance";
  - the payment bell names upgrade payments.
- **Customer drawer → Payments:** the same "Amount due now" card for that customer.

### Backend
- `PlanChanged` event (subscriptions) → `UpgradeChargeService` (invoicing):
  - If the month is already invoiced, it creates an `UpgradeCharge` (upgrade) or a credit note (downgrade).
  - The month's invoice is never changed or duplicated: the unique `(customer, period)` key stays.
  - The plan change is marked settled, so the next invoice does not prorate it again.
- `GET /api/billing/upgrade-charges/preview?subscriptionId&planId`: the cost of changing plan now.
- `GET /api/billing/upgrade-charges`: list (admin: all; customer: own).
- `POST /api/payments/mock/upgrade-charges/{id}/pay?method=`: pay an upgrade balance. It is recorded in the ledger as kind `UPGRADE`.
- `GET /api/payments/balance?customerId=` returns:
  - amount due now;
  - unpaid invoices and upgrade balances;
  - paid this month;
  - credit balance.
- Flyway `V5__upgrade_charges.sql` (prod). Dev and cloud create the table with Hibernate `update`.
- **Fix (FR4/FR5):** a closed month is now billed with the plan **in force on its last day**.
  - Before, a period-end change applied on the 1st (before the 00:30 billing run) re-priced the month that had just closed at the new plan.
- Prepaid renewal runs at 00:45 UTC: after the 00:10 trial/scheduled-change sweep and the 00:30 month-end billing run, so a customer moving from postpaid to prepaid on the 1st still gets the closed postpaid month billed (the Cloudflare cron uses the same order).

### Tests
- `PlanChangeSettlementTest`:
  - half-month upgrade = half the difference;
  - the full difference on the 1st;
  - a downgrade is negative;
  - for every day of the month, the balance equals the postpaid proration rule;
  - a closed month is priced by the plan of that month.
- **`feature-test.ps1`:**
  - the upgrade preview matches the formula;
  - the balance due equals the preview;
  - the month's invoice is not duplicated;
  - paying clears the balance and shows in the admin feed;
  - an immediate prepaid → postpaid switch is refused (400);
  - a downgrade adds the unused part to credit.

## V23.3: fully responsive layout pass (no feature changes)

### Checking
- Every page of both apps was checked automatically at 12 screen widths: 320, 360, 390, 414, 600, 768, 900, 1024, 1280, 1440, 1600 and 1920 px.
- No page scrolls sideways at any width.

### Cards and grids
- **Stat cards** sit in balanced rows: groups of 6 show 3+3, or 6 in one row on wide screens. There is never a lonely card on a second row.
- **Cards side by side** have equal heights.
- **Key-value blocks** are separate tiles, so an odd last item no longer leaves an empty bordered gap.
- **"Amount due now"** is a tidy three-column strip: due, paid this month, credit. It stacks on phones.
- **Plan cards** have equal heights, with the button at the bottom.
- **Card headers** keep a small action (Manage, View all) on the same line as the title when it fits.

### Phones
- **Payment feeds, the payment history and the customer list** turn into labelled cards (no sideways table scrolling). This uses a generic `cards-sm` option on `Table`.
- **The invoice table** hides the "Issued" column, keeping invoice, period, status and total.
- **The customer portal top bar** fits on 320 px phones.
- **Charts** never push the page wider than the screen.

## V23.4: full review against the brief: "every event billed exactly once"

The brief was reviewed again line by line against the code. Two gaps were found and fixed.

### 1. A subscription that left ACTIVE in a month was never invoiced for that month
- **Before:** the month-end run billed only `ACTIVE` subscriptions. A customer who cancelled, was paused, was blocked or was suspended on the 20th got no invoice for the 1st–20th: the accepted usage and the month's fee were lost. A customer could also cancel the day before billing to avoid paying.
- **Now:**
  - `Subscription` records `statusChangedAt` (the day its status last changed; Flyway `V6`).
  - `billableIn(period)` is true when the subscription is active, or when it left the active state on or after the first day of the period.
  - The billing run (`InvoiceBillingRun`, the sequential run and the batch partitioner) invoices those months once.
  - Months that start after a cancellation or pause are not billed.
  - An expired trial is never billed.
  - Existing rows have no date, so they keep the old behaviour.

### 2. Usage accepted that could never be billed
- **Before:** a paused subscription still accepted usage, and so did a cancelled customer, for any month.
- **Now, the ingestion gate:**
  - refuses usage while the subscription is paused, with a clear reason, as it already did for awaiting payment, blocked and suspended;
  - after a cancellation, accepts only events dated on or before the cancellation day, in a month that will still be invoiced (late arrivals, within the 72-hour window);
  - customers with no subscription yet are still accepted: a subscription started later that month bills them.

### Wording
- The client "Cancel subscription" dialog now says exactly what happens: the service stops today, usage dated after today is refused, and this month is still invoiced once.

### Tests
- `SubscriptionBillingWindowTest`:
  - cancelled, paused or blocked this month → billed this month, not next;
  - an expired trial is never billed;
  - a paused subscription refuses usage.
- `LeftActiveBillingTest`: a cancelled or paused customer is invoiced for the month they used, once (CREATED, then EXISTING).

## V23.5: deploy on Render + Vercel
- `render.yaml` Blueprint:
  - backend Docker service, free plan, Singapore region, health check `/actuator/health`, profile `cloud`;
  - JVM settings for 512 MB;
  - every secret is asked at setup (`sync: false`).
- `.github/workflows/daily-jobs.yml` wakes the sleeping free service every day and runs the idempotent jobs:
  - trials;
  - prepaid renewal;
  - dunning;
  - standing;
  - month-end billing on the 1st.
  It can also be run by hand.
- The login page explains the roughly one-minute wake-up of a sleeping free server ("The server is waking up…").
- `docs/DEPLOY-RENDER-VERCEL.md` is a step-by-step guide covering Neon, Render, two Vercel projects, CORS, daily jobs, checks and troubleshooting.
- No feature or API changes.
