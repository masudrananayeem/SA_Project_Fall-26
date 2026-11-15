# Database migrations (V15)

## Fresh production database
The production profile uses Flyway and Hibernate `ddl-auto: validate`. A clean PostgreSQL database is initialized from `backend/src/main/resources/db/migration/V1__meterflow_baseline.sql`. Spring Batch metadata tables are initialized by Spring Boot Batch schema initialization. Never use Hibernate `update` in production.

1. Create an empty PostgreSQL database and a restricted application user.
2. Back up any existing database before migration work.
3. Deploy with `SPRING_PROFILES_ACTIVE=prod`; Flyway validates and applies V1 before Hibernate validates the entity model.
4. Verify `flyway_schema_history`, application tables, Spring Batch metadata tables and `/actuator/health`.

## Existing V14 database
V1 is a clean-install baseline, not an automatic conversion script for an already-populated V14 database. Do not point V15 production at an existing database and expect Flyway to adopt it automatically. First clone the database, compare every table/column/index against V1 and the JPA entities, prepare a reviewed forward migration, and test restore/rollback. Baseline an existing database only after its schema has been independently proven equivalent.

## Rollback
Flyway migrations are forward-only. Restore the pre-deploy PostgreSQL backup or apply a separately reviewed compensating migration. Keep backups until the rollback window expires.
