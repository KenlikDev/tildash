# ADR-0017: Keep Learner Lesson Composition Above Core and Storage

- Status: Accepted
- Date: 2026-09-29

## Context

The repository now has deterministic learning core behavior, durable local progress storage, downloadable lesson packages, and synchronization infrastructure. A learner application flow needs to compose these pieces without moving persistence or presentation concerns into core learning rules.

## Decision

Implement the first learner application composition as `LearnerLessonCoordinator` in `app/shared`.

The coordinator:

- opens a lesson only from `DownloadedLessonStore`;
- restores `LearningProgress` through `LearningProgressStore`;
- starts the framework-independent `LessonSession` from the downloaded lesson plan;
- submits text through `LessonSession` and therefore reuses existing answer evaluation, progress, completion, and idempotency rules;
- persists the resulting immutable `LearningAttempt` through `LearningProgressStore`;
- returns immutable `LearnerLessonState` for presentation.

The coordinator does not:

- implement answer evaluation;
- implement review scheduling;
- access SQLDelight directly;
- send HTTP requests;
- manage authentication;
- generate UI state through Compose APIs;
- invent fallback content when offline data is unavailable.

Platform composition remains responsible for constructing durable stores and the future presentation layer consumes `LearnerLessonState`.

## Consequences

Positive:

- the same learner rules are reused by offline application code;
- local persistence remains behind explicit store contracts;
- the client can resume a downloaded lesson from durable progress;
- application composition remains testable without a UI runtime.

Trade-offs:

- a production app still needs platform driver composition and presentation wiring;
- the current offline lesson package supports the currently persisted ManualInputExercise type.

## Verification

Common tests cover opening from persisted progress, correct and incorrect submissions, durable attempt persistence, lesson completion, and session resume.

The parent learner experience remains open under issue #15 until course navigation, presentation, media, and complete end-to-end client composition are implemented.