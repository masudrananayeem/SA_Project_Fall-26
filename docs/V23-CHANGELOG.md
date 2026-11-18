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
