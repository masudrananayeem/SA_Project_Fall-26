# V9 change log — security, deployment and evidence improvements

## Implemented in source
- Subscription listing is now tenant-scoped for customer users; admins retain the all-subscriptions view.
- Subscription plan-change history checks subscription ownership for customer users. Subscription mutations remain admin-only.
- Added a production Spring profile: secrets are required from environment variables, error messages are not exposed, Hibernate schema auto-update is disabled (`validate`), and Spring Batch metadata schema auto-initialisation is disabled.
- Added a test profile using in-memory H2 for lightweight repository/application tests.
- Added a GitHub Actions workflow to run Maven verification and frontend production build on pushes and pull requests.
- Added explicit Compose memory ceilings (5 GiB aggregate) and Redis health checks; PostgreSQL data remains persisted in the `pgdata` named volume.
- Added a deployment, database, backup and production-hardening guide.

## Important qualifications
- CI is configured but has not run in this environment. A green workflow is not claimed.
- Docker Compose startup, Maven tests, frontend build and load tests have not been executed here.
- Production schema migrations are not yet supplied as a complete Flyway set. The production profile expects a pre-provisioned schema and validates it.
- The current in-memory Basic-auth users are for coursework/demo only. Replace with a persistent identity provider before public multi-tenant deployment.
- Customer username must match the `customerId` value for customer-scoped reads in this demo.
- Spring Batch restart-safe billing, QA benchmark evidence, event-level invoice provenance, full historical simulation and external C10/C12 remain open.
