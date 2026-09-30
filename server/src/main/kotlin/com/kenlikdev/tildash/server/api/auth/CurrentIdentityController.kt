package com.kenlikdev.tildash.server.api.auth

import com.kenlikdev.tildash.server.security.CurrentIdentityProvider
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import org.springframework.http.MediaType
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/auth")
class CurrentIdentityController(
    private val currentIdentityProvider: CurrentIdentityProvider,
) {
    @GetMapping("/me")
    @Operation(
        summary = "Get the current authenticated identity",
        description = "Returns the application identity resolved from the authenticated request.",
    )
    @ApiResponses(
        ApiResponse(responseCode = "200", description = "Authenticated identity"),
        ApiResponse(
            responseCode = "401",
            description = "Authentication required",
            content = [Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE, schema = Schema(implementation = Any::class))],
        ),
    )
    fun currentIdentity(): CurrentIdentityResponse {
        val identity = currentIdentityProvider.current()

        return CurrentIdentityResponse(
            subject = identity.subject,
            roles = identity.roles.map { it.tokenValue }.sorted(),
        )
    }
}
