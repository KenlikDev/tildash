# ADR-0019: Platform-local learning application composition

## Status

**Accepted**

## Context

The learner client now has a shared lesson presentation boundary and durable SQLDelight storage contracts, but the Android, iOS, and Desktop application entry points still rendered the shared application without constructing those storage dependencies.

A production learner flow must open downloaded lessons from durable local storage and persist attempts without placing database or learning rules in presentation code.

## Decision

Supported native/JVM application hosts construct a persistent SQLDelight driver and pass it into a common application composition.

The common application composition constructs the existing SqlDelightDownloadedLessonStore and SqlDelightLearningProgressStore, then wires them into LearnerLessonCoordinator.

The application layer owns generated attempt IDs and obtains submission timestamps from an injected Clock. Presentation remains responsible only for ephemeral input and event rendering.

Android uses AndroidSqliteDriver with the application context and tildash.db. iOS uses NativeSqliteDriver in the application support directory. Desktop/JVM uses the existing SQLite JDBC driver under the user's ~/.tildash/tildash.db directory.

Web/Wasm is intentionally not included until a browser-appropriate durable driver is explicitly implemented.

## Alternatives considered

1. Create a speculative dependency-injection framework for the application. Rejected because the current composition has only a small number of concrete dependencies and does not require a framework.
2. Automatically select or seed a lesson at startup. Rejected because the repository has no verified content-seeding pipeline and startup must not fabricate product state.
3. Add an in-memory fallback for Web/Wasm. Rejected because it would conceal the absence of durable browser storage and violate the offline persistence contract.

## Consequences

- Android, iOS, and Desktop can consume the same shared learner application with durable local storage.
- Database creation remains a platform concern while storage and learning rules remain shared.
- Attempt identity and time generation are testable application dependencies.
- Web/Wasm remains an explicit follow-up rather than an unsupported persistence approximation.
- A later lesson-download/catalog capability can populate the existing local lesson list without changing the storage contract.
