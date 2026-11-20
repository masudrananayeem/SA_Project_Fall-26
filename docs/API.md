# API examples

## Ingest usage
```bash
curl -u admin:meterflow-admin -X POST http://localhost:8080/api/usage/events:batch \
 -H 'Content-Type: application/json' -d '{"events":[{"customerId":"demo-customer","metric":"api_calls","quantity":120,"timestamp":"2026-11-13T10:00:00Z","idempotencyKey":"evt-001","properties":{"region":"ap-south-1","endpoint":"/v1/chat"}}]}'
```

## Create subscription
First list plans with `GET /api/catalogue/plans`, copy a plan `id`, then:
```json
{"customerId":"demo-customer","planId":"PLAN_UUID","periodStart":"2026-11-01","periodEnd":"2026-12-01"}
```
POST to `/api/subscriptions`.

## Run billing
`POST /api/invoices/run?period=2026-11`

## Explain invoice
`GET /api/invoices/{invoiceId}/lines`

All API endpoints except health, docs and OpenAPI require HTTP Basic authentication in this starter.

## V5 billing workflow endpoints

All endpoints use HTTP Basic authentication in the demo profile. Admin-only operations are marked.

### Credits
- `GET /api/credits?customerId={id}` — current prepaid balance (customer may read own balance; admin may read any).
- `POST /api/credits/top-up` — admin adds `{customerId,currency,amount}`.
- `POST /api/credits/notes` — admin issues `{customerId,currency,amount,reason}`; the note increases the prepaid balance.
- `GET /api/credits/notes?customerId={id}` — credit-note history.

### Budgets
- `POST /api/budgets` — create/update `{customerId,period,amount,currency}` where period is `YYYY-MM`.
- `GET /api/budgets/evaluate?customerId={id}&period=YYYY-MM` — evaluate estimate and emit each 50/80/100% alert once.
- The ingestion API also evaluates the current UTC-month budget after accepting events.

### Dunning / mock payments
- `POST /api/payments/mock/{invoiceId}/fail` — simulate a failed payment and open a dunning case.
- `POST /api/payments/mock/{invoiceId}/pay` — simulate success and reactivate an existing dunning case.
- `GET /api/dunning` — admin-only case list. Scheduled retries are day 1, day 3 and day 7; the mock retry worker records failed attempts and suspends the case after the third retry.

### Webhooks
- `POST /api/webhooks` — admin registers `{customerId,endpoint,secret}`.
- `GET /api/webhooks/{webhookId}/deliveries` — admin reads delivery attempts.
- `POST /api/webhooks/deliveries/{deliveryId}/replay` — admin manually requeues a delivery.
- Headers: `X-MeterFlow-Event` and `X-MeterFlow-Signature: sha256={hex}`. Signature is HMAC-SHA256 over the exact raw JSON body.
- Automatic events: `invoice.finalised`, `payment.failed`, `dunning.retry`, `usage.threshold_reached`.


## V6: configurable plan charges
`POST /api/catalogue/plans` remains admin-only and accepts an optional `charges` array. When supplied, the configured strategy list is used by live estimate and invoice pricing. Supported `type` values: `flat_fee`, `per_unit`, `per_seat`, `package`, `volume_tier`, `graduated_tier`.

Example:
```json
{
  "planKey": "api-growth",
  "name": "API Growth",
  "currency": "BDT",
  "flatFee": 0,
  "unitPrice": 0,
  "metric": "api_calls",
  "freeAllowance": 0,
  "minimumCommitment": 0,
  "charges": [
    {"type":"flat_fee","name":"Platform fee","amount":500},
    {"type":"graduated_tier","name":"API calls","amount":0,"tiers":[
      {"upTo":10000,"unitPrice":0.02},
      {"upTo":null,"unitPrice":0.015}
    ]}
  ]
}
```
If `charges` is omitted or empty, the legacy flat fee + per-unit + allowance + minimum commitment calculation is retained. `per_seat` currently uses one seat until seat quantities are modelled at subscription level.


## V7 subscription, tax, simulation and late adjustment APIs
- `POST /api/subscriptions` accepts optional `seatCount` and `trialEnd`.
- `POST /api/subscriptions/{id}/schedule-plan-change` schedules a plan change; the daily lifecycle job applies it on/after the effective date.
- `POST /api/subscriptions/{id}/change-plan` immediately changes plan and records a remaining-period proration delta; `GET /api/subscriptions/{id}/plan-changes` returns its audit history.
- `POST /api/subscriptions/{id}/seats?count=5` changes the billable seat count.
- Plan creation accepts `taxRate` as a percentage from 0 to 100.
- `GET /api/invoices/simulate?customerId=acme&period=2026-10` previews historical billing without writing an invoice.
- `GET /api/late-adjustments?customerId=acme&period=2026-10` lists pending late events; `POST /api/invoices/late-adjustments/run?customerId=acme&period=2026-10` creates a separate adjustment invoice record and marks those ledger entries invoiced.

