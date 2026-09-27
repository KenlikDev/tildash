# Tildash Architecture

## Status

This document describes the architecture that is implemented today and the explicit boundaries that constrain upcoming feature work.

The repository currently provides a Kotlin Multiplatform client foundation, a small shared core, and a Spring Boot backend. Security, persistence, and the canonical content model are implemented; product domains such as community and AI are not implemented yet.

## Repository topology

Tildash is split into three architectural areas:

- `app/` — user-facing Kotlin Multiplatform applications.
- `core/` — framework-independent shared code and domain foundations.
- `server/` — Spring Boot backend.

The application area contains these Gradle modules:

- `app:shared`
- `app:androidApp`
- `app:desktopApp`
- `app:webApp`

The native iOS application at `app/iosApp` is intentionally **not** a Gradle module. It is an Xcode application that consumes Kotlin/Native interop from `app:shared`.

## Dependency direction

The intended dependency direction is:

```
Android / iOS / Desktop / Web
             |
             v
       app:shared
             |
             v
           core

server
   |
   v
 core
```

Platform entry points may depend on `app:shared`. Shared application code may depend on `core`. The backend may depend on `core`.

The core must not depend on UI frameworks, Spring, persistence implementations, or platform-specific APIs.

## Client architecture

Kotlin Multiplatform is the client architecture for:

- Android
- iOS
- Desktop/JVM
- Web

Compose Multiplatform is the presentation layer.

Shared business rules belong in common code. Platform source sets exist only for genuine platform concerns such as native UI hosting or platform APIs.

The client should evolve toward state-driven presentation with unidirectional data flow. Client presentation must not mirror the server's HTTP/controller structure.

## Backend architecture

The backend is Spring Boot with Kotlin.

The intended application boundary is:

```
HTTP/API
   |
   v
Application Service / Use Case
   |
   v
Domain
   |
   v
Repository / External Gateway
```

Controllers are transport adapters. They validate and translate external input but must not become containers for business rules.

The current backend remains foundation-stage infrastructure. Public domain endpoints and application services are future work; security, persistence, and content contracts are established as infrastructure/domain boundaries.

## Public API boundary

The public HTTP API is a transport contract, not a direct exposure of domain internals.

Implemented API foundation rules:

- public application endpoints use the `/api/v1/...` namespace;
- requests and responses use explicit DTOs;
- domain models are not serialized directly as the public contract;
- validation occurs at the HTTP boundary;
- authorization is enforced server-side;
- API errors use RFC 9457 Problem Details;
- actuator and documentation endpoints remain separate from the public API.

Detailed transport conventions are defined in `docs/API.md`.

The API compatibility rules are defined in `docs/ENGINEERING_POLICIES.md`.

## Persistence boundary

Persistence is an infrastructure concern behind repository interfaces or equivalent application boundaries.

The backend persistence foundation uses PostgreSQL, Spring Data JDBC, and Flyway. Schema changes are explicit, versioned migrations under `server/src/main/resources/db/migration/`.

No domain repository interface or aggregate table is introduced yet because no owning persistence contract has been defined. The persistence infrastructure remains behind application/domain boundaries.

Local PostgreSQL setup and the test database strategy are documented in `docs/DATABASE.md`. The durable decision is recorded in ADR-0004.

## Content and provenance boundary

Educational content is modeled as a generic stable-ID tree with explicit source revisions, provenance, localization revisions, and published version snapshots.

The canonical model is implemented in `core` and the first relational schema is implemented in `server` migration V2. The model supports courses, lessons, examples, vocabulary, and media references without language-specific persistence branches. Deterministic lesson validation is also implemented in `core` as a provider-neutral contract.

Source content and localization are separate revision streams. Provenance is first-class and carried by source revisions. Published versions reference immutable history so they can be reconstructed later.

The state vocabulary is draft, submitted, under review, approved, published, and archived. The state vocabulary is implemented now; transition workflow behavior remains future application work covered by ADR-0006.

Detailed domain rules are documented in `docs/CONTENT.md`; deterministic validation rules are documented in `docs/CONTENT_VALIDATION.md`; durable content boundaries are recorded in ADR-0005 and deterministic validation in ADR-0008.

## Review workflow boundary

Content authoring and publishing now use explicit workflow state rather than implicit side effects.

The current content-studio workflow supports draft editing, submission for review, reviewer feedback, approval, rejection, publication, archival, and immutable workflow history. ADR-0006 records the durable review workflow boundary.

## Cross-cutting rules

- Business rules remain independent from UI and infrastructure.
- Security decisions are enforced on the server.
- Configuration and secrets stay outside source-controlled code.
- Observability must not leak credentials, tokens, or unnecessary personal data.
- New abstractions must solve an observed problem; do not add speculative framework layers.
- Documentation must describe verified behavior and clearly mark future architecture as future.

## Architecture change process

A material architectural change requires:

1. an explicit problem statement;
2. an ADR when the decision affects a durable boundary;
3. implementation changes in the smallest coherent scope;
4. focused verification;
5. broader verification for affected targets;
6. documentation updated with the final verified behavior.


## Security boundary

Authentication and authorization are enforced at the server boundary before application use cases execute.

The implemented security flow is:

```
HTTP request
   |
   v
Spring Security filter chain
   |
   +--> authentication / bearer token validation
   |
   +--> request authorization
   |
   v
application-level identity
   |
   v
Application Service / Use Case
   |
   v
Domain
```

The security adapter owns Spring Security types. Application and domain code should consume application-level identity and authorization concepts rather than framework principals.

The supported application roles are learner, teacher, reviewer, and administrator. Method-level authorization is enabled for use cases that require finer-grained server-side decisions.

Authentication and authorization errors use the same RFC 9457 Problem Details contract defined in `docs/API.md`.

Security architecture, threat model, client authentication contract, and deployment requirements are defined in `docs/SECURITY.md` and ADR-0007.

Resource-server authentication is configurable and provider-neutral. Production deployments must supply a trusted issuer and HTTPS/TLS. Identity persistence remains behind the persistence boundary and is not part of the HTTP security adapter.
