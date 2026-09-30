# Tildash Learner Client Composition

## Scope

This document defines the application composition boundary between shared learning rules and client persistence/presentation.

The current implementation is `LearnerLessonCoordinator` in `app/shared`.

## Flow

```text
DownloadedLessonStore
        |
        v
LearnerLessonCoordinator
        |
        +--> LearningProgressStore
        |
        v
LessonSession
        |
        v
LearnerLessonState
        |
        v
Presentation
```

`DownloadedLessonStore` provides the published offline lesson package. `LearningProgressStore` provides durable learner attempts. `LessonSession` remains the single source of truth for exercise sequencing, evaluation delegation, completion, and idempotency.

## Opening a lesson

`open(lessonId)` returns no state when the lesson is not present in downloaded storage.

When present, the coordinator loads durable learning progress and creates a `LessonSession` from the package plan and persisted-equivalent progress.

Opening a lesson therefore does not require network access.

## Submitting an answer

`submitText`:

1. reads the session's current exercise;
2. delegates the submission to `LessonSession`;
3. creates the immutable attempt using the returned evaluation;
4. persists that attempt through `LearningProgressStore`;
5. returns a new `LearnerLessonState`.

An attempt ID remains the idempotency key defined by the learning core. The coordinator does not replace or weaken that rule.

Failed answers remain persisted and the session keeps the same current exercise according to the existing core behavior.

Correct answers advance deterministically. Completing all exercises produces the existing `COMPLETED` session state.

## Boundaries

The coordinator contains no:

- HTTP client or server dependencies;
- SQLDelight queries;
- authentication/session token state;
- Compose-specific state management;
- answer-evaluation or review-scheduling logic.

Platform composition is a later concern. The current slice intentionally does not fabricate a storage fallback when a downloaded lesson is missing.

## Testing

Common tests verify:

- restoration from persisted progress;
- correct submission and advancement;
- incorrect submission and retry on the same exercise;
- durable persistence of every attempt;
- completion and recreation from persisted progress.

The shared Compose presentation boundary is `LearnerLessonScreen` in `app/shared`. It consumes only an existing `LearnerLessonState` and exposes answer submission through an explicit callback. It owns ephemeral answer-input state but does not generate attempt IDs, timestamps, evaluate answers, select exercises, persist attempts, or synchronize progress.

The application `App` surface now acts as a neutral host: it renders the lesson screen when an already-open learner state is supplied and otherwise shows a non-interactive lesson-selection state. Platform entry points remain responsible for constructing future storage/application composition.

The next client slice can consume `LearnerLessonState` from a UI without moving learning rules into presentation code.