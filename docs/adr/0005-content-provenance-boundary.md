# ADR-0005: Separate Content, Provenance, Localization, and Review State

- Status: Accepted — implementation pending
- Date: 2026-09-27

## Context

Tildash is intended to support educational content that can be authored, translated, revised, reviewed, and reused.

The current repository does not yet implement the production content model.

## Decision

Future content architecture must keep these concerns conceptually separate:

- content data;
- provenance and attribution;
- localization and version history;
- moderation/review state.

The boundaries must remain explicit even when persistence representations combine related records for practical reasons.

## Alternatives considered

- One undifferentiated content object containing all metadata and workflow state.
- Storing provenance only in infrastructure logs.
- Treating localization as a presentation-only concern.

These alternatives make traceability, versioning, and review behavior harder to reason about.

## Consequences

- Content contracts can evolve independently of provenance and moderation rules.
- Future APIs can expose only the information appropriate to each use case.
- No current product capability should be inferred from this ADR; implementation remains future work.
