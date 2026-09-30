# ADR-0002: Use Spring Boot for the Backend

- Status: Accepted
- Date: 2026-09-27

## Context

The backend needs a stable Kotlin/JVM HTTP foundation with clear transport, application, domain, and infrastructure boundaries.

The repository has migrated the backend from Ktor to Spring Boot.

## Decision

Use Spring Boot with Kotlin for the backend.

The backend follows this boundary:

```
HTTP/API -> Application Service / Use Case -> Domain -> Repository / External Gateway
```

Spring MVC is the HTTP transport layer. Business rules do not belong in controllers.

## Alternatives considered

- Ktor as the backend framework.
- A custom HTTP stack.
- Putting domain behavior directly inside Spring controllers.

Ktor was superseded by the completed backend migration. A custom stack would add unnecessary infrastructure. Controller-centric business logic would make application rules harder to test and reuse.

## Consequences

- Spring Boot dependencies remain infrastructure concerns.
- Public API models must be explicit.
- Domain logic remains independently testable.
- Future persistence and external integrations must remain behind stable application/domain boundaries.
