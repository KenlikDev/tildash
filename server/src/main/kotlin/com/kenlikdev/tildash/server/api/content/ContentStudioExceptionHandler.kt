package com.kenlikdev.tildash.server.api.content

import com.kenlikdev.tildash.content.workflow.ContentWorkflowViolation
import com.kenlikdev.tildash.server.content.ContentNotFoundException
import com.kenlikdev.tildash.server.content.ContentValidationException
import java.net.URI
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ProblemDetail
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.servlet.support.ServletUriComponentsBuilder

@RestControllerAdvice(assignableTypes = [ContentStudioController::class])
class ContentStudioExceptionHandler {
    @ExceptionHandler(ContentNotFoundException::class)
    fun handleNotFound(
        exception: ContentNotFoundException,
    ): ResponseEntity<ProblemDetail> =
        problem(
            status = HttpStatus.NOT_FOUND,
            type = "urn:tildash:problem:content-not-found",
            title = "Content not found",
            detail = "Content '${exception.contentId.value}' was not found.",
        )

    @ExceptionHandler(ContentValidationException::class)
    fun handleValidation(
        exception: ContentValidationException,
    ): ResponseEntity<ProblemDetail> {
        val response = problem(
            status = HttpStatus.CONFLICT,
            type = "urn:tildash:problem:validation-failed",
            title = "Content validation failed",
            detail = "Deterministic content validation reported blocking errors.",
        )
        val body = requireNotNull(response.body)
        body.setProperty(
            "errors",
            exception.result.errors.map {
                mapOf(
                    "code" to it.code.name,
                    "message" to it.message,
                    "path" to it.path,
                )
            },
        )
        return response
    }

    @ExceptionHandler(ContentWorkflowViolation::class)
    fun handleWorkflowViolation(
        exception: ContentWorkflowViolation,
    ): ResponseEntity<ProblemDetail> =
        problem(
            status = HttpStatus.CONFLICT,
            type = "urn:tildash:problem:content-workflow-conflict",
            title = "Content workflow conflict",
            detail = exception.message
                ?: "The requested content workflow transition is not allowed.",
        )

    @ExceptionHandler(IllegalArgumentException::class)
    fun handleInvalidRequest(
        exception: IllegalArgumentException,
    ): ResponseEntity<ProblemDetail> =
        problem(
            status = HttpStatus.BAD_REQUEST,
            type = "urn:tildash:problem:invalid-content-request",
            title = "Invalid content request",
            detail = exception.message ?: "The content request is invalid.",
        )

    private fun problem(
        status: HttpStatus,
        type: String,
        title: String,
        detail: String,
    ): ResponseEntity<ProblemDetail> {
        val uri = ServletUriComponentsBuilder.fromCurrentRequestUri().build().toUri()
        val problem = ProblemDetail.forStatusAndDetail(status, detail).apply {
            this.type = URI.create(type)
            this.title = title
            instance = uri
        }
        return ResponseEntity
            .status(status)
            .contentType(MediaType.APPLICATION_PROBLEM_JSON)
            .body(problem)
    }
}
