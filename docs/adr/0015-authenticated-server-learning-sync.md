# ADR-0015: Authenticated Server Learning Synchronization

- Status: Accepted
- Date: 2026-09-28

## Context

Tildash clients already persist immutable learning attempts locally, maintain a durable synchronization outbox, reconcile duplicates and conflicts deterministically, and use a bounded client-side retry policy.

The remaining transport boundary needs a server endpoint that can accept the same immutable attempt contract without making device identifiers authoritative and without overwriting conflicting history.

## Decision

Implement an authenticated synchronization endpoint at POST /api/v1/learning/sync.

The server:

- authenticates the request through the existing Spring Security boundary;
- scopes persistence to the authenticated identity subject;
- treats deviceId as sender metadata only;
- accepts the current shared LearnerResponse.Text representation and CORRECT / INCORRECT outcomes;
- persists attempts in PostgreSQL table tildash.learning_attempts;
- uses (learner_subject, attempt_id) as the idempotency key;
- acknowledges a new attempt after insertion;
- acknowledges a repeat only when every immutable payload field matches the stored row;
- reports a conflict when the same scoped attempt ID carries different immutable data;
- rejects contradictory duplicate attempt IDs within one request batch before persistence;
- never overwrites stored attempt history;
- enforces database-level immutability through a PostgreSQL trigger;
- returns explicit acknowledgement and conflict ID sets.

The service is transactional. Conflicts are reported as data rather than exceptions so independent valid attempts in the same batch can still be accepted.

The client coordinator remains provider-neutral. It is responsible for retry/backoff and local outbox acknowledgement; the concrete HTTP transport remains a composition concern.

## Alternatives considered

### Scope idempotency by device ID and attempt ID

Rejected because device identity is client-controlled metadata and must not become the authorization boundary. The authenticated learner subject is authoritative.

### Last-write-wins conflict resolution

Rejected because immutable learner attempts are audit data and replacing an existing event would destroy evidence and make convergence dependent on arrival order.

### Delete or update existing attempts on successful synchronization

Rejected because remote acknowledgement represents delivery, not permission to mutate learning history.

### Keep the endpoint unauthenticated for easier offline testing

Rejected because synchronization is a user data boundary and must be scoped to an authenticated identity.

### Use a server-side outbox for this slice

Deferred because the immediate contract only requires durable receipt and explicit acknowledgement/conflict semantics. Server-side outbound event delivery is a separate infrastructure concern.

## Consequences

Positive:

- authenticated per-user synchronization boundary;
- deterministic idempotency across multiple devices;
- immutable server-side learning history;
- explicit and auditable conflicts;
- no dependency from the shared learning core on HTTP or PostgreSQL;
- a concrete transport endpoint can now be composed with the existing client coordinator.

Trade-offs:

- the server currently supports only the existing text response variant;
- client platform composition still needs a concrete HTTP transport and user-visible sync/retry state;
- server-side learning history retention and observability are not yet part of this slice.

## Verification

Integration tests exercise:

- unauthenticated rejection;
- first delivery and persistence;
- identical retry idempotency;
- conflicting retry preservation;
- per-learner attempt isolation;
- contradictory duplicate IDs within one batch;
- invalid request Problem Details;
- database-enforced immutability.