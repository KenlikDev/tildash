package com.kenlikdev.tildash.server.api.learning

import com.kenlikdev.tildash.server.learning.LearningSyncService
import com.kenlikdev.tildash.server.security.CurrentIdentityProvider
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import jakarta.validation.Valid
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/learning")
class LearningSyncController(
    private val service: LearningSyncService,
    private val currentIdentityProvider: CurrentIdentityProvider,
) {
    @PostMapping("/sync")
    @PreAuthorize("hasRole('LEARNER')")
    @Operation(
        summary = "Synchronize immutable learner attempts",
        description = "Idempotently accepts learner attempts and explicitly reports conflicting attempt IDs.",
    )
    @ApiResponses(
        ApiResponse(responseCode = "200", description = "Synchronization completed"),
        ApiResponse(responseCode = "400", description = "Invalid synchronization request"),
        ApiResponse(responseCode = "401", description = "Authentication required"),
    )
    fun synchronize(
        @Valid @RequestBody request: LearningSyncRequest,
    ): LearningSyncResponse =
        service.synchronize(
            learnerSubject = currentIdentityProvider.current().subject,
            request = request,
        )
}
