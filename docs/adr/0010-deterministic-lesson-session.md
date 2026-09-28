# ADR-0010: Deterministic Lesson Session Boundary

- Status: Accepted
- Date: 2026-09-28

## Context

The deterministic learning engine now evaluates attempts and derives progress, but a learner journey still needs a small orchestration boundary that selects the next required exercise and resumes from existing progress.

That orchestration must not duplicate answer evaluation or progress rules.

## Decision

Introduce an immutable LessonSession in the shared core.

LessonSession contains a LearningPlan and LearningProgress and exposes:

- ACTIVE or COMPLETED state;
- the next pending exercise in LearningPlan order;
- submit(), which delegates to LearningEngine;
- start(plan, progress) for deterministic resume.

An exercise is considered complete for session sequencing only after a CORRECT attempt exists for its ID.

A failed attempt does not advance the session.

When every required exercise is complete, the session becomes COMPLETED and rejects further submissions.

No new evaluator or scheduling rule is introduced by this boundary.

## Alternatives considered

### Client-owned exercise sequencing

Rejected because Android, iOS, Desktop, Web, and future server orchestration could diverge on sequencing behavior.

### Session-level answer evaluation

Rejected because it would duplicate LearningEngine rules and allow behavior drift.

### Mutable session object

Rejected because immutable session snapshots are easier to resume, test, and synchronize.

## Consequences

Positive consequences:

- deterministic learner sequencing;
- one source of truth for attempt behavior;
- explicit completion semantics;
- straightforward restoration from persisted-equivalent progress;
- reusable across client and server layers.

Trade-offs:

- the plan order becomes part of the session contract;
- progress must be preserved externally when sessions need to survive process restarts.

## Verification

Common tests cover new-session selection, skipping completed exercises, failed attempts, delegation, idempotency, resume, completion, and completed-session rejection.