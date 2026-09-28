# ADR-0011: Deterministic Published Course Catalog Projection

- Status: Accepted
- Date: 2026-09-28

## Context

The verified content model and published snapshot model now exist, while the learning core has deterministic progress and lesson-session behavior. A learner-facing catalog is needed to connect published course content to learning without coupling browsing to persistence or UI.

## Decision

Introduce LearningCatalogProjector in the shared core.

The projector:

- indexes canonical ContentNode values by stable ContentId;
- selects the latest unique PublishedContentVersion for each content ID;
- rejects ambiguous duplicate versions;
- rejects published snapshots with missing content nodes;
- projects only published COURSE and LESSON nodes in a COURSE -> LESSON hierarchy;
- orders courses and lessons by position and stable content ID;
- maps source and localization text into immutable learner read models;
- rejects unsupported non-text title payloads explicitly.

The projector is a read-side deterministic boundary. It does not mutate content or learning progress.

## Alternatives considered

### Query database tables directly from clients

Rejected because clients must not know persistence structure or database-specific rules.

### Let the UI build the course tree

Rejected because multiple client platforms could diverge in hierarchy and ordering behavior.

### Reuse ContentNode and PublishedContentVersion as learner DTOs

Rejected because learner read models should not expose canonical authoring or persistence-facing structures.

### Combine catalog and lesson-session state

Rejected because content availability and learner progress have different lifecycles and should remain independently deterministic.

## Consequences

Positive consequences:

- one deterministic catalog projection for all clients;
- no persistence coupling in learner browsing logic;
- published snapshots remain the source of learner-visible content;
- malformed publication references fail explicitly;
- later HTTP and UI adapters can consume stable read models.

Trade-offs:

- additional mapping types are required;
- future content kinds need explicit projection rules rather than implicit fallbacks.

## Verification

Common tests cover publication filtering, latest-version selection, deterministic ordering, localization projection, duplicate IDs, duplicate versions, missing nodes, and invalid lesson hierarchy.