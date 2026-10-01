# Tildash Learner Client Composition

## Scope

This document defines the application composition boundary between shared learning rules and client persistence/presentation.

The current implementation uses `LearnerLessonApplication` and `LearnerLessonCoordinator` in `app/shared`. The Desktop host also composes the published learner HTTP API into the download path.

## Flow

```text
Platform SQLDelight driver
        |
        v
LearnerLessonApplication
        |
        +--> DownloadedLessonStore
        |
        +--> LearningProgressStore
        |
        v
LearnerLessonCoordinator
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

`DownloadedLessonStore` provides the published offline lesson package. `LearningProgressStore` provides durable learner attempts. `LearnerLessonCoordinator` remains the single application boundary for lesson opening and submission, while `LessonSession` remains the single source of truth for exercise sequencing, evaluation delegation, completion, and idempotency.

## Opening a lesson

`LearnerLessonApplication.listDownloadedLessons()` reads the current local lesson packages. Selecting a lesson calls `LearnerLessonApplication.openLesson(lessonId)`, which delegates to the existing coordinator.

`openLesson` returns no state when the lesson is not present in downloaded storage.

When present, the coordinator loads durable learning progress and creates a `LessonSession` from the package plan and persisted-equivalent progress.

Opening a lesson therefore does not require network access.

## Submitting an answer

`LearnerLessonApplication.submitText` supplies the immutable attempt metadata required by the coordinator:

1. a new random UUID for the attempt ID;
2. the injected `Clock` for the occurrence timestamp;
3. the learner response value.

The coordinator then delegates evaluation and sequencing to `LessonSession` and persists the resulting immutable attempt through `LearningProgressStore`.

Presentation never generates attempt IDs or timestamps.

Failed answers remain persisted and the session keeps the same current exercise according to the existing core behavior.

Correct answers advance deterministically. Completing all exercises produces the existing `COMPLETED` session state.

## Presentation boundary

`LearnerLessonScreen` consumes an already-open `LearnerLessonState` and exposes answer submission through a callback. It owns only ephemeral answer-input state.

The shared `App` surface now also renders the locally downloaded lesson list when a `LearnerLessonApplication` is supplied. An empty store renders an explicit empty state rather than creating content.

## Platform composition

Android, iOS, and Desktop/JVM entry points construct the platform-specific SQLDelight driver and pass it into `createLearnerLessonApplication`.

Web/Wasm builds intentionally do not construct this persistence composition. They remain explicit follow-up work until a browser-appropriate durable driver is introduced.

## Boundaries

The application composition contains no:

- authentication/session token state;
- HTTP client or server dependencies;
- raw SQLDelight queries in presentation;
- answer-evaluation or review-scheduling logic;
- fake persistence fallback for unsupported targets.

Platform composition is responsible only for genuine platform storage concerns. Learning rules remain shared and presentation remains state-driven.

## Testing

Common tests verify application-level lesson listing/opening and deterministic injection of attempt metadata, while existing coordinator and storage tests continue to verify learning and durability semantics.
## Online-to-offline learner flow

The Desktop composition uses the published learner API as the source of content:

    Published learner catalog
            |
            v
    LearnerContentTransport
            |
            v
    LearnerContentApplication.downloadLesson
            |
            v
    DownloadedLessonStore
            |
            v
    LearnerLessonApplication
            |
            v
    LessonSession

The client exposes Catalog, Downloaded, and Lesson states.

Download writes the complete published lesson package to local SQLDelight storage. Remove deletes that local package. Open reads the package from local storage and therefore does not require a network connection after download.

The Desktop application defaults to http://127.0.0.1:8080. TILDASH_API_URL overrides the URL. TILDASH_ACCESS_TOKEN supplies a real bearer token when an identity provider is configured. TILDASH_DEVELOPMENT_ROLE defaults to learner for the explicit local-development server mode.
