# Production security checklist (V15)

- Production disables HTTP Basic and requires OAuth2 Resource Server JWT validation using `METERFLOW_OIDC_ISSUER_URI` (mapped to `spring.security.oauth2.resourceserver.jwt.issuer-uri`).
- JWT must contain `sub` equal to the MeterFlow customer ID for customer access and a `roles` string array containing `ADMIN` or `CUSTOMER`. The identity provider must issue short-lived signed access tokens and support key rotation/discovery.
- Local/dev continues to use HTTP Basic demo users. Never expose demo credentials in production.
- Configure exact Vercel origins in `METERFLOW_CORS_ALLOWED_ORIGINS`; do not use `*`.
- Webhook production endpoints must use HTTPS, be configured in `METERFLOW_WEBHOOK_ALLOWED_HOSTS`, must not be IP literals, and must resolve only to public addresses. Redirects are not followed. Keep the allowlist narrow.
- DNS checks plus a hostname allowlist reduce SSRF risk, but Java's standard HttpClient does not pin the checked DNS answer to the subsequent socket connection. For high-assurance production, route webhook egress through a controlled proxy/firewall that enforces destination IP policy at connection time. Do not claim DNS-rebinding immunity without that network control.
- Store DB, Redis, OIDC and webhook configuration in Cloudflare secrets/managed secret storage. Rotate credentials.
- Use TLS for PostgreSQL and Redis, least-privilege DB users, backups, audit logs and rate limiting at the edge.
