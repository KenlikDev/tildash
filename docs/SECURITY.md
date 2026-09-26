# Tildash Security Architecture

## Status

This document describes the security foundation implemented for the Spring backend.

The backend is a stateless HTTP resource server boundary. Authentication is transport-level infrastructure and is translated into an application-level identity before identity data is exposed to API consumers.

## Security objectives

The security foundation must:

- fail closed for protected application endpoints;
- authenticate bearer tokens against a configured, trusted issuer when resource-server mode is enabled;
- enforce authorization on the server;
- keep Spring Security types at the infrastructure boundary;
- expose stable RFC 9457 Problem Details for authentication and authorization failures;
- avoid logging credentials, bearer tokens, or security internals;
- keep provider-specific configuration outside source control.

## Trust boundaries

Current trust boundaries are:

```
KMP client
   |
   | HTTPS + Bearer access token
   v
Spring HTTP security boundary
   |
   | application identity
   v
Application / Domain
   |
   v
Persistence / external systems
```

The client is untrusted. Claims supplied by a client are trusted only after token validation and issuer verification by the resource server.

Identity persistence is intentionally deferred to the persistence work in issue #11. The current identity contract therefore uses the authenticated token subject and mapped roles without introducing a database model.

## Threat model

| Threat | Risk | Foundation control | Remaining scope |
| --- | --- | --- | --- |
| Forged or modified bearer token | Unauthorized access | JWT signature validation through Spring Security resource-server support | Deployment must configure the correct trusted issuer |
| Token issued by an untrusted provider | Unauthorized access | Configurable issuer URI; provider is not hard-coded | Deployment must use a trusted issuer |
| Privilege escalation through arbitrary role claims | Unauthorized privileged access | Only known Tildash roles are mapped to `ROLE_*` authorities | Final role assignment remains an identity-provider policy |
| Missing authentication | Unauthorized access | `/api/v1/**` requires authentication | None for the foundation |
| Authenticated user invoking a forbidden use case | Privilege escalation | Method security and explicit authorities | Object-level policies will be added with domain use cases |
| Credential or token leakage in logs | Secret disclosure | Security handlers do not log authentication failures or request credentials | Logging configuration must preserve this rule |
| Security details leaked in HTTP errors | Information disclosure | Stable Problem Details without stack traces or exception class names | Application-specific errors must preserve the same contract |
| CSRF against bearer-token APIs | Cross-site state change | Stateless API uses bearer authentication and disables server-side session/CSRF flow | Browser clients must still follow token-handling guidance |
| Client secret embedded in KMP application | Credential theft | Interactive login uses Authorization Code with PKCE; public clients do not carry a client secret | Platform-specific redirect registration remains deployment work |
| Compromised client or stolen access token | Account compromise | TLS and short-lived bearer-token model are deployment requirements | Token lifetime, revocation, device/session policy remain deployment decisions |

## Authentication model

The backend is designed as an OAuth 2.1 / OpenID Connect-compatible resource server boundary.

When resource-server mode is enabled:

- requests to `/api/v1/**` require a valid bearer access token;
- the token issuer is configured through the standard Spring Security issuer property;
- JWT signature and issuer validation are delegated to Spring Security;
- no vendor-specific issuer is hard-coded in application code;
- the token subject is the application identity subject;
- the configurable `roles` claim is mapped to the four Tildash roles.

The four application roles are:

- `learner`
- `teacher`
- `reviewer`
- `administrator`

Only these values are mapped to `ROLE_LEARNER`, `ROLE_TEACHER`, `ROLE_REVIEWER`, and `ROLE_ADMINISTRATOR`. Unknown values are ignored.

Resource-server mode is enabled with:

```
TILDASH_SECURITY_RESOURCE_SERVER_ENABLED=true
SPRING_SECURITY_OAUTH2_RESOURCESERVER_JWT_ISSUER_URI=https://idp.example.com/issuer
```

The roles claim name is configurable with:

```
TILDASH_SECURITY_JWT_ROLES_CLAIM=roles
```

The default local configuration keeps resource-server mode disabled but still protects `/api/v1/**` by requiring authentication. This is a fail-closed development default rather than an authentication bypass.

## Client authentication contract

KMP interactive sign-in uses Authorization Code with PKCE.

The client contract is:

1. Register the platform-specific redirect URI with the selected identity provider.
2. Generate a PKCE code verifier and challenge on the client.
3. Complete authorization through the provider's authorization endpoint.
4. Exchange the authorization code with the verifier.
5. Store obtained tokens only in platform-secure storage.
6. Send the access token to the backend as `Authorization: Bearer <token>` over TLS.
7. Never embed a client secret in the KMP application.
8. Never log authorization codes, access tokens, refresh tokens, or Authorization headers.

The backend does not mint user tokens. Token issuance remains an identity-provider responsibility.

## Authorization model

Request-level authorization is defined in the HTTP security boundary:

- health and documentation endpoints are explicitly separated from the public application API;
- `/api/v1/**` requires authentication;
- all unspecified routes are denied.

Method-level authorization is enabled for use cases that require role or object-level decisions. Future domain/application services should prefer explicit authorities and keep authorization decisions on the server.

Spring Security annotations and authentication types must not become domain model dependencies. Application code should receive application-level identity data instead.

## Error handling

Authentication failures return:

- HTTP 401;
- `application/problem+json`;
- type `urn:tildash:problem:unauthorized`;
- no credentials, token contents, stack trace, or framework exception details.

Authorization failures return:

- HTTP 403;
- `application/problem+json`;
- type `urn:tildash:problem:forbidden`;
- no credentials, token contents, stack trace, or framework exception details.

## Actuator and documentation

`/actuator/health`, OpenAPI, and Swagger UI are explicitly separated from the public application namespace.

The current configuration exposes only the health actuator endpoint. Security changes must not accidentally expose additional operational endpoints.

## Configuration and secrets

Secrets and deployment-specific identity-provider configuration must stay outside source control.

Use environment variables, secret managers, or the deployment platform for:

- issuer URI;
- client registration secrets where a confidential client exists outside the KMP application;
- signing or encryption keys;
- database credentials;
- operational credentials.

Do not commit real tokens, client secrets, signing keys, private keys, or production issuer credentials.

## TLS

Production deployments must terminate traffic through HTTPS/TLS.

Plain HTTP is permitted only for explicitly isolated local development environments. No production security control may rely on an unencrypted network.

## Deferred decisions

The following are intentionally not implemented in this issue:

- identity persistence and user lifecycle storage;
- refresh-token persistence or rotation policy;
- device/session management;
- provider-specific audience values;
- fine-grained object permissions;
- security administration UI.

These remain explicit follow-up work rather than implicit security behavior.
