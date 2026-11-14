# Production security deployment checklist

## Required before public exposure
- Set `SPRING_PROFILES_ACTIVE=prod`.
- Supply `SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME`, `SPRING_DATASOURCE_PASSWORD`, `SPRING_DATA_REDIS_HOST`, `ADMIN_USERNAME`, `ADMIN_PASSWORD`, `CUSTOMER_USERNAME`, and `CUSTOMER_PASSWORD` from the hosting provider's secret store.
- Use unique, long random credentials; never reuse `.env.example` defaults.
- Restrict PostgreSQL and Redis network access to the backend only; do not expose their ports publicly.
- Terminate TLS at a trusted ingress and enforce HTTPS.
- Replace the demo in-memory two-user identity provider with persistent customer identities and OIDC/JWT before real customer onboarding.
- Configure rate limits, account lockout, password rotation and audit log retention.
- Restrict actuator endpoints to operators.
- For webhooks, retain egress restrictions and add DNS pinning/revalidation or an outbound proxy that blocks private, loopback, link-local, multicast, IPv4-mapped IPv6 and metadata-service ranges. Never assume a one-time DNS check alone prevents rebinding.
- Verify that HTTP redirects remain disabled for webhook requests.
- Run tenant-isolation tests for every customer-facing endpoint.

## What V10 currently does
- Production profile has no default datasource or security credential values; missing required environment variables prevent normal configuration.
- Passwords for the demo admin/customer users are BCrypt-encoded in memory.
- Customer subscription listing, invoice listing and invoice-line reads are scoped by authenticated username.
- This is still demo authentication, not production user lifecycle or OIDC/JWT.
