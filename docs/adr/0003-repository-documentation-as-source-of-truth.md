# ADR-0003: Keep Engineering Documentation in the Repository

- Status: Accepted
- Date: 2026-09-27

## Context

Engineering decisions must remain versioned with the source they govern. A separate documentation system would create the risk of stale or conflicting sources of truth.

## Decision

Keep architecture documentation, engineering policies, and ADRs in the repository.

External documentation systems may later publish or mirror repository content, but they are not authoritative.

## Alternatives considered

- External wiki as the primary documentation source.
- Hosted documentation generated independently of the repository.
- Issue descriptions as the only durable record of architecture.

These approaches can be useful for publishing, but they are weaker as the authoritative versioned source.

## Consequences

- Documentation changes travel through the same review workflow as code.
- ADR history is versioned with implementation history.
- Published documentation must be treated as a derivative view.
