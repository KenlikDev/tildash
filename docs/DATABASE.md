# Tildash Database

## Status

This document describes the database and migration infrastructure implemented for the backend.

The current implementation provides the PostgreSQL/Flyway persistence substrate plus content workflow and server-side learner-attempt persistence. Domain-specific repositories are introduced only where their application contracts are defined.

## Database engine

The backend uses PostgreSQL 18.6.

Local development uses the pinned image:

```text
postgres:18.6-alpine3.24
```

The local service is defined in `compose.yaml`.

## Persistence technology

The backend uses Spring Data JDBC rather than JPA/Hibernate.

The choice is intentional: repository support remains aggregate-oriented, SQL stays close to PostgreSQL, and the backend does not adopt ORM lifecycle behavior before it is required.

No domain repository interface is introduced by this foundation because no persisted domain aggregate contract is sufficiently specified yet.

## Migration technology

Schema changes are managed by Flyway. Production migrations live under `server/src/main/resources/db/migration/` and use the `V<VERSION>__<DESCRIPTION>.sql` convention.

The first migration is `V1__create_application_schema.sql`, which creates the application-owned `tildash` schema. Content tables are introduced by V2/V3, learner-attempt persistence is introduced by V4, V5 adds lesson scope, V6 adds editable and immutable published exercise definitions, and V7 adds the published lesson version used to evaluate each learner attempt. V8 enforces that each published exercise belongs to the same content node as its published version. V9 restricts archival of a published node to the state and timestamp change; its immutable identity and structure fields must remain unchanged.

Flyway validation is enabled and Flyway clean is disabled.

## Runtime configuration

The local defaults are:

```text
JDBC URL:  jdbc:postgresql://localhost:5432/tildash
username:  tildash
password:  tildash-dev
```

Shared and production environments must provide `TILDASH_DATABASE_URL`, `TILDASH_DATABASE_USERNAME`, and `TILDASH_DATABASE_PASSWORD` externally.

The default development password is not a production credential.

Flyway stores its migration history in the application schema, `tildash.flyway_schema_history`. The schema is configured explicitly so history-table placement does not depend on PostgreSQL's session search path. Existing non-empty schemas without a migration history are not automatically baselined.

The one-command local acceptance demo uses a separate `tildash_demo` database and leaves the default development database untouched.

Flyway can be controlled with `TILDASH_FLYWAY_ENABLED`; it should remain enabled in normal deployments.

## Local development

Start PostgreSQL with:

```bash
docker compose up -d postgres
```

Start the backend with:

```bash
./gradlew :server:bootRun
```

Flyway runs automatically during application startup.

Stop the database with:

```bash
docker compose down
```

The named volume preserves local data. `docker compose down -v` removes that data and is intentionally destructive.

## Test database strategy

Server integration tests use Testcontainers with the same pinned PostgreSQL image used for local development.

Spring Boot `@ServiceConnection` supplies container connection details to the application context. This keeps the test database real PostgreSQL rather than an H2 compatibility layer.

Migration tests verify that the application schema is created from a fresh database and that migration V1 is recorded successfully in Flyway history.

The existing server HTTP and security tests use the same PostgreSQL test configuration so application startup and migration behavior are exercised in CI.

Docker is therefore a test-time prerequisite for the backend test suite.

## Transaction boundaries

Transactions belong at the application service / use-case boundary:

```text
HTTP/API
   |
Application Service / Use Case   <-- transaction boundary
   |
Domain
   |
Repository
   |
PostgreSQL
```

Repository operations participate in the caller transaction and should not introduce unrelated transaction scopes.

Multi-step state changes that must be atomic belong in one application-level transaction. The foundation does not add transactional annotations to speculative services that do not yet exist.

## Persistence boundary

Spring Data JDBC, JDBC access, Flyway, datasource configuration, and PostgreSQL-specific SQL belong to the server infrastructure boundary.

Application and domain code should depend on explicit repository/application contracts rather than JDBC connections, Spring Data implementations, Flyway APIs, PostgreSQL driver types, or JSONB persistence encodings.

The canonical content model is documented in `docs/CONTENT.md`.

## Learner-attempt identity

The learner-attempt table keeps `(learner_subject, attempt_id)` as the idempotency key and stores `lesson_id` and `published_version` as part of the immutable payload. The lesson/version scope prevents the same `exercise_id` from sharing progress across lessons or across published revisions of a lesson.

V5 adds `lesson_id` and V7 adds `published_version` as nullable columns so existing attempt history can migrate without destructive backfill assumptions. New authenticated API requests require both values. The server resolves the exercise from the exact immutable published version, recomputes the outcome from the submitted response, and stores the server-derived outcome rather than trusting the client-provided outcome. Legacy rows with missing lesson scope or published version are retained as historical data but are not eligible for authenticated synchronization until an explicit recovery workflow can establish their original context safely.

## Migration safety

Migrations are forward-only schema history. Already-applied migrations must not be silently edited. Corrective changes should use a new forward migration rather than destructive automatic downgrades.

## Security and operations

Database credentials are configuration, not source-controlled secrets. Production database access must use encrypted transport and deployment-managed credentials.

Actuator exposure remains limited to health. Database and Flyway metadata are not exposed through the public `/api/v1/**` namespace.

## Deferred scope

This backend persistence layer intentionally does not implement identity/account persistence, a server-side sync outbox, seed/reference-data lifecycle, optimistic locking for learner attempts, or backup/restore operations.