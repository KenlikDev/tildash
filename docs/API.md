# Tildash HTTP API

## Purpose

This document defines the transport-level contract for the Tildash HTTP API.

The API layer is a transport boundary. It must not expose internal domain models or place business rules in controllers.

## Public API namespace

Public application endpoints use a versioned namespace:

`/api/v1/...`

The version is a major API version, independent from the product release version.

Backward-compatible additions stay within the current major API version. Breaking changes require a new major API version and an explicit compatibility decision.

Operational endpoints are outside the public namespace:

- Actuator: `/actuator/...`
- OpenAPI JSON: `/v3/api-docs`
- Swagger UI: `/swagger-ui.html`

These operational/documentation surfaces must not be treated as application-domain endpoints.

## Package structure

The server API area starts at:

`com.kenlikdev.tildash.server.api`

Future endpoints should follow the documented backend boundary:

```
server/api
   |
   v
application service / use case
   |
   v
domain
   |
   v
repository / external gateway
```

Controllers are adapters. They may bind and validate transport input, invoke application use cases, and translate application results into transport responses. They must not implement domain decisions.

## DTO contract

Every public request and response uses an explicit DTO.

Do not serialize domain entities, persistence records, or infrastructure objects directly.

Request DTOs describe the accepted transport shape. Response DTOs describe the documented public shape. Internal refactoring must not silently change those contracts.

DTO names should express their API role, for example:

- `CreateCourseRequest`
- `CourseResponse`
- `PageMetadataResponse`

## Validation

Validation belongs at the HTTP boundary.

Use Jakarta Validation annotations on request DTOs for deterministic input constraints. Validation failures are client errors and must be represented through the API error contract.

Business invariants that require domain knowledge remain in the application/domain layers rather than being duplicated in controller validation.

## Error responses

HTTP API errors use RFC 9457 Problem Details with media type:

`application/problem+json`

The stable base fields are:

- `type`
- `title`
- `status`
- `detail`, when useful and safe
- `instance`

Application-specific error types should use stable identifiers rather than exposing exception class names or framework internals.

Error responses must not contain credentials, tokens, secrets, stack traces, or unnecessary personal data.

## OpenAPI

The generated OpenAPI document is available at:

`/v3/api-docs`

Swagger UI is available at:

`/swagger-ui.html`

OpenAPI metadata is configured in the API configuration package and must remain aligned with the actual public API.

Every public endpoint should document:

- operation summary and description;
- request parameters/body;
- successful response schemas;
- validation/client errors;
- authorization requirements.

## Compatibility

Changes to a public endpoint must be evaluated for compatibility before merge.

Breaking examples include:

- removing or renaming a documented field;
- changing a field's meaning;
- changing requiredness in a breaking way;
- changing status-code semantics;
- changing authentication or authorization requirements;
- changing the versioned resource semantics.

A compatibility decision belongs in the pull request and, for durable architectural changes, an ADR.


## Authentication

Application endpoints under `/api/v1/**` require server-side authentication.

The backend accepts OAuth 2.1 / OpenID Connect-compatible bearer access tokens when resource-server mode is enabled. Token issuance is performed by the configured identity provider; the Tildash backend does not mint user tokens.

The current authenticated identity is available to authenticated clients at:

`GET /api/v1/auth/me`

The response is a transport DTO:

```json
{
  "subject": "provider-user-subject",
  "roles": [
    "learner",
    "teacher"
  ]
}
```

The `subject` value is the authenticated token subject. Roles are the known application roles mapped by the backend. Unknown token role values are not exposed as application roles.

Interactive KMP authentication uses Authorization Code with PKCE. KMP applications must not contain confidential client secrets and must store tokens using platform-secure mechanisms.

Authentication failures return RFC 9457 Problem Details with HTTP 401. Authorization failures return RFC 9457 Problem Details with HTTP 403. Security errors must not expose tokens, stack traces, exception class names, or other security internals.


## Content Studio workflow

