package com.kenlikdev.tildash.server.security

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "tildash.security")
data class SecurityProperties(
    val resourceServer: ResourceServerProperties = ResourceServerProperties(),
    val jwt: JwtProperties = JwtProperties(),
)

data class ResourceServerProperties(
    val enabled: Boolean = false,
)

data class JwtProperties(
    val rolesClaim: String = "roles",
)
