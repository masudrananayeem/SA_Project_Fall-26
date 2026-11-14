# MeterFlow — local database and deployment guide

## Local development (recommended for the course demo)

1. Install Docker Desktop and start it.
2. Copy `.env.example` to `.env` in the repository root.
3. Keep the sample values only for local development. Change the passwords before exposing any port outside your machine.
4. Run `docker compose up --build`.
5. Open the frontend at `http://localhost:5173`, API at `http://localhost:8080`, and Swagger at `http://localhost:8080/swagger-ui.html`.

PostgreSQL runs in the `postgres` container. Its database is `meterflow` and its named Docker volume is `pgdata`; data survives container recreation. Redis is a separate cache/service container and is not the system of record. To stop: `docker compose down`. To remove the database volume too (DESTRUCTIVE): `docker compose down -v`.

The Compose memory ceilings are PostgreSQL 2 GiB, backend 2 GiB, Redis 512 MiB and frontend 512 MiB (5 GiB total ceiling, leaving headroom within an 8 GiB host). These are container limits, not a measured performance result.

## Suggested hosted deployment

- Frontend: Vercel or Cloudflare Pages. Set `VITE_API_URL` to the public backend origin (no `/api` suffix).
- Backend: Render, Railway, Fly.io or a comparable Java 21 container host. Set `SPRING_PROFILES_ACTIVE=prod`, `SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME`, `SPRING_DATASOURCE_PASSWORD`, `SPRING_DATA_REDIS_HOST`, `SPRING_DATA_REDIS_PORT`, `ADMIN_USERNAME`, `ADMIN_PASSWORD`, `CUSTOMER_USERNAME`, and `CUSTOMER_PASSWORD` as platform secrets.
- Database: managed PostgreSQL (Neon, Supabase, Render PostgreSQL, etc.). Enable SSL according to the provider's connection string and keep backups enabled.
- Redis: managed Redis (e.g. Upstash or provider Redis) if required by the selected host.

Never put database credentials or backend admin secrets in `VITE_*` variables: Vite values are shipped to the browser. Do not deploy the sample credentials. The production profile disables Hibernate schema auto-update and uses `ddl-auto: validate`; provision the schema through a reviewed migration before enabling it. A complete Flyway migration set is still a release task, so a fresh hosted database is not yet a one-click production deployment.

## Database backup

For local backup: `docker compose exec -T postgres pg_dump -U "$POSTGRES_USER" "$POSTGRES_DB" > meterflow-backup.sql` (load `.env` in the shell first). Restore only after confirming the target database. For hosted environments, use the provider's automated backup/PITR features and test a restore.

## Deployment checklist

- [ ] Replace every sample password with long unique secrets.
- [ ] Configure HTTPS and restrictive CORS at the hosting layer/API.
- [ ] Provision schema migrations and verify `ddl-auto=validate` startup.
- [ ] Configure database SSL, backups, restore test and retention.
- [ ] Set a real customer identity provider; current in-memory users are demo credentials, not multi-tenant production identity.
- [ ] Run CI, integration tests, smoke tests and load tests against the deployed configuration.
