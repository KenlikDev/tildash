# Spring Boot Backend Standard

The backend framework is Spring Boot with Kotlin. Ktor is not part of the target backend architecture.

Use the latest stable Spring Boot release that is verified compatible with the repository Kotlin, Gradle, and JDK versions. Record the exact version used for the change and verify compatibility before integration.

Target layering:

```
HTTP/API -> Application Service/Use Case -> Domain -> Repository/External Gateway
```

Controllers handle transport concerns, not business rules.

Validate untrusted input at the boundary. Enforce authorization server-side. Use explicit API models and stable public error responses. Never expose stack traces or secrets.

Configuration must externalize secrets. Persistence must use explicit migrations and intentional transaction boundaries.

Use structured logs and correlation identifiers where appropriate. Never log credentials, tokens, or unnecessary personal data.

Prefer idiomatic Kotlin and small explicit components over mechanical Java patterns or unnecessary framework abstractions.
