# ADR-0018: Scope Learner Attempts to Lesson Identity

- Status: Accepted
- Date: 2026-09-29

## Context

The learner content model permits the same exercise identifier to be reused in different lessons. The local downloaded-exercise schema already reflects this by using (lesson_id, exercise_id) as the exercise identity.

The learning progress model, however, historically matched attempts by exerciseId alone. That allows a correct attempt from one lesson to affect completion, review state, or mistake counts for another lesson that reuses the same exercise ID.

The synchronization boundary also persisted and compared attempts without the lesson scope.

## Decision

A new learner attempt is scoped to the lesson that contains the exercise.

The logical exercise identity is:

(lessonId, exerciseId)

The immutable idempotency key remains:

attemptId

Therefore:

- attemptId identifies one immutable learner event for idempotency;
- lessonId identifies the lesson in which the event occurred;
- exerciseId identifies the exercise within that lesson;
- all immutable fields, including lessonId, participate in conflict detection.

New attempts produced through LessonSession and LearningEngine carry the lesson ID.

Lesson completion, review state, and mistake counts use the lesson scope when evaluating progress.

The local SQLDelight attempt table stores lesson_id.

The authenticated synchronization API requires lessonId in every new request.

The PostgreSQL attempt table stores lesson_id, while (learner_subject, attempt_id) remains the idempotency key.

Legacy persisted rows created before this decision may contain a null lesson ID. They remain historical records and are not eligible for the new authenticated sync transport or lesson-scoped completion calculations.

## Alternatives considered

### Keep exercise ID globally unique

Rejected because the canonical downloaded-content model already scopes exercise IDs by lesson, and global uniqueness would add an unnecessary content authoring restriction.

### Change the idempotency key to include lesson ID

Rejected because attemptId is already the stable client-generated event key used across local persistence and synchronization. Changing it would complicate retry semantics without solving the exercise identity problem.

### Infer lesson ID from exercise ID

Rejected because the same exercise ID can exist in multiple lessons and therefore cannot uniquely determine the lesson.

### Allow cross-lesson progress sharing

Rejected because lesson completion and review scheduling are defined over a specific learning plan. Sharing progress would make behavior depend on unrelated lessons.

## Consequences

Positive:

- learner progress is isolated between lessons;
- offline and server synchronization use the same immutable identity contract;
- exercise identifiers can be reused safely across lessons;
- conflict detection becomes complete for the attempt payload;
- the invariant is testable across core, local storage, transport, API, and PostgreSQL.

Trade-offs:

- the local schema requires a forward migration;
- legacy unscoped attempts need explicit handling;
- all new sync clients must send lessonId.

## Verification

Tests cover:

- cross-lesson exercise ID reuse in the learning core;
- lesson-scoped local persistence;
- lesson-scoped sync conflicts;
- wire serialization of lessonId;
- server-side cross-lesson conflict detection;
- immutable PostgreSQL persistence remains intact.

The implementation is documented in docs/LEARNING_CORE.md, docs/LEARNING_SYNC.md, docs/LOCAL_STORAGE.md, docs/API.md, and docs/DATABASE.md.