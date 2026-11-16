# ADR-005: Spring Boot 3.5 baseline

**Status:** Accepted (V18)

## Context
Up to V17 the POM declared Spring Boot 4.0.0, but the code was written against Boot 3 APIs:
Spring Batch 5 packages (`org.springframework.batch.core.Job`, `org.springframework.batch.item.*`),
Jackson 2 (`com.fasterxml.jackson.*`) and Flyway auto-configuration triggered by `flyway-core`.
Boot 4 moves Batch core types (Batch 6), defaults to Jackson 3 (`tools.jackson`) and needs
`spring-boot-starter-flyway`. The project therefore could not compile.

## Decision
Pin the build to the last Boot 3 line: Spring Boot 3.5.16, Spring Modulith 1.4.14, springdoc 2.8.13,
Java 21. All application code stays on stable, documented APIs.

## Consequences
- The code compiles against the APIs it was written for.
- Open-source support for Boot 3.5 ended in June 2026, so a Boot 4 migration is future work:
  rename Batch imports, adopt Jackson 3 (or `spring.jackson.use-jackson2-defaults`), add
  `spring-boot-starter-flyway` and `spring-boot-starter-webmvc`.
