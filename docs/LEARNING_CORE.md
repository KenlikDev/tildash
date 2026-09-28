# Tildash Learning Core

## Scope

This document specifies the first deterministic learning-domain slice used by the learner experience.

Implemented in:

core/src/commonMain/kotlin/com/kenlikdev/tildash/learning/LearningProgress.kt

The learning core is framework-independent. It does not depend on Spring, PostgreSQL, KMP platform APIs, HTTP, audio decoders, or AI providers.

## Learning plans and exercises

A LearningPlan identifies one lesson and its required exercises.

The current exercise implementation is ManualInputExercise:

- stable exercise ID;
- stable content ID;
- prompt;
- one or more expected text answers.

Expected answers must be unique after normalization. Future exercise types may provide separate evaluators while using the same progress and scheduling contracts.

## Answer evaluation

Manual input normalizes answers by trimming surrounding whitespace and applying case normalization.

Evaluation returns:

- CORRECT when any expected answer matches;
- INCORRECT otherwise.

The same exercise and response always produce the same result.

## Attempts

A LearningAttempt is immutable and contains:

- attemptId;
- exerciseId;
- response;
- outcome;
- occurredAt.

Attempt IDs are idempotency keys.

Submitting an identical attempt again returns the existing state without adding another attempt.

Reusing an attempt ID with different exercise, response, or timestamp is rejected as AttemptIdConflict.

Attempts are stored in canonical order by occurrence time and then attempt ID. This ordering is stable when timestamps collide and when attempts arrive late.

## Progress

LearningProgress stores the canonical attempt history.

Derived state includes:

- review state per exercise;
- mistake count per exercise;
- lesson completion;
- lesson completion timestamp.

An incorrect attempt does not erase earlier correct attempts or mistakes. Mistake history is cumulative.

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

A correct answer advances one stage up to stage 5.

The algorithm intentionally uses fixed intervals in this first contract. Adaptive scheduling can be introduced later only as an explicit versioned behavior change.

## Lesson completion

A lesson becomes complete when every required exercise has at least one correct attempt.

The completion timestamp is the earliest canonical attempt at which all required exercise IDs have been answered correctly at least once.

Completion is therefore reproducible from the attempt history and does not depend on client UI state.

## Synchronization implications

The core is designed for eventual persistence and synchronization:

- attempt IDs must remain stable across retries and devices;
- exact occurrence timestamps must be preserved;
- duplicate delivery must be idempotent;
- attempts must be mergeable using canonical ordering;
- previous attempt history must not be replaced by a latest-counter snapshot.

Persistence and offline synchronization are separate follow-up contracts.

## Future exercise types

The progress engine depends on the evaluator boundary rather than a hard-coded response algorithm.

A future listening exercise can provide its own evaluator while reusing:

- LearningAttempt;
- LearningProgress;
- ReviewScheduler;
- lesson completion;
- mistake tracking.

Audio transport, speech processing, and transcript matching are intentionally outside this slice.

## Verification

Common tests are the executable specification for the current behavior and cover:

- exact normalized text evaluation;
- incorrect answer handling;
- deterministic SRS stages and intervals;
- cumulative mistakes;
- lesson completion;
- attempt idempotency;
- attempt conflicts;
- deterministic ordering.

The durable architectural decision is recorded in ADR-0009.