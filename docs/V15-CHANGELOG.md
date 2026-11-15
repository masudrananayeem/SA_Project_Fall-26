# V15 changelog

- Reworked Spring Batch billing to use customer partitions, checkpointed ItemStreamReader and chunk size 25.
- Added Flyway PostgreSQL clean-install baseline V1 and production Flyway/DDL validation configuration.
- Added production OAuth2 Resource Server JWT profile; local profile retains demo HTTP Basic. Frontend settings can send a pasted bearer token for protected production API calls.
- Added webhook HTTPS/host allowlist/IP-literal checks in production and documented residual DNS pinning requirement.
- Updated Cloudflare container environment propagation for OIDC issuer and webhook allowlist; added initial SQLite Durable Object migration declaration.
- Added batch billing, migration and production security runbooks.

## Verification limits
Source and archive structural checks are performed in the build environment. Maven is not installed in this environment and Docker/Cloudflare/Vercel account deployment cannot be executed here. Treat Java compilation, Flyway-vs-entity validation, OIDC issuer integration, restart fault injection and performance QA as pending until run in the target environment.
