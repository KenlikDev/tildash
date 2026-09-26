# ADR-0006: Model Review as Explicit Workflow State

- Status: Accepted — implementation pending
- Date: 2026-09-27

## Context

Educational content and other user-generated changes may require review, correction, approval, rejection, or revision.

The current repository does not yet implement this workflow.

## Decision

Review is modeled as explicit workflow state with observable transitions.

A future implementation must make:

- the current review state;
- the actor responsible for a transition;
- the relevant timestamps;
- the reason or decision where required;

available to the application layer rather than relying on implicit side effects.

Review logic must remain independent from UI presentation and transport details.

## Alternatives considered

- Boolean flags without transition history.
- UI-only review state.
- Implicit review behavior embedded in controller actions.

These approaches make auditing, retries, and future moderation behavior harder to verify.

## Consequences

- Review transitions become testable application behavior.
- Future moderation and content tools can share the same workflow contract.
- Persistence representation remains an implementation detail behind the application boundary.
- No current review feature is claimed by this ADR.
