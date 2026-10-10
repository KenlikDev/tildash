# ADR-0020: Bind Learner Attempts to Published Lesson Versions

- Status: Accepted
- Date: 2026-10-11

## Context

Tildash supports offline lesson downloads. A learner may download one published lesson version, answer exercises without network access, and synchronize those immutable attempts after a newer lesson version has been published.

An attempt containing only the lesson ID and exercise ID cannot distinguish the exact published exercise definition used for the answer. Re-evaluating it against the latest publication can change the outcome of a historical attempt. Trusting the client-supplied outcome is also insufficient because a modified client can claim an incorrect response was correct.

## Decision

- Bind each new learner attempt to the positive published lesson version used by the learner.
- Preserve that version in the learning plan, durable local attempt row, synchronization DTO, and server-side attempt record.
- Resolve the exercise from the immutable published snapshot matching both lesson ID and published version.
- Re-evaluate the submitted response on the server through the shared deterministic evaluator before idempotency comparison and persistence.
- Treat the client-supplied `outcome` as untrusted compatibility data. Persist and compare the server-derived outcome.
- Keep `(learner_subject, attempt_id)` as the server idempotency key. Published version, lesson ID, exercise ID, response, derived outcome, and occurrence timestamp are immutable payload data.
- Report unresolved lesson-version/exercise references as conflicts and do not persist them.
- Do not infer missing versions for pre-migration local or server rows. An inferred current version could assign a historically incorrect outcome.

## Alternatives considered

### Evaluate every attempt against the latest published lesson

Rejected because an offline attempt against version 1 could be incorrectly rescored using version 2 after content or expected answers change.

### Trust the client-reported outcome

Rejected because clients are untrusted and can forge outcomes that would corrupt server-authoritative history or analytics.

### Backfill missing versions from the current downloaded lesson

Rejected because the currently downloaded lesson is not proof of the version used when each older attempt was created.

## Consequences

Positive:

- offline attempts retain their content-version context;
- server-persisted outcomes are calculated from published content rather than client claims;
- retries remain deterministic across later content publication;
- old and new lesson versions can coexist without changing historical scoring.

Trade-offs:

- local and server migrations preserve old rows with a null published version;
- local migration 5 removes unversioned legacy rows from the outbox without deleting immutable attempt history, so they cannot block new versioned sync batches;
- those legacy attempts cannot enter the version-bound sync path until a recovery workflow establishes their original version;
- the current client still keeps locally computed progress; user-visible reconciliation with server-authoritative outcomes remains follow-up work.

## Verification

Integration tests verify that the server rejects client outcome manipulation in both directions and evaluates attempts against the exact historical published version even after a later version exists. Storage and transport tests verify version persistence and reject unversioned attempts before network submission.
