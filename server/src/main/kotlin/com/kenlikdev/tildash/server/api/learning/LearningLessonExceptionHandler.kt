package com.kenlikdev.tildash.server.api.learning

import com.kenlikdev.tildash.server.learning.PublishedLearningLessonInvalidException
import com.kenlikdev.tildash.server.learning.PublishedLearningLessonNotFoundException
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ProblemDetail
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.servlet.support.ServletUriComponentsBuilder
import java.net.URI

@RestControllerAdvice(assignableTypes = [LearningCatalogController::class])
class LearningLessonExceptionHandler {
    @ExceptionHandler(PublishedLearningLessonNotFoundException::class)
    fun handleNotFound(exception: PublishedLearningLessonNotFoundException): ResponseEntity<ProblemDetail> =
        problem(
            status = HttpStatus.NOT_FOUND,
            type = "urn:tildash:problem:learning-lesson-not-found",
            title = "Published learner lesson not found",
            detail = exception.message ?: "The requested published learner lesson does not exist.",
        )

    @ExceptionHandler(PublishedLearningLessonInvalidException::class)
    fun handleInvalidPackage(): ResponseEntity<ProblemDetail> =
        problem(
            status = HttpStatus.INTERNAL_SERVER_ERROR,
            type = "urn:tildash:problem:learning-lesson-invalid",
            title = "Published learner lesson unavailable",
            detail = "The published learner lesson could not be loaded.",
        )

    private fun problem(
        status: HttpStatus,
        type: String,
        title: String,
        detail: String,
    ): ResponseEntity<ProblemDetail> {
        val problem =
            ProblemDetail.forStatusAndDetail(status, detail).apply {
                this.type = URI.create(type)
                this.title = title
                instance = ServletUriComponentsBuilder.fromCurrentRequestUri().build().toUri()
            }

        return ResponseEntity
            .status(status)
            .contentType(MediaType.APPLICATION_PROBLEM_JSON)
            .body(problem)
    }
}
