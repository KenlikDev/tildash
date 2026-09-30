package com.kenlikdev.tildash.server.security

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.web.filter.OncePerRequestFilter

class DevelopmentIdentityFilter(
    private val properties: SecurityProperties,
) : OncePerRequestFilter() {
    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain,
    ) {
        if (!properties.development.enabled ||
            !request.requestURI.startsWith("/api/v1/") ||
            request.getHeader("Authorization") != null ||
            !isLoopback(request.remoteAddr)
        ) {
            filterChain.doFilter(request, response)
            return
        }

        val role =
            request.getHeader("X-Tildash-Development-Role")
                ?.let(Role::fromTokenValue)
        if (role == null) {
            filterChain.doFilter(request, response)
            return
        }

        val subject =
            request.getHeader("X-Tildash-Development-Subject")
                ?.trim()
                ?.takeIf { it.isNotEmpty() }
                ?: properties.development.subject

        val authentication =
            UsernamePasswordAuthenticationToken(
                subject,
                null,
                listOf(SimpleGrantedAuthority(role.authority)),
            )
        SecurityContextHolder.getContext().authentication = authentication

        try {
            filterChain.doFilter(request, response)
        } finally {
            SecurityContextHolder.clearContext()
        }
    }

    private fun isLoopback(remoteAddress: String): Boolean =
        remoteAddress == "127.0.0.1" ||
            remoteAddress == "::1" ||
            remoteAddress == "0:0:0:0:0:0:0:1"
}
