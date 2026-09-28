# Tildash Local Learning Storage

## Scope

This slice provides durable local persistence for immutable learner attempts and the synchronization outbox.

Implementation:

app/shared/src/commonMain/kotlin/com/kenlikdev/tildash/storage/LearningProgressStore.kt

Database schema:

app/shared/src/commonMain/sqldelight/com/kenlikdev/tildash/storage/LearningStorage.sq

The storage implementation is client-side infrastructure. The domain model remains in `core` and is not coupled to SQLDelight.

## Persistence model

Each immutable LearningAttempt is stored as one row.

The current response contract supports the existing `LearnerResponse.Text` variant with an explicit response type discriminator. The discriminator is intentionally persisted so future response variants can be introduced without changing the idempotency key.

Attempt timestamps are stored as epoch milliseconds and reconstructed as kotlin.time.Instant.

## Sync outbox

Every newly persisted attempt is transactionally added to the local sync outbox.

The outbox stores only the canonical attempt ID because the complete immutable attempt already lives in the attempt table.

A successful remote acknowledgement removes the outbox row but never deletes the attempt history.

Conflicting reuse of an attempt ID is rejected with the existing AttemptIdConflict contract and does not overwrite the stored attempt or its outbox state.

## Transactions

Persisting a new attempt and enqueueing it for synchronization happen in one database transaction.

Acknowledging an attempt is a separate transaction that only removes its outbox entry.

Loading progress is deterministic and ordered by occurrence timestamp and attempt ID, matching the canonical ordering defined in the learning core.

## Database technology

SQLDelight generates typesafe Kotlin APIs from the SQLite schema. The current stack uses SQLDelight 2.4.0 with platform-specific drivers for Android, iOS/Native, and JVM.

Web/Wasm driver integration is intentionally deferred from this slice. The common storage contract does not depend on a platform-specific driver.

## Restart guarantee

The JVM integration tests open a file-backed SQLite database, persist attempts, close the driver, reopen the same database file, and verify that both the learning history and pending outbox survive.

This verifies durable storage rather than an in-memory test double.

## Platform boundary

Platform composition is responsible for constructing a persistent SQLDelight driver and injecting it into SqlDelightLearningProgressStore.

The storage implementation itself does not know whether the driver is backed by Android application storage, an iOS native SQLite file, or a JVM SQLite file.

Downloaded lesson content, authentication/session UI, sync observability, and secure-at-rest policy remain separate follow-up work under issue #16. A concrete authenticated server synchronization endpoint is now implemented; client transport wiring remains separate.

## Safety rules

Never use destructive schema recreation as normal application startup behavior.

Future local schema changes must be forward-compatible and versioned by SQLDelight migrations.

Never treat acknowledgement as permission to delete immutable learning history.

Never silently overwrite a stored attempt under an existing attempt ID.