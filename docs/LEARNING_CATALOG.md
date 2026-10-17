# Tildash Learner Course Catalog

## Purpose

The learner catalog is a deterministic read-side projection over the canonical content tree and immutable published snapshots.

The projection lives in the shared core and does not depend on HTTP, Spring, JDBC, PostgreSQL, or UI code. The server exposes this read model through the authenticated `/api/v1/learning/catalog` HTTP boundary.

## Input

The projector accepts:

- content nodes defining hierarchy and source locale;
- published content versions containing immutable source revisions and localizations.

Only content with a published snapshot and a current `PUBLISHED` node state participates in the learner catalog. Archiving a lesson removes that lesson from the catalog while retaining its immutable history. Archiving a course also hides its lessons from the learner catalog, even when those lesson nodes still have `PUBLISHED` state.

## Version selection

For each content ID, exactly one published version number is allowed. The highest version is selected as the current learner-visible snapshot.

Duplicate published version numbers for the same content ID are rejected because they would make selection ambiguous.

Published snapshots without a corresponding content node are rejected explicitly.

## Hierarchy

The learner catalog currently projects only:

COURSE
  -> LESSON

A published course must be a root node.

A visible published lesson must have a visible published COURSE parent. A lesson whose parent course is archived is hidden rather than treated as malformed. A visible lesson with a missing parent, a non-course parent, or a non-published non-archived course parent is rejected explicitly.

Malformed active hierarchy is rejected instead of being silently omitted.

Other content kinds remain outside this catalog projection and may be exposed by later learner feature slices.

## Ordering

Courses are ordered by their content-node position and then stable content ID.

Lessons inside a course are ordered by their content-node position and then stable content ID.

This guarantees deterministic output even when input collections arrive in a different order.

## Read models

The projection exposes immutable learner-facing summaries:

- LearnerCourseCatalog;
- LearnerCourseSummary;
- LearnerLessonSummary;
- LearnerLocalizedText.

The read models do not expose Spring Data, JDBC, Flyway, persistence entities, or database-specific types.

Course and lesson titles currently require text source payloads. Non-text source payloads produce an explicit projection violation rather than an invalid learner title.

Lesson localizations are projected as text read models and ordered by locale and text.

Localization identity is `(locale, variant, revision)` within a published content version. Duplicate keys are rejected.

## Relationship to learning execution

The catalog is intentionally separate from LessonSession.

The catalog answers:

Which published courses and lessons are available to a learner?

LessonSession answers:

Which exercise should the learner execute next inside a selected lesson?

Keeping these responsibilities separate avoids mixing content browsing, publication snapshots, exercise evaluation, and progress state into one abstraction.

## Non-goals

This slice does not implement:

- learner UI;
- offline local persistence;
- synchronization;
- exercise construction from arbitrary content;
- audio/listening playback;
- AI behavior.

These capabilities remain separate feature contracts.