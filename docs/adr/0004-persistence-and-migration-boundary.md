# ADR-0004: Isolate Persistence and Use Explicit Migrations

- Status: Accepted
- Date: 2026-09-27

## Context

The product requires durable storage, but database concerns must not leak into domain rules. Schema history also needs to be deterministic, source-controlled, and reproducible across local development and CI.

The foundation does not yet define any persisted product aggregate. Adding domain tables or repository interfaces without an owning application contract would create speculative architecture.

## Decision

The backend persistence boundary uses:

- PostgreSQL 18.6 as the relational database;
- Spring Data JDBC for repository infrastructure;
- Flyway for source-controlled schema migrations;
- Testcontainers PostgreSQL for server integration tests;
- Docker Compose for reproducible local database setup.

Persistence remains an infrastructure concern behind explicit application/domain contracts.

Schema changes are stored under `server/src/main/resources/db/migration/` and use Flyway versioned migration naming.

The first migration, `V1__create_application_schema.sql`, creates the application-owned `tildash` PostgreSQL schema. Domain tables will be introduced by the feature that owns the corresponding aggregate contract.

Transactions are owned by application services / use cases. Repository operations participate in the caller transaction rather than creating unrelated transaction scopes.

## Alternatives considered

### Spring Data JPA

Rejected for the current foundation because no use case requires ORM entity lifecycle, lazy loading, or Hibernate-specific behavior. Spring Data JDBC keeps the persistence model closer to aggregate-oriented application contracts.

### Ad-hoc schema changes

Rejected because manual or unversioned changes cannot provide a deterministic path from a supported baseline to the current schema.

### Liquibase

Not selected because Flyway provides the required versioned SQL migration model with less framework surface for the current persistence stage.

### H2 or another database emulator for integration tests

Rejected because persistence tests must exercise PostgreSQL behavior directly. Testcontainers provides the real database engine used by local development and deployment.

### Domain repositories without domain aggregates

Rejected as speculative. Repository interfaces will be introduced together with their owning use cases and aggregate contracts.

## Consequences

Positive consequences:

- persistence remains isolated from domain rules;
- migration history is explicit and reproducible;
- local development and CI use the same PostgreSQL engine;
- database-dependent tests do not rely on a manually prepared host database;
- future aggregate repositories can be introduced without changing the infrastructure boundary.

Trade-offs:

- Docker is required for backend integration tests;
- local backend startup requires a PostgreSQL instance;
- PostgreSQL-specific SQL is acceptable inside migrations and persistence adapters, but must remain outside domain code;
- database schema evolution requires deliberate forward migrations.

## Verification

The repository contains:

- a pinned Docker Compose PostgreSQL service;
- a Flyway V1 migration;
- Spring-managed Testcontainers PostgreSQL configuration;
- migration tests that verify schema creation and successful Flyway history;
- existing backend tests executing against the same PostgreSQL test configuration.
