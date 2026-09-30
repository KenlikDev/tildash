# ADR-0012: Deterministic Learning Progress Synchronization

- Status: Accepted
- Date: 2026-09-28

## Context

Learning attempts are immutable canonical events and may be created offline on multiple client devices. The synchronization layer must tolerate retries, duplicate delivery, reordering, and conflicting reuse of an attempt identifier.

Network transport and local persistence are intentionally not part of this slice. The shared core therefore needs a framework-independent reconciliation contract that those later layers can call.

## Decision

Synchronization operates on immutable LearningAttempt values.

A sync batch contains a non-blank device identifier and zero or more attempts.

When merging a batch into local progress:

- an unseen attempt ID is accepted exactly once;
- an identical attempt already present locally is acknowledged as an idempotent duplicate;
- an attempt ID carrying different data is a conflict and is never overwritten;
- duplicate identical entries inside the same batch collapse to one event;
- conflicting entries for one ID inside the same batch remain an explicit conflict;
- accepted attempts are merged with existing attempts using the canonical `(occurredAt, attemptId)` ordering already defined by ADR-0009.

The merge result returns:

- the resulting immutable LearningProgress;
- acknowledged attempt IDs that may be removed from a sender outbox after successful delivery;
- explicit conflicts that require caller-visible handling.

A batch containing conflicts is not fully acknowledgeable. Conflict resolution must not mutate an existing immutable attempt or silently select one payload over another.

The device identifier is routing/observability metadata for the sync protocol. It is not part of the immutable attempt identity because attempt IDs already provide the idempotency key.

## Alternatives considered

### Last-write-wins by timestamp

Rejected because an immutable learner attempt must not be silently rewritten during synchronization.

### Device-specific attempt IDs

Rejected because it would make deduplication and cross-device convergence dependent on transport metadata rather than the canonical attempt identity.

### Arrival-order merge

Rejected because retries and network reordering would produce different progress states.

## Consequences

Positive consequences:

- repeated delivery is safe;
- independent devices converge on identical immutable attempts;
- conflicts remain observable instead of being hidden;
- the same merge behavior can be reused by client storage and backend synchronization;
- the shared model remains independent of HTTP, database, and platform implementations.

Trade-offs:

- conflicts need an explicit higher-level resolution workflow;
- transport must preserve attempt IDs and complete attempt payloads;
- sender outboxes need to retain attempts until their IDs are acknowledged.

## Verification

Common tests cover new-event merge, idempotent retry, cross-device convergence, conflicts, deterministic ordering, duplicate batch entries, and acknowledgement semantics.

Transport, persistence, retry backoff, and authentication remain follow-up concerns under issue #16.