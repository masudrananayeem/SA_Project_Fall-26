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
