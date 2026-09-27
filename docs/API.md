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
- authorization requirements once security is introduced.

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