- `POST /api/catalogue/plans/{planKey}/migrate-active?fromVersion=1` migrates active subscriptions pinned to the specified version to the latest version and writes a plan-change audit record. It intentionally does not post a price delta.


## Invoice-line traceability (V10)
`GET /api/invoices/{invoiceId}/lines` returns `sourceEventIdsJson` and `sourceEventCount` for billable usage lines. The IDs identify the persisted usage-event rows used for the metric quantity. This is an audit aid; verify that the production database has applied the corresponding schema update before deployment.


## Paginated collection endpoints (V11)
To avoid unbounded collection responses, paginated endpoints are available without removing the original compatibility routes:
- `GET /api/subscriptions/page?page=0&size=20`
- `GET /api/invoices/page?page=0&size=20&customerId=<customer>`
Page size is limited to 100. Customer accounts are scoped to their authenticated username; administrators can query all records or a selected customer. Spring Data page metadata is returned with the content.


## Additional V14 endpoints

- `GET /api/usage/aggregate`: filtered sum/count/maximum/unique_count/latest_value over a metric window. Parameters: `customerId`, `metric`, `from`, `to`, `operation`; optional `filters` (URL-encoded JSON object) and `distinctProperty`. Customer ownership rules apply.
- `POST /api/invoices/batch/run?period=YYYY-MM`: ADMIN-only partitioned Spring Batch billing run. It partitions active customers and delegates each partition to an idempotent customer-period invoice operation. Spring Batch job metadata enables failed-job restart with the same parameters; the invoice unique constraint remains the final duplicate guard.

## V18 endpoints

| Method | Route | Role | Purpose |
|---|---|---|---|
| PUT | `/api/catalogue/plans/{planKey}` | ADMIN | Edit a plan = publish the next immutable version |
| GET | `/api/catalogue/plans/{planKey}/versions` | any | All versions of a plan |
| GET | `/api/catalogue/plans/charge-types` | any | Registered charge types (built-in + plug-ins) |
| POST | `/api/catalogue/plans/{planKey}/migrate-active?fromVersion=1&toVersion=2&dryRun=true` | ADMIN | Move pinned subscriptions to another version (dry run shows impact) |
| POST | `/api/subscriptions/{id}/change-plan` body `{"planId":"…","timing":"IMMEDIATE\|PERIOD_END"}` | ADMIN | Upgrade/downgrade with proration or at period end |
| GET | `/api/usage/estimate?customerId=&period=` | owner/ADMIN | Live spend: usage, charge lines, tax, total (cached 30 s) |
| GET/POST | `/api/budgets` | owner/ADMIN | Read / set a monthly budget (alerts at 50/80/100 %) |
| GET | `/api/notifications?customerId=` | owner/ADMIN | In-app notifications (invoices, payments, dunning, budgets) |
| GET | `/api/invoices/{id}` | owner/ADMIN | Invoice |
| GET | `/api/invoices/{id}/render?format=text\|csv` | owner/ADMIN | Rendered invoice (Visitor) |
| GET | `/api/invoices/lines/{lineId}/trace` | owner/ADMIN | QA5: line -> price rule, tax rule, event ids, recomputed quantity |
| GET | `/api/invoices/simulate?customerId=&period=&planId=` | ADMIN | FR10 simulator (any plan version, default last month) |
| GET | `/api/invoices/simulate/versions?customerId=&period=` | ADMIN | Simulate under every version of the customer's plan |
| POST | `/api/invoices/run/customer?customerId=&period=` | ADMIN | Bill one customer (idempotent) |
| POST | `/api/invoices/batch/run?period=` | ADMIN | Partitioned, restartable billing job |
| POST | `/api/invoices/batch/recover` | ADMIN | Mark executions left by a killed JVM as FAILED (also automatic at startup) |
| POST | `/api/payments/mock/{invoiceId}/collect` | ADMIN | Charge now through the mock provider |
| POST | `/api/payments/mock/customers/{customerId}/outcome?succeed=false` | ADMIN | Make the mock provider decline this customer |
| POST | `/api/dunning/process?asOf=2026-11-20T00:00:00Z` | ADMIN | Run due retries as of a time (demo of day 1/3/7) |
| GET | `/api/late-adjustments?customerId=&status=PENDING` | ADMIN | Late-usage ledger (billed automatically on the next invoice) |

Removed: `POST /api/invoices/late-adjustments/run` (late usage is now billed automatically).

### Webhook signature (changed in V18)
Headers: `X-MeterFlow-Event`, `X-MeterFlow-Delivery`, `X-MeterFlow-Timestamp` (epoch seconds),
`X-MeterFlow-Signature: sha256=<hex>` where the HMAC-SHA256 is computed over
`<timestamp>.<raw body>` with the endpoint secret (min. 16 characters). Reject requests whose
timestamp is more than 5 minutes old.

