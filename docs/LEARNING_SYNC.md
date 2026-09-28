# Tildash Learning Progress Synchronization

## Purpose

This slice defines the deterministic reconciliation contract for offline learner attempts. It does not implement HTTP transport, local database storage, authentication, or UI.

Implementation:

core/src/commonMain/kotlin/com/kenlikdev/tildash/learning/LearningProgressSync.kt

## Sync batch

A LearningProgressSyncBatch contains:

- `deviceId` — non-blank sender/device metadata;
- `attempts` — immutable LearningAttempt values.

Attempt IDs remain the idempotency keys. Device IDs do not change attempt identity.

## Merge rules

Given local progress and an incoming batch:

1. An attempt ID not present locally is accepted.
2. An attempt with the same ID and identical full payload is treated as an idempotent retry and acknowledged.
3. An attempt with the same ID but different data becomes a conflict.
4. Conflicting data is never selected by last-write-wins or timestamp.
5. Identical duplicate entries within one incoming batch collapse to one event.
6. Conflicting entries within one incoming batch remain a conflict and are not inserted.
7. The merged attempt history is canonically ordered by occurrence timestamp and then attempt ID.

## Acknowledgement

`acknowledgedAttemptIds` contains IDs that the sender can consider delivered:

- newly accepted attempts;
- identical attempts that were already present.

Conflict IDs are not acknowledged as successfully delivered.

`canAcknowledgeBatch` is false whenever any conflict exists.

The sender should keep unacknowledged/conflicted attempts in its durable outbox until a higher-level conflict workflow resolves them.

## Convergence

If two devices independently contain the same immutable attempt ID and payload, merging the other device's batch produces one canonical event.

The merge does not depend on arrival order. Canonical ordering remains deterministic because it is inherited from LearningProgress.

## Conflict semantics

A conflict means the same canonical attempt ID has different immutable event data.

Examples include a changed:

- exercise ID;
- response;
- outcome;
- occurrence timestamp.

Conflict handling is deliberately explicit. This slice does not invent a business rule that chooses a replacement event.

## Retry safety

Transport retries are safe as long as the sender resends the same immutable attempt payload with the same attempt ID.

An already accepted attempt is returned as acknowledged and does not create another event.

## Architecture boundary

```text
KMP client / offline store
          |
          v
LearningProgressSyncBatch
          |
          v
LearningProgressSync.merge
          |
          v
LearningProgress
          |
          v
local persistence / future sync transport
```

The shared sync layer must not depend on:

- HTTP clients or controllers;
- SQL databases;
- platform storage APIs;
- authentication frameworks;
- AI providers.

## Local persistence

`LearningProgressStore` persists immutable attempts and a durable synchronization outbox. Acknowledgement removes an outbox entry without deleting learning history. The storage contract is documented in `docs/LOCAL_STORAGE.md` and ADR-0013.

## Future work under #16

Remaining offline-first work includes local persistence, downloaded-content availability, sync transport, retry/backoff, observability, authentication boundaries, and end-to-end offline recovery.

The durable design decision is recorded in ADR-0012.