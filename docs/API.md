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
