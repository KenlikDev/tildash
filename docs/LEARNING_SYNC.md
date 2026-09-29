# Tildash Learning Progress Synchronization

## Purpose

This document defines deterministic reconciliation for offline learner attempts, the client synchronization coordinator, and the implemented authenticated server synchronization transport. It does not implement downloaded-content storage or user-facing sync UI.

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

## Client sync coordinator

`LearningSyncCoordinator` reads the durable `LearningProgressStore` outbox, submits a deterministic `LearningProgressSyncBatch` to an injected `LearningSyncTransport`, and acknowledges only attempt IDs explicitly accepted by the transport.

Transport-reported conflicts remain in the outbox. Unknown acknowledgements and contradictory acknowledgement/conflict responses are rejected instead of silently mutating local state.

Transient transport failures are retried according to `LearningSyncRetryPolicy`. The coordinator requires an explicit suspendable delay implementation; it does not provide a no-op production fallback. Non-transient exceptions are propagated immediately.

The default retry policy is bounded: three total attempts, exponential delays starting at one second, and a thirty-second delay cap. The caller owns the actual scheduler/coroutine implementation used for the delay.

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
          +--> local persistence / LearningSyncCoordinator
          |
          +--> POST /api/v1/learning/sync
                    |
                    v
             server PostgreSQL persistence
```

The shared core reconciliation layer must not depend on HTTP clients/controllers, SQL databases, platform storage APIs, authentication frameworks, or AI providers. The concrete server transport lives outside core and binds authenticated identity to the PostgreSQL persistence boundary.

## Local persistence

`LearningProgressStore` persists immutable attempts and a durable synchronization outbox. Acknowledgement removes an outbox entry without deleting learning history. The storage contract is documented in `docs/LOCAL_STORAGE.md` and ADR-0013.

## Current server transport

The authenticated server endpoint is `POST /api/v1/learning/sync`.

The server accepts immutable attempts, scopes persistence by the authenticated learner subject, acknowledges new or identical attempts, and reports payload conflicts without overwriting stored history. The device ID remains metadata and does not affect idempotency.

The endpoint uses the API-wide RFC 9457 Problem Details contract for invalid requests and authentication failures.

## Concrete client HTTP transport

The concrete client transport is implemented by `KtorLearningSyncTransport` in `app/shared`.

It implements the provider-neutral `LearningSyncTransport` contract without changing the core reconciliation or coordinator rules.

The transport:

- sends `POST /api/v1/learning/sync`;
- authenticates with an injected `AccessTokenProvider`;
- serializes the canonical batch contract without persisting tokens;
- treats 401 as an authentication-required failure;
- treats 403 as an authorization failure;
- treats 408, 429, and 5xx responses as transient;
- preserves RFC 9457 problem details returned by the server for HTTP failures;
- rejects malformed successful responses as protocol failures;
- converts request-level network and timeout failures into `TransientLearningSyncFailure`;
- rethrows coroutine cancellation without retry classification.

Ktor engine implementations are selected per KMP target in `app/shared/build.gradle.kts`. The transport itself remains in common code.

The transport does not implement login, refresh-token storage, secure token persistence, or user-facing retry UI. Authentication/session storage remains a platform composition concern.


## Future work under #16

Remaining offline-first work includes wiring the client coordinator to a concrete HTTP client, retry/error presentation, sync observability, downloaded-content availability, secure-at-rest policy, and end-to-end offline recovery.

The durable design decision is recorded in ADR-0012.