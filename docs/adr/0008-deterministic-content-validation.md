# ADR-0008: Keep Content Validation Deterministic and Provider-Neutral

- Status: Accepted
- Date: 2026-09-27

## Context

Tildash content must not enter review or publication with malformed structure, inconsistent exercise definitions, invalid media references, or unresolved provenance/licensing requirements.

These checks must be reproducible in local development, CI, server workflows, and future content tooling. They must not depend on an AI provider being available or returning a particular answer.

## Decision

Deterministic content validation lives in the shared core module.

The validator accepts a complete lesson validation input containing:

- the content tree;
- source revisions;
- localization revisions;
- exercise definitions associated with content nodes.

Validation produces explicit issues with:

- a stable validation code;
- ERROR or WARNING severity;
- an actionable message;
- an optional domain path.

The result exposes an explicit outcome:

- PASS — no issues;
- PASS_WITH_WARNINGS — no errors, but warnings exist;
- FAIL — at least one error exists.

Warnings are therefore never silently represented as a clean pass. Submission is allowed only when no errors remain. Publication is allowed only for a clean PASS.

The deterministic rule set covers:

- content tree identity, parent references, hierarchy, cycles, and sibling ordering;
- source revision presence and uniqueness;
- localization lineage to an existing source revision;
- provenance and copyright/license consistency;
- restricted-content publication checks;
- payload compatibility with the content kind;
- media URI and media-type syntax;
- exercise placement, identity, prompts, answers, and option consistency.

AI-assisted review is a separate future capability. It must consume deterministic validation results rather than replacing or weakening them.

## Alternatives considered

### AI-only content validation

Rejected because an external model cannot provide a reproducible contract for structural and licensing rules.

### Controller-only validation

Rejected because the same deterministic rules would be duplicated across APIs and non-HTTP tooling.

### Exceptions as validation results

Rejected because exceptions are not a stable machine-readable review contract and do not naturally represent warnings alongside errors.

### Boolean pass/fail result

Rejected because warnings would be lost or silently collapsed into success.

## Consequences

Positive consequences:

- local and CI validation are deterministic;
- validation can be reused by future server, content-studio, and import tooling;
- review tooling receives stable machine-readable issue codes;
- warnings and blocking errors are explicitly distinguishable;
- AI remains an optional assistant rather than a source of truth.

Trade-offs:

- validation rules must be deliberately expanded when new content types or exercise types are introduced;
- the shared core owns validation contracts that must remain framework-independent.

## Verification

Common tests cover successful lessons, warning semantics, structural failures, revision lineage, provenance/licensing, media integrity, and exercise consistency.

The validation specification is documented in docs/CONTENT_VALIDATION.md.