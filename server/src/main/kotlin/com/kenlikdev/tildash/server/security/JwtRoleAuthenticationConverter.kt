package com.kenlikdev.tildash.server.security

import org.springframework.core.convert.converter.Converter
import org.springframework.security.core.GrantedAuthority
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter

class JwtRoleAuthenticationConverter(
    private val rolesClaim: String,
) : Converter<Jwt, Collection<GrantedAuthority>> {
    private val scopeAuthorities = JwtGrantedAuthoritiesConverter()

    override fun convert(jwt: Jwt): Collection<GrantedAuthority> {
        val authorities = scopeAuthorities.convert(jwt).orEmpty()
        val roles = extractRoles(jwt)

        return (authorities + roles)
            .distinctBy(GrantedAuthority::getAuthority)
    }

    private fun extractRoles(jwt: Jwt): List<GrantedAuthority> {
        val rawClaim = jwt.claims[rolesClaim]
        val values =
            when (rawClaim) {
                is Collection<*> -> rawClaim.filterIsInstance<String>()
                is String -> rawClaim.split(" ", ",")
                else -> emptyList()
            }

        return values
            .mapNotNull(Role::fromTokenValue)
            .map(Role::authority)
            .map(::SimpleGrantedAuthority)
    }
}

fun createJwtAuthenticationConverter(
    rolesClaim: String,
): JwtAuthenticationConverter =
    JwtAuthenticationConverter().apply {
        setJwtGrantedAuthoritiesConverter(JwtRoleAuthenticationConverter(rolesClaim))
    }
