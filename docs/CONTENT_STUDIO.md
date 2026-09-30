# Tildash Content Studio

## Purpose

Content Studio is the server-side authoring and review boundary for structured educational content. It intentionally does not implement a UI.

## Authoring lifecycle

Teachers and administrators create content nodes in DRAFT state and append source revisions while the content remains editable.

Each source revision stores a provenance snapshot. Draft editing is append-only at the revision layer: editing creates revision N+1 instead of changing revision N.

Preview reads the latest source revision without changing workflow state.

## Review lifecycle

The canonical lesson state machine is:

DRAFT -> SUBMITTED -> UNDER_REVIEW -> APPROVED -> PUBLISHED -> ARCHIVED

Rejection returns UNDER_REVIEW to DRAFT and requires a non-blank reason.

Reviewer feedback is stored as an auditable event without changing the current state.

## Validation gates

Submission requires deterministic validation with no ERROR findings.

Approval and publication require a clean PASS. PASS_WITH_WARNINGS is not a publication pass.

Validation is provider-neutral and runs in shared core. It does not call an AI provider.

## Authorization

Teacher/admin capabilities:

- create content nodes;
- append source revisions while content is DRAFT;
- submit content for review;
- preview content;
- read review history.

Reviewer/admin capabilities:

- start review;
- add feedback;
- approve;
- reject;
- publish;
- archive;
- preview and read review history.

HTTP authorization is enforced with Spring Security. The application service also checks the current identity before executing authoring or workflow commands.

## Persistence and audit

Workflow events are stored in the tildash.content_workflow_events table and are append-only.

Publishing creates an immutable row in content_published_versions and snapshot rows in content_published_localizations.

PostgreSQL prevents mutation of published or archived content except the explicit PUBLISHED -> ARCHIVED transition.

## Boundaries

Controllers are transport adapters. They do not contain workflow decisions.

The workflow state machine lives in core and is framework-independent.

Persistence and transaction boundaries live in server.

Deterministic content validation is reused rather than duplicated in controllers.

## Verification

Integration tests exercise the full lifecycle, authorization boundary, validation failure, rejection reason, preview and revision editing, and database immutability.