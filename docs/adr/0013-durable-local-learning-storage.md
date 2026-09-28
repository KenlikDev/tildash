# ADR-0013: Durable Local Learning Progress Storage

- Status: Accepted
- Date: 2026-09-28

## Context

LearningProgress and LearningProgressSync are framework-independent domain contracts. The learner client now needs durable local state so attempts survive application restarts and can remain available for later synchronization.

The storage boundary must not leak SQLDelight, SQLite, platform APIs, or HTTP concepts into the core learning model.

## Decision

Use SQLDelight-backed SQLite storage in `app/shared`.

The persisted model is:

- `learning_attempt` — immutable learner attempts;
- `learning_sync_outbox` — attempt IDs awaiting acknowledgement.

New attempt persistence and outbox insertion are one transaction.

An existing attempt ID with identical immutable data is idempotent.

An existing attempt ID with different data raises the existing `AttemptIdConflict` contract.

Remote acknowledgement removes only the outbox entry. Learning history remains durable.

The store reconstructs `LearningProgress` through the public `fromPersistedAttempts` factory, preserving deterministic canonical ordering without coupling storage to synchronization logic.

Platform-specific SQLDelight drivers are injected by client composition. This slice covers Android, iOS/Native, and JVM driver wiring; Web/Wasm driver integration is deferred.

## Alternatives considered

### JSON file snapshots

Rejected because durable attempt history plus an independently acknowledged outbox needs transactional mutation and selective row access. A full-file rewrite would add fragile concurrency and migration concerns.

### Shared preferences/key-value storage

Rejected because the attempt history is an append-oriented relational dataset and the sync outbox is a second durable set with transactional coupling.

### SQLDelight in core

Rejected because core must remain framework-independent and reusable by server and non-persistence tooling.

### Store only derived progress counters

Rejected because immutable attempt history is required for deterministic review scheduling, mistake tracking, conflict detection, and synchronization.

## Consequences

Positive:

- restart-safe learning progress;
- transactional local sync queue;
- deterministic rehydration;
- type-safe schema/API generation;
- clear separation between core learning rules and platform storage.

Trade-offs:

- SQLDelight becomes a client infrastructure dependency;
- local schema changes require explicit migrations;
- Web/Wasm still needs a dedicated driver/composition implementation.

## Verification

JVM integration tests exercise a real file-backed SQLite database and verify persistence after close/reopen, idempotent writes, conflict rejection, deterministic pending ordering, and acknowledgement semantics.