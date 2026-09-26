# ADR-0001: Use Kotlin Multiplatform for the Client

- Status: Accepted
- Date: 2026-09-27

## Context

Tildash must provide a shared client architecture across Android, iOS, Desktop/JVM, and Web without duplicating business-facing application logic for every platform.

## Decision

Use Kotlin Multiplatform as the client architecture.

Shared application code lives in `app:shared`. Platform-specific source sets are used only for genuine platform concerns.

Compose Multiplatform is the UI layer for the shared client architecture.

The native iOS application at `app/iosApp` remains an Xcode application rather than a Gradle module and consumes the Kotlin/Native interop exported by `app:shared`.

## Alternatives considered

- Separate native client implementations for each platform.
- A single platform-specific client with remote rendering.
- A web-only client packaged for other platforms.

These alternatives would either duplicate client logic or compromise native platform integration.

## Consequences

- Shared application code must remain platform-neutral where possible.
- Platform APIs require explicit source-set boundaries.
- Web is a first-class client target rather than a later port.
- Native iOS integration remains partly outside Gradle.
