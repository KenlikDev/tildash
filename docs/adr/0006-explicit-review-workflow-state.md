# ADR-0006: Model Review as Explicit Workflow State

- Status: Accepted
- Date: 2026-09-27

## Context

Educational content and other user-generated changes may require review, correction, approval, rejection, or revision.

The content model defines explicit states: DRAFT, SUBMITTED, UNDER_REVIEW, APPROVED, PUBLISHED, and ARCHIVED.

The workflow must prevent teachers from bypassing review, preserve immutable published snapshots, and make review decisions auditable.

## Decision

Lesson workflow:

DRAFT -> SUBMITTED -> UNDER_REVIEW -> APPROVED -> PUBLISHED -> ARCHIVED

Rejection moves UNDER_REVIEW back to DRAFT and requires a non-blank reason.

Reviewer feedback is an auditable event that leaves content in UNDER_REVIEW.

Teachers and administrators may create content nodes and append source revisions only while the node is DRAFT.

Submission requires deterministic validation with no ERROR findings. PASS_WITH_WARNINGS may be submitted, but approval and publication require a clean PASS.

Approval and publication re-run deterministic validation. Publication creates an immutable published-version snapshot referencing the exact source revision and provenance plus the latest localization revisions available for the published content.

Every state transition and reviewer feedback action is stored in content_workflow_events with action, previous state, next state, actor subject, optional reason, validation outcome, and timestamp.

The workflow history is append-only. PostgreSQL prevents mutation of published or archived content except the explicit PUBLISHED -> ARCHIVED transition.

Authorization is enforced at the HTTP boundary and again in the application service. Business transition rules remain in the framework-independent core workflow state machine.

## Alternatives considered

- Boolean review flags without transition history.
- UI-only review state.
- Implicit review behavior embedded in controller actions.
- Mutable published content without immutable snapshots.
- AI-controlled approval or publication.

These approaches weaken auditability, allow bypasses, or make the published state dependent on mutable working data.

## Consequences

- Review transitions are deterministic and testable.
- Teacher and reviewer responsibilities are explicit.
- Rejection and feedback are auditable.
- Published versions remain reproducible.
- The shared workflow contract is independent of HTTP and persistence frameworks.

Trade-offs:

- review history adds durable records for every action;
- state changes must go through the application workflow;
- future review features must extend the explicit command/state model rather than adding implicit flags.

## Verification

The repository contains core workflow state-machine tests, PostgreSQL persistence for immutable workflow events, HTTP integration tests for the lesson lifecycle, and database-level protection for published-content and workflow-history immutability.

Implementation and API details are documented in docs/CONTENT_STUDIO.md and docs/API.md.