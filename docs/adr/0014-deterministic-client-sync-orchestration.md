# ADR-0014: Deterministic Client Sync Orchestration

- Status: Accepted
- Date: 2026-09-28

## Context

Tildash already has immutable learning attempts, a durable local outbox, and deterministic reconciliation rules. A client coordinator is required to turn those contracts into a safe synchronization workflow without coupling the client to HTTP or a particular network library.

Retry behavior must be bounded and deterministic, while concrete scheduling remains a platform/composition concern.

## Decision

Introduce `LearningSyncCoordinator` in the client storage boundary.

The coordinator:

- reads pending attempts from `LearningProgressStore`;
- builds a `LearningProgressSyncBatch` with the caller-provided device ID;
- submits the batch through provider-neutral `LearningSyncTransport`;
- acknowledges only attempt IDs explicitly returned as accepted;
- leaves conflict IDs in the outbox;
- rejects acknowledgements for unknown attempt IDs;
- rejects responses that both acknowledge and conflict the same attempt;
- retries only `TransientLearningSyncFailure`;
- propagates non-transient failures immediately;
- uses bounded exponential backoff through `LearningSyncRetryPolicy`.

The retry sleeper is an explicit dependency. There is no implicit no-op fallback because such a fallback could hide missing scheduling behavior and create an unintended busy loop.

The coordinator is suspendable but does not depend on a coroutine framework. Platform composition supplies the actual delay/scheduler implementation.

## Alternatives considered

### Concrete HTTP client in the coordinator

Rejected because HTTP transport belongs to infrastructure and would couple synchronization policy to one network stack.

### Retry on every exception

Rejected because permanent protocol, serialization, authentication, or programming errors should not be retried automatically.

### Unbounded retries

Rejected because a permanently unavailable transport could create an endless battery/network loop.

### Implicit zero-delay fallback

Rejected because it hides an incomplete composition and can produce a busy loop under outage conditions.

## Consequences

Positive:

- local outbox semantics remain authoritative;
- transport providers are replaceable;
- retries are bounded and testable;
- conflicts remain explicit rather than being auto-resolved;
- platform scheduling stays outside the storage/domain contract.

Trade-offs:

- concrete network transport still needs to be implemented;
- platform composition must provide a scheduler for delays;
- user-visible retry/error states are a later presentation concern.

## Verification

Common tests cover empty outboxes, deterministic submission order, partial acknowledgement, conflicts, transient retry delays, retry exhaustion, non-transient failures, and invalid transport responses.