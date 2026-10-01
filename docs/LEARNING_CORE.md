# Tildash Learning Core

## Scope

This document specifies the first deterministic learning-domain slice for the learner experience.

Implemented in:

core/src/commonMain/kotlin/com/kenlikdev/tildash/learning/LearningProgress.kt

The learning core is framework-independent and does not depend on Spring, PostgreSQL, HTTP, audio decoders, platform APIs, or AI providers.

## Learning plans and exercises

A LearningPlan identifies one lesson and its required exercises.

The current supported exercise is ManualInputExercise. It contains a stable exercise ID, stable content ID, prompt, and one or more expected text answers.

Expected answers must be unique after trimming and case normalization.

Exercise evaluation is isolated behind ExerciseEvaluator so future exercise types can supply separate evaluation logic without changing progress state or scheduling.

## Answer evaluation

Manual input normalizes text by trimming surrounding whitespace and applying case normalization.

Evaluation returns CORRECT when any expected answer matches, otherwise INCORRECT.

The same exercise and response always produce the same result.

## Published learner catalog

`LearningCatalogProjector` is the deterministic read-side boundary for learner-visible published courses and lessons. It selects the latest published snapshot per content ID, requires valid COURSE -> LESSON hierarchy, orders output deterministically, and exposes immutable learner read models without persistence or HTTP types. The detailed contract is documented in `docs/LEARNING_CATALOG.md` and ADR-0011.

## Lesson sessions

`LessonSession` is the deterministic orchestration boundary between a `LearningPlan` and the learner-facing layer. It exposes `ACTIVE` or `COMPLETED` state, selects the first plan exercise without a correct attempt, and delegates submission to `LearningEngine`. It can be recreated from an existing `LearningProgress` without changing attempt history.

A failed attempt keeps the current exercise pending; a correct attempt advances to the next pending exercise. Once all required exercises have a correct attempt, the session is explicitly completed and rejects further submissions.

The client application composition in `app/shared` uses `LearnerLessonCoordinator` to open downloaded lessons from durable storage, restore persisted progress, submit through `LessonSession`, and persist immutable attempts. It does not introduce a second learning-rule implementation. The boundary is documented in `docs/LEARNING_CLIENT.md` and ADR-0017.

The dedicated contract is documented in `docs/LEARNING_SESSION.md` and ADR-0010.

## Attempts

A LearningAttempt contains attemptId, lessonId, exerciseId, response, outcome, and occurredAt. The logical exercise identity is scoped by `(lessonId, exerciseId)`. New client-generated attempts always carry the lesson ID; legacy persisted attempts may have a null lesson ID and are not eligible for lesson-scoped progress.

Attempt IDs are immutable idempotency keys across the synchronization boundary.

Repeating an identical attempt ID with the same lesson ID, exercise ID, response, and timestamp is a no-op.

Reusing an existing attempt ID with any different immutable payload, including lesson ID, is rejected as AttemptIdConflict.

Lesson completion, review state, and mistake counts are scoped to the lesson ID as well as the exercise ID so an exercise reused in another lesson cannot inherit progress.

Attempts are canonically ordered by occurrence time and then attempt ID. This makes concurrent equal-time submissions deterministic and allows late-arriving attempts to be folded without replacing prior history.

## Progress

LearningProgress stores immutable canonical attempt history.

Derived state includes review state per exercise, cumulative mistake count per exercise, lesson completion, and the earliest lesson completion timestamp.

An incorrect attempt does not erase earlier correct attempts or mistakes.

## Review scheduling

ReviewScheduler uses six deterministic stages:

| Stage | Interval after a correct answer |
| ---: | ---: |
| 0 | 0 days |
| 1 | 1 day |
| 2 | 3 days |
| 3 | 7 days |
| 4 | 14 days |
| 5 | 30 days |

An incorrect answer resets the exercise to stage 0 and makes it immediately due.

A correct answer advances one stage, up to stage 5.

The initial scheduler is intentionally fixed and deterministic. Adaptive algorithms are a separate versioned behavior change.

## Lesson completion

A lesson becomes complete when every exercise in its LearningPlan has at least one correct attempt.

The completion timestamp is the earliest canonical attempt at which the full required exercise set becomes complete.

Completion is therefore reproducible from the attempt history and is not derived from client UI state.

## Synchronization implications

The model is designed for later persistence and offline synchronization:

- attempt IDs remain stable across retries;
- exact occurrence timestamps are preserved;
- duplicate delivery is idempotent;
- attempts have deterministic canonical ordering;
- history is not collapsed into mutable counters only.

Durable local persistence, the synchronization outbox, the concrete synchronization transport, and deterministic client synchronization orchestration are implemented in the client boundaries. End-to-end offline acceptance and remaining platform/application composition work remain separate follow-up tasks.

## Synchronization

Deterministic reconciliation for offline learner attempts is implemented by `LearningProgressSync`. It merges immutable `LearningAttempt` values idempotently, preserves canonical ordering, acknowledges accepted/replayed IDs, and surfaces conflicting reuse of an attempt ID without overwriting either event. The contract is documented in `docs/LEARNING_SYNC.md` and ADR-0012.

## Non-goals

This domain slice does not implement HTTP/API transport adapters, PostgreSQL persistence, audio transport/decoding, learner UI, or AI tutor behavior. Those concerns are implemented or deferred in their respective application/infrastructure boundaries.

The parent issue #15 remains open until the end-to-end learner journey and client acceptance criteria are implemented.

## Verification

Common tests cover normalized text evaluation, incorrect-answer behavior, deterministic SRS progression, cumulative mistakes, lesson completion, attempt idempotency, attempt conflicts, deterministic tie-breaking, and invalid empty plans.