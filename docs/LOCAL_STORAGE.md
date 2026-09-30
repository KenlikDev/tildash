# Tildash Local Learning Storage

## Scope

This slice provides durable local persistence for immutable learner attempts and the synchronization outbox.

Implementation:

app/shared/src/commonMain/kotlin/com/kenlikdev/tildash/storage/LearningProgressStore.kt

Database schema:

app/shared/src/commonMain/sqldelight/com/kenlikdev/tildash/storage/LearningStorage.sq

The storage implementation is client-side infrastructure. The domain model remains in `core` and is not coupled to SQLDelight.

## Persistence model

Each immutable LearningAttempt is stored as one row. New attempts include the lesson ID so the same exercise ID can be reused safely across different lessons without sharing progress.

The current response contract supports the existing `LearnerResponse.Text` variant with an explicit response type discriminator. The discriminator is intentionally persisted so future response variants can be introduced without changing the idempotency key.

Attempt timestamps are stored as epoch milliseconds and reconstructed as kotlin.time.Instant.

## Downloaded learning content

The local client also persists downloaded lesson packages required for offline delivery.

A downloaded package contains:

- course metadata and published version;
- lesson metadata and published version;
- the lesson's executable learning plan;
- the exercise type, content ID, prompt, and expected answers.

The current persisted exercise contract covers ManualInputExercise. Unknown future exercise types fail closed during rehydration instead of being silently downgraded.

Saving or replacing a lesson package is transactional. Replacing a package removes the previous lesson rows before inserting the new published version, while preserving unrelated lessons in the same course.

Offline content is a cache of an explicitly published version; it is not a mutable source of truth for authoring data.

## Sync outbox

Every newly persisted attempt is transactionally added to the local sync outbox.

The outbox stores only the canonical attempt ID because the complete immutable attempt already lives in the attempt table.

A successful remote acknowledgement removes the outbox row but never deletes the attempt history.

Conflicting reuse of an attempt ID is rejected with the existing AttemptIdConflict contract when any immutable field differs, including lesson ID, and does not overwrite the stored attempt or its outbox state.

## Transactions

Persisting a new attempt and enqueueing it for synchronization happen in one database transaction.

Acknowledging an attempt is a separate transaction that only removes its outbox entry.

Loading progress is deterministic and ordered by occurrence timestamp and attempt ID, matching the canonical ordering defined in the learning core. Legacy rows created before lesson scoping may have a null lesson ID and are retained as history but are not eligible for the new authenticated sync transport.

## Database technology

SQLDelight generates typesafe Kotlin APIs from the SQLite schema. The current stack uses SQLDelight 2.4.0 with platform-specific drivers for Android, iOS/Native, and JVM.

Web/Wasm driver integration is intentionally deferred from this slice. The common storage contract does not depend on a platform-specific driver.

## Restart guarantee

The JVM integration tests open a file-backed SQLite database, persist attempts, close the driver, reopen the same database file, and verify that both the learning history and pending outbox survive.

This verifies durable storage rather than an in-memory test double.

## Platform boundary

Platform composition is responsible for constructing a persistent SQLDelight driver and injecting it into SqlDelightLearningProgressStore.

The storage implementation itself does not know whether the driver is backed by Android application storage, an iOS native SQLite file, or a JVM SQLite file.

Authentication/session UI and platform-specific encrypted-at-rest drivers remain composition concerns. The shared storage layer never persists access or refresh tokens.

## Safety rules

Never use destructive schema recreation as normal application startup behavior.

Future local schema changes must be forward-compatible and versioned by SQLDelight migrations.

Never treat acknowledgement as permission to delete immutable learning history.

Never silently overwrite a stored attempt under an existing attempt ID.