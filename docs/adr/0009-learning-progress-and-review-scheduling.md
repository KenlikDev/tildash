# ADR-0009: Deterministic Learning Progress and Review Scheduling

- Status: Accepted
- Date: 2026-09-28

## Context

The learner domain needs deterministic answer evaluation, progress tracking, mistake history, lesson completion, and review scheduling before server persistence, synchronization, and UI are introduced.

These rules must be reusable by all clients and the backend. The learner state must also support eventual synchronization, which requires idempotent attempts and deterministic ordering.

## Decision

The shared core owns the deterministic learning engine.

An exercise is identified by a stable exercise ID and belongs to a stable content ID. The current supported exercise type is manual text input.

Answer normalization for manual input is trimming surrounding whitespace and case normalization. An answer matches when any normalized expected answer equals the normalized learner response.

Each learner attempt has a stable attempt ID, exercise ID, learner response, deterministic evaluation outcome, and occurrence timestamp.

Attempts are canonicalized by `(occurredAt, attemptId)`. Repeating the same attempt ID with identical data is idempotent. Reusing an attempt ID with different exercise, response, or timestamp is a conflict.

Progress is represented as an immutable canonical attempt log. Derived review state, mistake counts, and lesson completion are calculated from that log, which allows late-arriving attempts to be merged deterministically without discarding prior history.

The review scheduler uses fixed deterministic stages with intervals of 0, 1, 3, 7, 14, and 30 days.

- incorrect -> stage 0 and due immediately;
- correct -> advance one stage up to the maximum stage and schedule at the corresponding interval.

Lesson completion is reached when every exercise required by the lesson has at least one correct attempt. Completion time is the earliest canonical attempt at which the full required set becomes complete.

Current exercise evaluation is isolated behind an evaluator interface so future exercise types can add their own evaluator without changing the progress state model or scheduling engine.

The core does not persist data, perform network calls, decode audio, invoke AI providers, or depend on Spring.

## Alternatives considered

### Server-only progress logic

Rejected because clients need the same deterministic behavior for offline-capable learning and conflict-free synchronization.

### Timestamp-free attempt ordering

Rejected because concurrent attempts can be observed in different arrival orders. Canonical `(occurredAt, attemptId)` ordering gives deterministic folding.

### Mutable counters only

Rejected because counters lose the attempt history needed for mistake tracking, deterministic replay, and future synchronization.

### Adaptive or AI-generated intervals

Deferred. The first scheduling contract must be deterministic and testable before adaptive scheduling is introduced.

## Consequences

Positive consequences:

- identical attempt sets produce identical progress state;
- duplicate network/sync delivery can be handled idempotently;
- previous mistakes remain visible after later correct attempts;
- SRS behavior is deterministic and easy to test;
- future exercise evaluators can be added without coupling the progress state to one exercise type.

Trade-offs:

- derived state is currently recalculated from attempt history;
- the initial SRS schedule is intentionally simple and may later need a versioned algorithm contract;
- persistence and synchronization layers must preserve attempt IDs, timestamps, and exact response payloads.

## Verification

Common tests cover answer evaluation, normalization, SRS stage/interval progression, mistake accumulation, lesson completion, idempotency, attempt conflict detection, and deterministic ordering.