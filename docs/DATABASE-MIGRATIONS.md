# Database migration policy

## Current state
- Local development currently uses Hibernate `ddl-auto: update`.
- Production profile uses `ddl-auto: validate`; it will not silently mutate the schema.
- V10 adds two columns to `invoice_lines`. Apply `db/V10-invoice-provenance.sql` to an existing V9 database before deploying V10.
- The SQL is additive and has defaults so existing invoice lines remain readable.

## Safe deployment sequence
1. Take a PostgreSQL backup (`pg_dump -Fc ... > meterflow-before-v10.dump`).
2. Test restore the backup into a staging database.
3. Apply `psql "$DATABASE_URL" -v ON_ERROR_STOP=1 -f docs/db/V10-invoice-provenance.sql`.
4. Deploy backend V10 with `SPRING_PROFILES_ACTIVE=prod`.
5. Verify `/actuator/health`, invoice list and invoice-line endpoints.
6. Keep the pre-deploy backup until rollback window expires.

## Important limitation
A complete Flyway baseline for every existing entity/table has not yet been generated and validated. Do not enable Flyway against a populated database until a baseline migration and migration test are prepared. This V10 SQL is a focused additive migration, not a claim that full Flyway migration management is complete.
