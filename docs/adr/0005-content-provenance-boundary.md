# ADR-0005: Separate Content, Provenance, Localization, and Review State

- Status: Accepted
- Date: 2026-09-27

## Context

Tildash needs a canonical content model that can support courses, lessons, examples, vocabulary, media references, source attribution, dialects, variants, localization, revision history, and publication without coupling those concepts to a single language or UI.

The first concrete target is a Crimean Tatar course, but the model must remain generic and must not introduce language-specific persistence branches.

## Decision

Content is represented as a generic stable-ID tree.

```
COURSE
  |
  +-- LESSON
        |
        +-- EXAMPLE
        +-- VOCABULARY
        +-- MEDIA_REFERENCE
```

Each node has a stable canonical UUID, content kind, optional parent, source locale, explicit content state, and ordering position.

Source content and localization are separate revision streams.

Source revisions carry immutable provenance snapshots. Provenance can identify the source and locator, author, speaker, dialect, variant classification, license, copyright status, reviewer, and verification timestamp.

Localized revisions carry only localization-specific data and the source revision from which they were derived. They do not duplicate unrelated domain objects.

Published content is represented as an immutable version snapshot. A published version identifies the exact source revision and the localization revisions included in that publication.

The relational implementation mirrors these boundaries:

- `content_nodes`;
- `content_provenance`;
- `content_source_revisions`;
- `content_localization_revisions`;
- `content_published_versions`;
- `content_published_localizations`.

History tables are append-oriented and database triggers reject update/delete mutations.

Content payloads are modeled in `core` as typed values (`Text`, `Vocabulary`, and `MediaReference`). The server persistence layer stores them as JSONB; the shared model does not depend on the persistence encoding.

The state vocabulary is draft, submitted, under review, approved, published, and archived.

Issue #12 defines the state values. State transition rules remain part of the separate review workflow decision in ADR-0006.

## Alternatives considered

### Separate language-specific content models

Rejected because language-specific classes and tables would create special cases and prevent the first course from exercising the same model as later languages.

### Duplicate full content objects for each localization

Rejected because localization should add localized payload and metadata without duplicating the source object, provenance, or hierarchy.

### Mutable content rows with a current-version flag

Rejected because mutable rows make historical reconstruction depend on current state and weaken reproducibility of published versions.

### Store provenance only in application logs

Rejected because attribution and verification are part of the content contract and must survive independently of operational logs.

### Generic untyped JSON as the shared domain model

Rejected because the shared contract should expose meaningful domain concepts and invariants. JSONB is an infrastructure encoding, not the domain model.

## Consequences

Positive consequences:

- the first Crimean Tatar course uses the same generic model as future languages;
- localization and source content evolve independently;
- provenance is first-class and preserved with source history;
- published content can be reconstructed from immutable snapshots;
- persistence can evolve without changing the shared domain contract.

Trade-offs:

- versioned history creates more rows than a single mutable representation;
- application code must create new revisions rather than overwrite historical records;
- JSONB payload encoding requires a server persistence adapter;
- review state exists before its transition workflow is implemented.

## Verification

The repository contains canonical Kotlin content model tests, a versioned PostgreSQL V2 content schema, database tests for the canonical tables and published-version immutability, and documentation describing the domain and persistence boundaries.