### Billing period rule
Invoices cover calendar months (UTC). The scheduled billing date is 00:30 UTC on the 1st for the
month that just closed. In the prod profile an open month cannot be billed; the dev profile allows
it for demos (`METERFLOW_ALLOW_OPEN_PERIOD`).

## V19 endpoints
| Method | Route | Role | Purpose |
|---|---|---|---|
| GET | `/api/me` | any signed-in user | `{ username, roles, customerId }`: used by the admin console and customer portal right after login to check the credentials and the role |

Browser clients send `X-Requested-With: XMLHttpRequest`; with that header a failed login returns a JSON 401
without `WWW-Authenticate` (no browser popup). Other clients keep the standard Basic challenge.

## V20 endpoints (accounts and self-service)
| Method | Route | Role | Purpose |
|---|---|---|---|
| POST | `/api/auth/signup` | public | `{username, password, displayName, email, company?}`: creates a customer login; username = customer ID |
| GET | `/api/account` | signed in | Own profile (`stored=false` for the built-in demo customer) |
| PUT | `/api/account` | CUSTOMER | Update own name, email, company |
| GET | `/api/customers` | ADMIN | All stored customer accounts |
| POST | `/api/customers` | ADMIN | Create a login (also for an existing customer ID) |
| POST | `/api/customers/{username}/enabled?value=true\|false` | ADMIN | Enable or disable a login |
| POST | `/api/subscriptions/self` | CUSTOMER | `{planId, seatCount?}`: subscribe yourself (starts today, one live subscription) |
| POST | `/api/subscriptions/{id}/change-plan` | ADMIN or owner | Now also for the customer that owns the subscription |
| POST | `/api/subscriptions/{id}/cancel` | ADMIN or owner | Now also for the customer that owns the subscription |

## V23 endpoints (prepaid, payments, standing, usage breakdown)
| Method | Route | Role | Purpose |
|---|---|---|---|
| POST/PUT | `/api/catalogue/plans` | ADMIN | Plan input accepts `billingMode`: `POSTPAID` (default) or `PREPAID` |
| GET | `/api/subscriptions/analytics` | ADMIN | Per subscription: plan, billing mode, status, `onPlanSince`, `daysOnPlan`, `daysAsCustomer` |
| POST | `/api/payments/mock/{invoiceId}/pay?method=card\|wallet\|bank` | ADMIN or owner | Pay an invoice; the method is recorded in the ledger |
| POST | `/api/payments/mock/top-up` | ADMIN or CUSTOMER | `{customerId?, amount, currency, method}`: buy prepaid credit |
| GET | `/api/payments?customerId=` | ADMIN, or CUSTOMER (own only) | Payment ledger (latest 200), successes and failures |
| GET | `/api/payments/summary` | ADMIN | Totals today / 7 days / 30 days, failures today, block limit |
| POST | `/api/payments/standing/enforce` | ADMIN | Block postpaid customers with 2+ unpaid invoices, unblock those who paid |
| POST | `/api/invoices/prepaid/renew` | ADMIN | Issue this month's prepaid package invoices now (normally daily at 00:45 UTC, after the month-end run) |
| GET | `/api/usage/breakdown?customerId&from&to` | ADMIN or owner | Events per metric, per day, per property value, latest events, and whether usage is accepted |

New subscription statuses: `PENDING_PAYMENT` (prepaid, not paid yet) and `BLOCKED` (postpaid, too many unpaid invoices).
In both, `POST /api/usage/events:batch` rejects the events with the reason in `rejectionDetails`.

## V23.2 endpoints (plan upgrade balance)
| Method | Route | Role | Purpose |
|---|---|---|---|
| GET | `/api/billing/upgrade-charges/preview?subscriptionId&planId` | ADMIN or owner | Cost of changing plan now. `mode`: `SETTLE_NOW` (pay `totalDueNow`), `CREDIT` (`creditNow` to credit balance), `NEXT_INVOICE` (postpaid, prorated at month end), `NONE`, `PERIOD_END_ONLY` (prepaid ↔ postpaid) |
| GET | `/api/billing/upgrade-charges?customerId=` | ADMIN, or CUSTOMER (own) | Upgrade balances: already paid, remaining days, difference, VAT, total, status `DUE`/`PAID` |
| POST | `/api/payments/mock/upgrade-charges/{id}/pay?method=card\|wallet\|bank` | ADMIN or owner | Pay an upgrade balance (ledger kind `UPGRADE`) |
| GET | `/api/payments/balance?customerId=` | ADMIN, or CUSTOMER (own) | `totalDueNow`, `unpaidInvoices[]`, `upgradeCharges[]` (with `payBy`, `overdue`), `paidThisMonth`, `creditBalance` |

Rule: an upgrade inside a month that is already invoiced (a prepaid package paid up front) costs
`(new recurring − old recurring) × remaining days / days in month` + VAT.
A downgrade credits the same amount (pre-VAT) to the credit balance.
An immediate change between prepaid and postpaid is refused (400): it can only start at period end.
