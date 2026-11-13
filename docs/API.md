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

## Event-ingestion semantics (V2.1)
- `events` must contain between 1 and 1,000 entries.
- `idempotencyKey` is unique across the event table. Concurrent replays of the same key are accepted only once; other attempts are counted in `duplicates`.
- Events older than 72 hours or more than 5 minutes in the future are skipped and returned in `rejectionDetails` with their idempotency key and reason.
- A successful response contains `received`, `accepted`, `duplicates`, `rejected`, `rejectionDetails`, and `lateEventWindowHours`.

## Subscription lifecycle
The console supports create, pause, resume and cancel. A subscription references a specific plan UUID/version. Mid-cycle proration, trials and scheduled changes are not yet implemented.
