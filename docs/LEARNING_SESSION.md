# Tildash Lesson Session

## Purpose

LessonSession is the deterministic execution boundary between a LearningPlan and the learner-facing orchestration layer.

It does not evaluate answers itself. It delegates attempts to the existing LearningEngine and exposes the next exercise from the immutable plan.

## Session state

A session is:

- ACTIVE while at least one required exercise has no correct attempt;
- COMPLETED after every required exercise has at least one correct attempt.

An ACTIVE session always has a next exercise. A COMPLETED session has no next exercise.

## Exercise selection

Next-exercise selection is deterministic:

1. traverse LearningPlan exercises in their declared order;
2. skip exercises that already have a CORRECT attempt in LearningProgress;
3. return the first remaining exercise.

The session does not reorder the plan, invent exercises, or derive a second ordering policy.

## Submission

LessonSession.submit delegates to LearningEngine.

The session therefore inherits:

- manual-input normalization;
- deterministic answer evaluation;
- attempt idempotency;
- attempt conflict detection;
- canonical attempt ordering;
- mistake tracking;
- review scheduling.

The session layer does not duplicate those rules.

A failed attempt leaves the current exercise pending. A successful attempt advances selection to the next pending exercise.

## Resume

A session can be recreated from an existing LearningPlan and LearningProgress without changing the progress history.

This is intentionally equivalent to restoring persisted progress later. Persistence and synchronization are separate infrastructure concerns.

## Completion

When the final required exercise receives a correct attempt, the returned session is explicitly COMPLETED and exposes no next exercise.

A COMPLETED session rejects further submissions.

## Non-goals

This slice does not define:

- HTTP transport;
- persistence;
- offline synchronization;
- learner UI state;
- media playback;
- AI behavior.

The boundary is intentionally framework-independent and reusable by server and KMP clients.
