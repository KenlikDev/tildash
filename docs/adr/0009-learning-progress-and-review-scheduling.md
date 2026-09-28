# ADR-0009: Deterministic Learning Progress and Review Scheduling

- Status: Accepted
- Date: 2026-09-28

## Context

The learner domain needs deterministic answer evaluation, progress tracking, mistake history, lesson completion, and review scheduling before server persistence, synchronization, and UI are introduced.

These rules must be reusable by all clients and the backend. Learner state must also support eventual synchronization, requiring idempotent attempts and deterministic ordering.

## Decision

The shared core owns the deterministic learning engine.

Each exercise has a stable ID and belongs to a stable content ID. Manual text input is the first supported exercise type.

Manual text answers are normalized by trimming surrounding whitespace and case normalization. Matching any normalized expected answer is CORRECT; otherwise the result is INCORRECT.

Each attempt has a stable attempt ID, exercise ID, response, evaluation outcome, and occurrence timestamp.

Attempts are canonicalized by `(occurredAt, attemptId)`. Repeating an identical attempt is idempotent. Reusing an attempt ID with different data is a conflict.

Progress is an immutable canonical attempt log. Review state, mistake counts, and lesson completion are derived from that log so late-arriving attempts can be incorporated without discarding earlier history.

The review scheduler uses fixed deterministic intervals of 0, 1, 3, 7, 14, and 30 days. Incorrect answers reset to stage 0 and are immediately due. Correct answers advance one stage up to stage 5.

Lesson completion occurs when every exercise required by the lesson has at least one correct attempt. The completion timestamp is the earliest canonical attempt that completes the required set.

Exercise evaluation is behind an evaluator boundary so future exercise types can add evaluation behavior without rewriting progress state or review scheduling.

The core remains framework-independent.

## Alternatives considered

### Server-only learning logic

Rejected because offline-capable clients need the same deterministic learning behavior.

### Arrival-order progress updates

Rejected because network retries and concurrent delivery can produce different results.

### Mutable counters without attempt history

Rejected because mistake history, replay, and synchronization require durable event-level data.

### AI-generated scheduling

Deferred until a deterministic baseline is stable and versioned.

## Consequences

Positive consequences:

- identical attempt sets produce identical progress;
- duplicate delivery can be handled idempotently;
- prior mistakes remain visible after later correct answers;
- review scheduling is deterministic and testable;
- future exercise evaluators can reuse the same progress model.

Trade-offs:

- derived state is recalculated from canonical attempts in this initial implementation;
- the scheduler will need an explicit version if its intervals change;
- persistence and synchronization must preserve attempt IDs, timestamps, and responses.

## Verification

Common tests cover answer normalization, outcomes, review intervals, mistakes, completion, idempotency, conflicts, and deterministic ordering.