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
