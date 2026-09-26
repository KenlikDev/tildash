# Engineering Policies

## Product versioning

Tildash uses Semantic Versioning for product releases:

```
MAJOR.MINOR.PATCH
```

Before 1.0.0, minor releases may contain planned breaking changes while the public product surface is still stabilizing. Patch releases must remain backward-compatible within the supported product contract.

After 1.0.0:

- MAJOR indicates incompatible public contract changes;
- MINOR adds backward-compatible functionality;
- PATCH contains backward-compatible fixes and corrections.

Release tags use the form `vMAJOR.MINOR.PATCH`.

A version is released only after its acceptance criteria and required verification evidence are satisfied.

Component or build-tool versions may have their own internal version identifiers. They do not replace the product release version.

## API compatibility

The public HTTP API is versioned through an explicit documented contract.

Until a stable public API exists, internal Spring endpoints are not treated as a compatibility commitment.

Once a public API is introduced:

- breaking changes require a major API version transition or another explicitly documented compatibility mechanism;
- additive backward-compatible fields and endpoints may be introduced under the existing compatibility contract;
- existing documented behavior must not change silently;
- error shapes and validation semantics are part of the contract when exposed publicly;
- deprecated API behavior must have a documented removal path.

OpenAPI is the source for the machine-readable public API contract once the HTTP API foundation is implemented.

## Database migration policy

Schema changes must be represented by versioned, source-controlled migrations.

Migrations must:

- be applied deterministically;
- have unique, ordered identifiers;
- preserve a reproducible path from the supported baseline to the current schema;
- be reviewed together with the code that depends on the changed schema;
- be tested against the supported database version;
- avoid destructive changes that are not compatible with the deployment sequence.

The migration framework will be selected and recorded before production persistence is introduced. The repository must not carry an unverified migration dependency merely to reserve the capability.

Rollback must be treated separately from schema downgrade. Forward-compatible corrective migrations are preferred to destructive automated downgrades.

## Deprecation policy

Deprecation is an explicit contract change, not an informal warning.

A deprecated public or reusable API must provide:

- the replacement or migration path;
- the reason for deprecation;
- the scope of affected consumers;
- the earliest planned removal version or milestone.

Removal requires verification that the documented migration path is available and that remaining consumers have been identified.

Internal code may be removed directly when it has no supported consumers and the dependency analysis is verified.

## Documentation ownership

Repository documentation is the source of truth for engineering behavior and durable architectural decisions.

Documentation updates must be made in the same change that alters a documented contract whenever practical.

External documentation or publishing systems may mirror repository documentation later, but they must not become a second authoritative source.

## ADR policy

ADR files live under `docs/adr/`.

Each ADR contains:

- context;
- decision;
- alternatives considered;
- consequences.

An ADR records why a durable decision exists. It does not replace implementation documentation.

Future architecture must be labeled as future architecture and must not be presented as an implemented capability.

## Verification policy

Documentation may not claim:

- a supported platform that has not been built or tested;
- a security property that has not been verified;
- an API behavior that is not implemented;
- a release or deployment capability that does not exist.

When implementation and documentation disagree, the verified implementation is the immediate factual source and the documentation must be corrected as part of the change.
