package com.kenlikdev.tildash.server.api.learning

import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ProblemDetail
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.servlet.support.ServletUriComponentsBuilder
import java.net.URI

@RestControllerAdvice(assignableTypes = [LearningSyncController::class])
class LearningSyncExceptionHandler {
    @ExceptionHandler(IllegalArgumentException::class, IllegalStateException::class)
    fun handleInvalidRequest(exception: RuntimeException): ResponseEntity<ProblemDetail> {
        val problem =
            ProblemDetail
                .forStatusAndDetail(
                HttpStatus.BAD_REQUEST,
                exception.message ?: "The learning synchronization request is invalid.",
            ).apply {
                type = URI.create("urn:tildash:problem:learning-sync-invalid-request")
                title = "Invalid learning synchronization request"
                instance = ServletUriComponentsBuilder.fromCurrentRequestUri().build().toUri()
            }

        return ResponseEntity
            .status(HttpStatus.BAD_REQUEST)
            .contentType(MediaType.APPLICATION_PROBLEM_JSON)
            .body(problem)
    }
}
