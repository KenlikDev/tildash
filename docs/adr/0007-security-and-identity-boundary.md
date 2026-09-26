# ADR-0007: Security and Identity Boundary

## Status

Accepted

## Context

Tildash exposes a multi-platform client (Android, iOS, Desktop, and Web) and a Spring Boot HTTP API. Authentication and authorization must be established before user data, protected content, or persistence-backed identity is introduced.

The security boundary must remain independent from the product domain so that identity-provider and framework changes do not leak into domain contracts.

## Decision

### Authentication protocol

Interactive client authentication will use OAuth 2.1 / OpenID Connect-compatible flows with Authorization Code and PKCE.

The backend will act as an OAuth 2.1 resource server for protected API requests.

The token issuer is configurable and provider-neutral. A concrete identity provider is selected as an implementation/deployment decision rather than embedded in domain code.

### Authorization

Request authorization uses Spring Security's `SecurityFilterChain` and `authorizeHttpRequests`.

Method security may be used for use-case or object-level decisions that cannot be expressed safely at the HTTP boundary.

Authorization is server-side and is based on explicit application authorities/roles:

- `LEARNER`
- `TEACHER`
- `REVIEWER`
- `ADMIN`

An authenticated principal is mapped into an application-level identity abstraction. Spring Security principal types must not become domain model dependencies.

### Public and operational endpoints

The public application API uses the versioned `/api/v1/...` namespace.

Actuator and documentation endpoints are operational surfaces and have explicit security rules independent of application-domain authorization.

### Error handling

Authentication and authorization failures use the API Problem Details contract.

Responses must not expose stack traces, internal security configuration, token contents, credentials, or other sensitive diagnostics.

### Secrets and configuration

Client secrets, issuer configuration, signing credentials, database credentials, and other security-sensitive configuration are supplied through deployment configuration or secret management. They are never committed to the repository.

Production HTTP communication is protected by TLS. Local development may use explicitly documented non-production exceptions.

## Consequences

This decision keeps the application/domain layers independent from Spring Security and from a particular identity vendor.

It also means that identity persistence, account lifecycle, token issuance, and provider-specific configuration are implementation concerns that must be introduced with explicit contracts and tests rather than inferred from the HTTP layer.

The KMP clients require a platform-appropriate secure credential/token storage mechanism; token material must not be stored in ordinary preferences or logs.

## Verification expectations

The implementation must demonstrate:

- unauthenticated requests are rejected for protected API resources;
- public endpoints remain explicitly accessible where intended;
- role-restricted operations reject insufficient authorities;
- authentication and authorization failures use stable Problem Details;
- no secrets or bearer tokens appear in source, logs, or error responses;
- the client/server authentication contract is documented and tested.

## Rejected alternatives

### Server-side sessions as the primary API authentication mechanism

Rejected for the primary multi-platform API contract because it couples API consumers to server-side session state and complicates independent client/platform operation.

### Provider-specific SDKs in the domain/application layer

Rejected because changing identity providers would force domain/application changes.

### Credentials embedded in KMP clients

Rejected because installed client applications cannot safely keep a confidential client secret.

## References

- Spring Security request authorization uses `SecurityFilterChain` and `authorizeHttpRequests`.
- OAuth 2.1 / OpenID Connect flows remain the protocol boundary; concrete provider configuration is deferred to implementation.
