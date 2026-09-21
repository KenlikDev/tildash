# Spring Boot Backend Standard

The backend framework is Spring Boot with Kotlin. Ktor is not part of the target backend architecture.

Use the latest stable Spring Boot release that is verified compatible with the repository Kotlin, Gradle, and JDK versions. As of 2026-09-21, Spring Boot 4.1.1 is the latest stable release; re-check this before integration.

Target layering:

```
HTTP/API -> Application Service/Use Case -> Domain -> Repository/External Gateway
```

Controllers handle transport concerns, not business rules.

Validate untrusted input at the boundary. Enforce authorization server-side. Use explicit API models and stable public error responses. Never expose stack traces or secrets.

Configuration must externalize secrets. Persistence must use explicit migrations and intentional transaction boundaries.

Use structured logs and correlation identifiers where appropriate. Never log credentials, tokens, or unnecessary personal data.

Prefer idiomatic Kotlin and small explicit components over mechanical Java patterns or unnecessary framework abstractions.
