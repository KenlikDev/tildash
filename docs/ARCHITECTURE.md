# Tildash Architecture

## Status

This document describes the architecture that is implemented today and the explicit boundaries that constrain upcoming feature work.

The repository currently provides a Kotlin Multiplatform client foundation, a small shared core, and a Spring Boot backend. Product domains such as identity, persistence, learning, content workflows, community, and AI are not implemented yet.

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

The current backend is still foundation-stage infrastructure. Public domain endpoints, persistence, identity, and application services are future work.

## Public API boundary

The public HTTP API is a transport contract, not a direct exposure of domain internals.

When API development begins:

- requests and responses use explicit DTOs;
- domain models are not serialized directly as the public contract;
- validation occurs at the boundary;
- authorization is enforced server-side;
- error responses are stable and documented;
- actuator and operational endpoints remain separate from the public API.

The API compatibility rules are defined in `docs/ENGINEERING_POLICIES.md`.

## Persistence boundary

Persistence is an infrastructure concern behind repository interfaces or equivalent application boundaries.

The repository currently has no production persistence implementation. When introduced, schema changes must use explicit, versioned migrations and must be compatible with the deployment strategy.

The migration policy is defined in `docs/ENGINEERING_POLICIES.md` and ADR-0004.

## Content and provenance boundary

Educational content must remain distinguishable from its provenance and moderation metadata.

The current repository does not yet implement the content model. ADR-0005 records the architectural boundary that future content features must preserve: content data, provenance, localization/version history, and review state are separate concepts.

## Review workflow boundary

Content and other user-generated changes must be reviewable through explicit workflow state rather than implicit side effects.

The current repository does not yet implement the review workflow. ADR-0006 records the architectural boundary to preserve when it is introduced.

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
