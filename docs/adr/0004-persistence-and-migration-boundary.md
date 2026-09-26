# ADR-0004: Isolate Persistence and Use Explicit Migrations

- Status: Accepted — implementation pending
- Date: 2026-09-27

## Context

The product will require durable storage, but the current repository does not yet contain a production persistence implementation.

The architecture must prevent database concerns from leaking into domain rules and must provide a deterministic schema history.

## Decision

Persistence will be an infrastructure concern behind repository or equivalent application boundaries.

Schema changes will use explicit, ordered, source-controlled migrations.

The migration framework is intentionally not selected yet. That choice will be made and recorded before production persistence is introduced.

## Alternatives considered

- Direct database access from domain objects.
- Ad-hoc schema changes performed manually outside version control.
- Selecting a migration framework before persistence requirements are known.

The alternatives either weaken architectural boundaries or create an unverified dependency.

## Consequences

- The current codebase remains free of premature persistence dependencies.
- Future storage work must preserve the domain/infrastructure boundary.
- The migration technology becomes a separate explicit decision before first production use.
