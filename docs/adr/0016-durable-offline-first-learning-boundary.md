# ADR-0016: Durable Offline-First Learning Boundary

- Status: Accepted
- Date: 2026-09-29

## Context

The learner client must continue to deliver downloaded lessons without network access and must preserve learner progress until the authenticated server transport can acknowledge it.

The repository already provides deterministic learning rules, durable local progress/outbox storage, deterministic synchronization, an authenticated Ktor transport, and a published learner catalog.

The remaining offline-first boundary must connect durable downloaded lesson packages with the existing learning session and progress contracts without coupling core learning behavior to SQLDelight, Ktor, or platform UI.

## Decision

Use a client storage boundary with two durable responsibilities:

1. DownloadedLessonStore persists explicitly published lesson packages required for offline lesson delivery.
2. LearningProgressStore persists immutable learner attempts and a synchronization outbox.

Downloaded lesson packages contain the minimum executable contract needed to recreate a LearningPlan for the currently supported ManualInputExercise type.

Downloaded content is versioned by published lesson/course metadata and may be replaced atomically when a newer package is downloaded.

LearningSyncCoordinator remains responsible for deterministic batching, retry policy, acknowledgement, and conflict preservation.

KtorLearningSyncTransport remains responsible for HTTP transport and authenticated bearer-token injection.

LearningSyncObserver exposes deterministic synchronization state for presentation and observability without allowing observers to mutate sync decisions.

The resulting boundary is:

Published course/lesson
        |
        v
DownloadedLessonStore --> offline LessonSession
        |
        v
LearningProgressStore --> LearningSyncCoordinator
        |
        v
KtorLearningSyncTransport --> authenticated server

Platform composition owns:

- the lifecycle and physical location of local drivers;
- authentication/session storage;
- platform-specific encrypted-at-rest database drivers where required;
- background scheduling;
- network reachability presentation;
- user-facing retry/conflict UI.

The shared storage contracts do not persist bearer or refresh tokens.

## Alternatives considered

### Persist only learning progress

Rejected because a learner cannot recreate a lesson offline when the lesson package itself is unavailable.

### Cache raw HTTP responses as offline content

Rejected because it couples offline storage to transport DTOs and makes schema evolution dependent on API wire shape.

### Put offline persistence into core

Rejected because core must remain framework- and platform-independent.

### Let sync state mutate synchronization decisions

Rejected because observability should not be able to acknowledge, drop, or rewrite attempts.

## Consequences

Positive:

- downloaded lessons survive application restart;
- lesson sessions can resume from durable progress without network access;
- local progress remains append-oriented and retry-safe;
- transport and storage can evolve independently;
- presentation receives explicit synchronization state without owning sync semantics.

Trade-offs:

- the current downloaded exercise persistence supports manual-input exercises only;
- platform encrypted-at-rest implementation remains a platform composition requirement;
- background scheduling and visible retry/conflict UX remain outside this infrastructure slice.

## Verification

JVM integration tests verify:

- downloaded lesson persistence across database reopen;
- multiple lessons in one offline course;
- atomic replacement of a downloaded published version;
- deletion without leaving orphaned offline lessons;
- offline lesson completion after restart;
- outbox persistence and acknowledgement;
- deterministic transient recovery after a transport failure.