Content authoring and review use the /api/v1/content/** namespace.

### Authoring

POST /api/v1/content/nodes

Creates a content node in DRAFT state and creates its first source revision.

Requires TEACHER or ADMINISTRATOR.

GET /api/v1/content/{contentId}/preview

Returns the latest source revision for preview.

Requires TEACHER, REVIEWER, or ADMINISTRATOR.

POST /api/v1/content/{contentId}/source-revisions

Appends a new source revision. Editing is permitted only while the node is DRAFT.

Requires TEACHER or ADMINISTRATOR.

### Review lifecycle

POST /api/v1/content/{contentId}/submit

Runs deterministic lesson validation and moves a valid draft from DRAFT to SUBMITTED.

Validation errors block submission.

Requires TEACHER or ADMINISTRATOR.

POST /api/v1/content/{contentId}/review/start

Moves submitted content to UNDER_REVIEW.

Requires REVIEWER or ADMINISTRATOR.

POST /api/v1/content/{contentId}/review/feedback

Adds auditable reviewer feedback without changing the current state.

Requires REVIEWER or ADMINISTRATOR.

POST /api/v1/content/{contentId}/review/approve

Runs deterministic validation and moves UNDER_REVIEW content to APPROVED only when validation is a clean PASS.

Requires REVIEWER or ADMINISTRATOR.

POST /api/v1/content/{contentId}/review/reject

Requires a non-blank reason and returns UNDER_REVIEW content to DRAFT.

Requires REVIEWER or ADMINISTRATOR.

POST /api/v1/content/{contentId}/publish

Re-runs deterministic validation, moves APPROVED content to PUBLISHED, and creates an immutable publication snapshot.

Requires REVIEWER or ADMINISTRATOR.

POST /api/v1/content/{contentId}/archive

Moves PUBLISHED content to ARCHIVED.

Requires REVIEWER or ADMINISTRATOR.

GET /api/v1/content/{contentId}/review-history

Returns immutable workflow and feedback history.

Requires TEACHER, REVIEWER, or ADMINISTRATOR.

### Workflow invariants

- Teachers cannot publish or approve directly.
- Review state transitions are server-side rules, not UI conventions.
- Validation errors are blocking.
- Warnings remain distinct from a clean pass.
- Published versions reference immutable source/provenance history.
- Published and archived content cannot be edited through the authoring endpoint.
- Workflow history is append-only.

Malformed content requests use RFC 9457 Problem Details. Validation and workflow conflicts use stable problem types rather than framework exception names.



## Content exercises

GET /api/v1/content/{contentId}/exercises

Lists the current exercise definitions for a draft content node.

Requires TEACHER, REVIEWER, or ADMINISTRATOR.

POST /api/v1/content/{contentId}/exercises

Creates a manual-input exercise on a DRAFT lesson.

Requires TEACHER or ADMINISTRATOR.

Request:

    {
      "id": "exercise-1",
      "prompt": "Translate hello.",
      "position": 0,
      "expectedAnswers": ["merhaba"]
    }

PUT /api/v1/content/{contentId}/exercises/{exerciseId}

Updates a manual-input exercise while the lesson is DRAFT.

Requires TEACHER or ADMINISTRATOR.

DELETE /api/v1/content/{contentId}/exercises/{exerciseId}

Deletes a manual-input exercise while the lesson is DRAFT.

Requires TEACHER or ADMINISTRATOR.

A lesson cannot be submitted, approved, or published without at least one executable exercise. Exercise definitions are validated together with lesson content and copied into an immutable publication snapshot.

## Published learner lesson package

GET /api/v1/learning/lessons/{lessonId}

Returns the immutable published package required for offline lesson execution.

Requires the LEARNER application role.

A successful response contains the published course and lesson summaries plus executable manual-input exercises. The package is read from the publication snapshot, not from mutable draft exercise definitions.

## Local development authentication

Production authentication remains bearer-token based. For local manual acceptance only, the server supports an explicitly opt-in development identity:

    TILDASH_SECURITY_DEVELOPMENT_ENABLED=true

With this setting enabled, a loopback client may authenticate by sending:

    X-Tildash-Development-Role: learner

or another known application role. An optional X-Tildash-Development-Subject header overrides the configured development subject.

The development identity is disabled by default, ignores requests that already contain an Authorization header, and is restricted to local development client addresses. It is not a production authentication mechanism.
## Learning catalog

GET /api/v1/learning/catalog

Returns the deterministic learner course catalog projected from canonical content nodes and immutable published snapshots.

Requires the LEARNER application role.

Successful response:

```json
{
  "courses": [
    {
      "id": "course-id",
      "title": "Course title",
      "sourceLocale": "crh",
      "publishedVersion": 1,
      "lessons": [
        {
          "id": "lesson-id",
          "title": "Lesson title",
          "sourceLocale": "crh",
          "publishedVersion": 1,
          "localizations": [
            {
              "locale": "ru",
              "value": "Название урока"
            }
          ]
        }
      ]
    }
  ]
}
```

The response contains only explicit learner-facing DTOs. It does not expose persistence records, provenance objects, Spring types, JDBC types, or database-specific identifiers.

The server loads the published snapshots required by the shared LearningCatalogProjector. The projector selects the highest published version per content ID, rejects ambiguous duplicate versions and malformed hierarchy, and applies deterministic course/lesson ordering.

An empty published catalog returns:

```json
{
  "courses": []
}
```

The endpoint does not seed or infer unpublished content.

## Learning synchronization

POST /api/v1/learning/sync

Synchronizes immutable learner attempts for the authenticated learner.

Request:

```json
{
  "deviceId": "device-a",
  "attempts": [
    {
      "attemptId": "attempt-1",
      "lessonId": "550e8400-e29b-41d4-a716-446655440000",
      "exerciseId": "exercise-1",
      "response": {
        "type": "TEXT",
        "value": "hello"
      },
      "outcome": "CORRECT",
      "occurredAt": "2026-09-28T09:00:00Z"
    }
  ]
}
```

The endpoint requires the LEARNER application role. An authenticated user without LEARNER is forbidden from using the learner synchronization endpoint. The authenticated identity subject, not device ID, scopes the server-side attempt record. `lessonId` identifies the lesson containing the exercise and is part of immutable payload conflict comparison.

Successful responses contain two explicit ID sets:

- `acknowledgedAttemptIds` — newly stored or already-identical attempts;
- `conflictAttemptIds` — attempt IDs whose immutable payload differs from the stored record, or which are contradictory duplicates inside the same request batch.

A conflicting attempt is never overwritten. Unknown or unsupported payloads are rejected as client errors using RFC 9457 Problem Details.

The server persists attempts under `(learner_subject, attempt_id)`. The immutable payload also stores `lesson_id`, so a reused `exercise_id` in another lesson never shares learner progress.
