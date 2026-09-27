# ADR-0007: Security Identity and Authorization Boundary

## Status

Accepted

## Context

The Spring backend needs a security foundation before user data and protected content are introduced.

The repository requires authentication and authorization to remain independent from persistence details and from the eventual identity provider. The KMP applications are public clients and must not embed confidential client credentials.

The API also needs stable error behavior that does not expose security implementation details.

## Decision

Tildash will use the backend as a stateless OAuth 2.1 / OpenID Connect-compatible resource server boundary.

The security architecture is:

- Spring Security `SecurityFilterChain` handles HTTP authentication and request authorization.
- JWT bearer tokens are accepted when resource-server mode is explicitly enabled and a trusted issuer is configured.
- The issuer is deployment configuration, not application code.
- Interactive KMP sign-in uses Authorization Code with PKCE.
- Application identity is represented by a framework-independent subject and a set of known Tildash roles.
- Spring Security principals and authentication objects remain inside the infrastructure adapter.
- The supported application roles are learner, teacher, reviewer, and administrator.
- JWT role claims are mapped only for those known roles.
- Method security is enabled for use-case authorization that cannot be expressed safely at the request boundary.
- Authentication and authorization failures use stable RFC 9457 Problem Details.
- The default local configuration is fail-closed for protected API routes.
- Persistence of identities and authorization data is deferred to issue #11 and must not leak through this security boundary.

## Alternatives considered

### Session-based server authentication

Rejected for the current API foundation because the clients are cross-platform public applications and the backend is intended to operate as a stateless resource server.

### Vendor-specific identity-provider SDK

Rejected because it would couple the backend security boundary to one provider before deployment and identity-provider requirements are finalized.

### Passing Spring Security principals into domain code

Rejected because it couples application and domain behavior to the transport security framework and makes future identity-provider changes more expensive.

### Disabling API authorization in local development

Rejected because it creates a development path that differs from the production security invariant. The repository instead fails closed when resource-server authentication is not configured.

## Consequences

Positive consequences:

- provider-neutral authentication boundary;
- clear separation between transport authentication and application identity;
- server-side authorization remains authoritative;
- KMP clients can authenticate without embedded secrets;
- security failures have a stable public error contract;
- persistence can be introduced later without changing the HTTP security contract.

Trade-offs:

- deployments must provide a trusted issuer when JWT authentication is enabled;
- role mapping depends on a documented token claim contract;
- detailed object-level authorization will require domain/application work in later issues;
- local API calls need explicit authenticated test credentials or security-test support.

## Verification

The implementation includes tests for:

- unauthenticated API rejection;
- stable 401 Problem Details;
- role-based method authorization;
- current identity translation;
- known JWT role mapping and rejection of unknown role values.

The broader repository CI must remain green before integration.
