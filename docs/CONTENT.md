# Tildash Content Model

## Status

The canonical content model for issue #12 is implemented in `core` and its first persistence schema is implemented in `server`.

The model is generic. A Crimean Tatar course is represented through the same content tree, provenance, localization, and versioning primitives used for any supported language or variant.

## Content tree

Content is represented as a generic tree of stable content IDs:

```text
COURSE
  |
  +-- LESSON
        |
        +-- EXAMPLE
        +-- VOCABULARY
        +-- MEDIA_REFERENCE
```

`ContentNode.parentId` expresses the hierarchy. Courses are roots; every non-course node has a parent.

`ContentKind` currently supports `COURSE`, `LESSON`, `EXAMPLE`, `VOCABULARY`, and `MEDIA_REFERENCE`.

Ordering is represented by a non-negative `position` within a parent's children.

## Stable IDs and locale

`ContentId` is an opaque canonical UUID value.

`LanguageTag` is a non-blank, whitespace-free language tag. The model does not special-case Crimean Tatar; `crh` is a normal source locale value.

## Source content and revisions

Source content and its history are separate from localized content.

A `SourceContentRevision` contains:

- the stable content ID and positive revision number;
- the source payload;
- the complete provenance snapshot for that revision;
- the actor who created the revision;
- the creation timestamp.

Revision records are append-oriented history. A later edit creates a later revision rather than overwriting the historical meaning of an earlier revision.

## Content payloads

The canonical model supports these payload types:

- `Text` for prose, examples, course/lesson text, and other free-form source content;
- `Vocabulary` for a term and its gloss;
- `MediaReference` for an external media URI and optional media type.

The persistence layer stores payloads as JSONB. Payload encoding remains an infrastructure concern and is not a dependency of the shared domain model.

## Provenance

Provenance is first-class and attached to source revisions.

It can represent:

- source title and locator;
- author;
- speaker;
- dialect/language variety;
- literary, colloquial, regional, dialectal, historical, or generic variant classification;
- license identifier and URL;
- copyright status;
- reviewer;
- verification timestamp.

Reviewer and verification timestamp must be supplied together. Source records can remain unverified without pretending that verification occurred.

Identity persistence is intentionally not required for provenance. `PersonReference` can carry a human-readable name and an optional external identifier until a durable identity domain exists.

## Localization

A `LocalizedContentRevision` is a separate revision stream keyed by content ID and locale.

It contains only localized payload and localization metadata plus the source revision from which it was derived. Unrelated course, lesson, provenance, or source objects are not duplicated.

Localization revisions can be classified with the same supported variant types and have their own explicit content state.

## Content states

The canonical state vocabulary is:

```text
draft
submitted
under review
approved
published
archived
```

These values are represented in code as `ContentState` and in PostgreSQL as constrained text values.

Issue #12 defines the state model. The server-side review transition rules are implemented as a separate application workflow governed by ADR-0006.

## Published versions

`PublishedContentVersion` is an immutable snapshot shape containing:

- the stable content ID;
- the published version number;
- the exact source revision;
- the localizations included in that publication;
- the publishing actor;
- the publication timestamp.

The PostgreSQL schema additionally makes provenance, source revisions, localization revisions, and published snapshots append-only by rejecting update/delete mutations through database triggers.

Consequently a published version references immutable history and can be reconstructed later without depending on the current editable state.

## Persistence schema

The V2 migration creates:

- `content_nodes` — canonical content tree and current state;
- `content_provenance` — immutable provenance snapshots;
- `content_source_revisions` — source revision history;
- `content_localization_revisions` — independent localization history;
- `content_published_versions` — immutable publication snapshots;
- `content_published_localizations` — immutable localization snapshots for a publication.

Foreign keys keep revision and localization relationships explicit.

## Example: Crimean Tatar course

A first Crimean Tatar course can use the generic model:

```text
Course node:       kind=COURSE, sourceLocale=crh
Lesson node:       kind=LESSON, parentId=<course-id>
Example node:      kind=EXAMPLE, parentId=<lesson-id>
Vocabulary node:   kind=VOCABULARY, parentId=<lesson-id>
```

The example or vocabulary payload can use `ContentPayload.Text` or `ContentPayload.Vocabulary` without any language-specific class or special-case persistence table.

Literary or regional forms are represented by the normal `ContentVariantType` and `LanguageTag` values.

## Deterministic validation

Lesson validation is implemented in core and is independent from HTTP, persistence, and AI providers.

Validation produces stable issue codes with ERROR or WARNING severity and an explicit PASS, PASS_WITH_WARNINGS, or FAIL outcome.

Deterministic rules cover content-tree structure, source revision presence and uniqueness, localization lineage, provenance and licensing consistency, payload compatibility, media-reference syntax, and exercise consistency.

Submission is blocked by errors. Publication is allowed only for a clean PASS; PASS_WITH_WARNINGS remains explicitly distinct from PASS.

AI-assisted review remains a separate future capability and cannot replace deterministic validation or turn a deterministic error into a pass.

## Boundaries

The shared `core` model must not depend on Spring, JDBC, Flyway, PostgreSQL, or HTTP frameworks.

The server persistence adapter owns relational mapping, JSONB encoding, transaction configuration, and migration execution.

Public HTTP DTOs must remain separate from these domain and persistence structures.

## Content authoring and review

The backend implements a deterministic lesson authoring and review workflow. Teachers and administrators can create content nodes and append source revisions while content is in DRAFT. Reviewers and administrators can start review, add feedback, approve, reject, publish, and archive.

Submission, approval, and publication re-run deterministic validation. Published and archived content cannot be edited through the authoring workflow.

The workflow history is stored as immutable database events. The durable workflow decision is recorded in ADR-0006 and the HTTP contract is documented in docs/API.md.

## Deferred work

This issue does not implement:

- identity persistence;
- domain repository implementations;
- moderation APIs;
- content authoring UI;
- search/indexing infrastructure;
- media ingestion or storage.