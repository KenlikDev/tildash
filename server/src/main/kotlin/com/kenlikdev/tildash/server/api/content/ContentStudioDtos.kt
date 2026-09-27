package com.kenlikdev.tildash.server.api.content

import com.kenlikdev.tildash.content.model.ContentKind
import com.kenlikdev.tildash.content.model.ContentState
import com.kenlikdev.tildash.content.model.CopyrightStatus
import jakarta.validation.Valid
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import tools.jackson.databind.JsonNode
import java.time.OffsetDateTime
import java.util.UUID

enum class PayloadType {
    TEXT,
    VOCABULARY,
    MEDIA_REFERENCE,
}

data class PayloadRequest(
    @field:NotNull
    val type: PayloadType,
    val value: String? = null,
    val term: String? = null,
    val gloss: String? = null,
    val uri: String? = null,
    val mediaType: String? = null,
)

data class ProvenanceRequest(
    @field:NotBlank
    val sourceTitle: String,
    val sourceLocator: String? = null,
    val authorName: String? = null,
    val speakerName: String? = null,
    val dialect: String? = null,
    val variant: String? = null,
    val licenseIdentifier: String? = null,
    val licenseUrl: String? = null,
    @field:NotNull
    val copyrightStatus: CopyrightStatus,
)

data class CreateContentNodeRequest(
    @field:NotNull
    val kind: ContentKind,
    val parentId: String? = null,
    @field:NotBlank
    val sourceLocale: String,
    @field:Min(0)
    val position: Int,
    @field:Valid
    @field:NotNull
    val payload: PayloadRequest,
    @field:Valid
    @field:NotNull
    val provenance: ProvenanceRequest,
)

data class AppendSourceRevisionRequest(
    @field:Valid
    @field:NotNull
    val payload: PayloadRequest,
    @field:Valid
    @field:NotNull
    val provenance: ProvenanceRequest,
)

data class WorkflowReasonRequest(
    val reason: String? = null,
)

data class ContentMutationResponse(
    val id: String,
    val state: ContentState,
    val revision: Int? = null,
    val version: Int? = null,
)

data class ContentPreviewResponse(
    val id: String,
    val kind: ContentKind,
    val state: ContentState,
    val revision: Int,
    val sourceLocale: String,
    val payload: JsonNode,
)

data class ReviewHistoryResponse(
    val id: String,
    val action: String,
    val fromState: ContentState,
    val toState: ContentState,
    val actorSubject: String,
    val reason: String?,
    val validationOutcome: String?,
    val occurredAt: OffsetDateTime,
)

data class ReviewDecisionResponse(
    val id: String,
    val state: ContentState,
    val validationOutcome: String,
)
