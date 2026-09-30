package com.kenlikdev.tildash.server.security

data class AuthenticatedIdentity(
    val subject: String,
    val roles: Set<Role>,
)
