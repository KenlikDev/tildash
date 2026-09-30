package com.kenlikdev.tildash.server.security

import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Component

@Component
class CurrentIdentityProvider {
    fun current(): AuthenticatedIdentity {
        val authentication =
            SecurityContextHolder.getContext().authentication
                ?: throw IllegalStateException("No authenticated identity is available.")

        check(authentication.isAuthenticated) {
            "The current security context is not authenticated."
        }

        val subject =
            authentication.name
                ?: throw IllegalStateException("The authenticated identity has no subject.")

        check(subject.isNotBlank()) {
            "The authenticated identity has no subject."
        }

        return AuthenticatedIdentity(
            subject = subject,
            roles =
                authentication.authorities
                    .mapNotNull { it.authority?.let(Role::fromAuthority) }
                    .toSet(),
        )
    }
}
