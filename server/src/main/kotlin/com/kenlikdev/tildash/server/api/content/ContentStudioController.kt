package com.kenlikdev.tildash.server.api.content

import com.kenlikdev.tildash.content.model.ContentId
import com.kenlikdev.tildash.server.content.ContentStudioService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import jakarta.validation.Valid
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/content")
class ContentStudioController(
    private val service: ContentStudioService,
) {
    @PostMapping("/nodes")
    @PreAuthorize("hasAnyRole('TEACHER', 'ADMINISTRATOR')")
    @Operation(summary = "Create a content node in draft state")
    @ApiResponses(
        ApiResponse(responseCode = "201", description = "Content node created"),
        ApiResponse(responseCode = "403", description = "Teacher or administrator role required"),
    )
    fun createNode(
        @Valid @RequestBody request: CreateContentNodeRequest,
    ): ResponseEntity<ContentMutationResponse> =
        ResponseEntity
            .status(201)
            .body(service.createNode(request))

    @GetMapping("/{contentId}/exercises")
    @PreAuthorize("hasAnyRole('TEACHER', 'REVIEWER', 'ADMINISTRATOR')")
    @Operation(summary = "List draft lesson exercises")
    fun listExercises(@PathVariable contentId: String): List<ContentExerciseResponse> =
        service.listExercises(contentId.toContentId())

    @PostMapping("/{contentId}/exercises")
    @PreAuthorize("hasAnyRole('TEACHER', 'ADMINISTRATOR')")
    @Operation(summary = "Create a manual-input lesson exercise")
    fun createExercise(
        @PathVariable contentId: String,
        @Valid @RequestBody request: CreateExerciseRequest,
    ): ResponseEntity<ContentExerciseResponse> =
        ResponseEntity
            .status(201)
            .body(service.createExercise(contentId.toContentId(), request))

    @PutMapping("/{contentId}/exercises/{exerciseId}")
    @PreAuthorize("hasAnyRole('TEACHER', 'ADMINISTRATOR')")
    @Operation(summary = "Update a draft lesson exercise")
    fun updateExercise(
        @PathVariable contentId: String,
        @PathVariable exerciseId: String,
        @Valid @RequestBody request: UpdateExerciseRequest,
    ): ContentExerciseResponse = service.updateExercise(contentId.toContentId(), exerciseId, request)

    @DeleteMapping("/{contentId}/exercises/{exerciseId}")
    @PreAuthorize("hasAnyRole('TEACHER', 'ADMINISTRATOR')")
    @Operation(summary = "Delete a draft lesson exercise")
    fun deleteExercise(
        @PathVariable contentId: String,
        @PathVariable exerciseId: String,
    ) = service.deleteExercise(contentId.toContentId(), exerciseId)

    @GetMapping("/{contentId}/preview")
    @PreAuthorize("hasAnyRole('TEACHER', 'REVIEWER', 'ADMINISTRATOR')")
    @Operation(summary = "Preview the latest content revision")
    fun preview(
        @PathVariable contentId: String,
    ): ContentPreviewResponse = service.preview(contentId.toContentId())

    @PostMapping("/{contentId}/source-revisions")
    @PreAuthorize("hasAnyRole('TEACHER', 'ADMINISTRATOR')")
    @Operation(summary = "Append a new draft source revision")
    fun appendSourceRevision(
        @PathVariable contentId: String,
        @Valid @RequestBody request: AppendSourceRevisionRequest,
    ): ResponseEntity<ContentMutationResponse> =
        ResponseEntity
            .status(201)
            .body(service.appendSourceRevision(contentId.toContentId(), request))

    @PostMapping("/{contentId}/submit")
    @PreAuthorize("hasAnyRole('TEACHER', 'ADMINISTRATOR')")
    @Operation(summary = "Submit a validated draft for review")
    fun submit(
        @PathVariable contentId: String,
    ): ContentMutationResponse = service.submit(contentId.toContentId())

    @PostMapping("/{contentId}/review/start")
    @PreAuthorize("hasAnyRole('REVIEWER', 'ADMINISTRATOR')")
    @Operation(summary = "Start reviewer work on submitted content")
    fun startReview(
        @PathVariable contentId: String,
    ): ContentMutationResponse = service.startReview(contentId.toContentId())

    @PostMapping("/{contentId}/review/feedback")
    @PreAuthorize("hasAnyRole('REVIEWER', 'ADMINISTRATOR')")
    @Operation(summary = "Add auditable reviewer feedback")
    fun addFeedback(
        @PathVariable contentId: String,
        @Valid @RequestBody request: WorkflowReasonRequest,
    ): ContentMutationResponse = service.addFeedback(contentId.toContentId(), request.reason)

    @PostMapping("/{contentId}/review/approve")
    @PreAuthorize("hasAnyRole('REVIEWER', 'ADMINISTRATOR')")
    @Operation(summary = "Approve content after deterministic validation")
    fun approve(
        @PathVariable contentId: String,
    ): ContentMutationResponse = service.approve(contentId.toContentId())

    @PostMapping("/{contentId}/review/reject")
    @PreAuthorize("hasAnyRole('REVIEWER', 'ADMINISTRATOR')")
    @Operation(summary = "Reject content and return it to draft")
    fun reject(
        @PathVariable contentId: String,
        @Valid @RequestBody request: WorkflowReasonRequest,
    ): ContentMutationResponse = service.reject(contentId.toContentId(), request.reason)

    @PostMapping("/{contentId}/publish")
    @PreAuthorize("hasAnyRole('REVIEWER', 'ADMINISTRATOR')")
    @Operation(summary = "Publish approved content as an immutable version")
    fun publish(
        @PathVariable contentId: String,
    ): ContentMutationResponse = service.publish(contentId.toContentId())

    @PostMapping("/{contentId}/archive")
    @PreAuthorize("hasAnyRole('REVIEWER', 'ADMINISTRATOR')")
    @Operation(summary = "Archive a published version")
    fun archive(
        @PathVariable contentId: String,
        @Valid @RequestBody(required = false) request: WorkflowReasonRequest?,
    ): ContentMutationResponse = service.archive(contentId.toContentId(), request?.reason)

    @GetMapping("/{contentId}/review-history", produces = [MediaType.APPLICATION_JSON_VALUE])
    @PreAuthorize("hasAnyRole('TEACHER', 'REVIEWER', 'ADMINISTRATOR')")
    @Operation(summary = "Read immutable content review history")
    fun reviewHistory(
        @PathVariable contentId: String,
    ): List<ReviewHistoryResponse> = service.reviewHistory(contentId.toContentId())
}

private fun String.toContentId() = ContentId(this)
