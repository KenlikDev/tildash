package com.kenlikdev.tildash.server.api.auth

data class CurrentIdentityResponse(
    val subject: String,
    val roles: List<String>,
)
