package com.kenlikdev.tildash.server.content

import com.kenlikdev.tildash.content.model.ContentId
import com.kenlikdev.tildash.content.model.ContentKind
import com.kenlikdev.tildash.content.model.ContentNode
import com.kenlikdev.tildash.content.model.ContentPayload
import com.kenlikdev.tildash.content.model.ContentState
import com.kenlikdev.tildash.content.model.ContentVariantType
import com.kenlikdev.tildash.content.model.CopyrightStatus
import com.kenlikdev.tildash.content.model.LanguageTag
import com.kenlikdev.tildash.content.model.LicenseReference
import com.kenlikdev.tildash.content.model.PersonReference
import com.kenlikdev.tildash.content.model.Provenance
import com.kenlikdev.tildash.content.model.SourceReference
import com.kenlikdev.tildash.content.validation.ContentValidator
import com.kenlikdev.tildash.content.validation.LessonValidationInput
import com.kenlikdev.tildash.content.validation.ReviewResult
import com.kenlikdev.tildash.content.workflow.ContentWorkflow
import com.kenlikdev.tildash.content.workflow.ContentWorkflowActor
import com.kenlikdev.tildash.content.workflow.ContentWorkflowCommand
import com.kenlikdev.tildash.content.workflow.ContentWorkflowViolation
import com.kenlikdev.tildash.content.workflow.WorkflowActorRole
import com.kenlikdev.tildash.server.api.content.AppendSourceRevisionRequest
import com.kenlikdev.tildash.server.api.content.ContentMutationResponse
import com.kenlikdev.tildash.server.api.content.ContentPreviewResponse
import com.kenlikdev.tildash.server.api.content.CreateContentNodeRequest
import com.kenlikdev.tildash.server.api.content.PayloadRequest
import com.kenlikdev.tildash.server.api.content.PayloadType
import com.kenlikdev.tildash.server.api.content.ProvenanceRequest
import com.kenlikdev.tildash.server.api.content.ReviewDecisionResponse
import com.kenlikdev.tildash.server.api.content.ReviewHistoryResponse
import com.kenlikdev.tildash.server.security.AuthenticatedIdentity
import com.kenlikdev.tildash.server.security.CurrentIdentityProvider
import com.kenlikdev.tildash.server.security.Role
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID

class ContentNotFoundException(
    val contentId: ContentId,
) : RuntimeException("Content '${contentId.value}' was not found.")

class ContentValidationException(
    val result: ReviewResult,
) : RuntimeException("Deterministic content validation failed.")

@Service
class ContentStudioService(
    private val repository: ContentStudioRepository,
    private val currentIdentityProvider: CurrentIdentityProvider,
    private val validator: ContentValidator,
) {
    @Transactional
    fun createNode(request: CreateContentNodeRequest): ContentMutationResponse {
        val identity = currentIdentityProvider.current()
        requireAuthor(identity)
        val id = ContentId(UUID.randomUUID().toString())
        val kind = request.kind
        val payload = request.payload.toDomain()
        requirePayloadMatchesKind(kind, payload)

        val node =
            ContentNode(
                id = id,
                kind = kind,
                parentId = request.parentId?.let(::ContentId),
                sourceLocale = LanguageTag(request.sourceLocale),
                state = ContentState.DRAFT,
                position = request.position,
            )

        repository.insertNode(node)
        repository.appendSourceRevision(
            contentId = id,
            revision = 1,
            payload = request.payload,
            provenance = request.provenance,
            createdBy = identity.asPersonReference(),
            createdAt = now(),
        )

        return ContentMutationResponse(
            id = id.value,
            state = ContentState.DRAFT,
            revision = 1,
        )
    }

    @Transactional
    fun appendSourceRevision(
        contentId: ContentId,
        request: AppendSourceRevisionRequest,
    ): ContentMutationResponse {
        val identity = currentIdentityProvider.current()
        requireAuthor(identity)
        val stored = requireNode(contentId)
        requireDraft(stored.node.state)
        val payload = request.payload.toDomain()
        requirePayloadMatchesKind(stored.node.kind, payload)

        val revision = repository.nextRevision(contentId)
        repository.appendSourceRevision(
            contentId = contentId,
            revision = revision,
            payload = request.payload,
            provenance = request.provenance,
            createdBy = identity.asPersonReference(),
            createdAt = now(),
        )

        return ContentMutationResponse(
            id = contentId.value,
            state = ContentState.DRAFT,
            revision = revision,
        )
    }

    @Transactional(readOnly = true)
    fun preview(contentId: ContentId): ContentPreviewResponse {
        val identity = currentIdentityProvider.current()
        requireAuthorOrReviewer(identity)
        val preview = repository.preview(contentId) ?: throw ContentNotFoundException(contentId)

        return ContentPreviewResponse(
            id = preview.node.id.value,
            kind = preview.node.kind,
            state = preview.node.state,
            revision = preview.revision,
            sourceLocale = preview.node.sourceLocale.value,
            payload = preview.payload,
        )
    }

    @Transactional
    fun submit(contentId: ContentId): ContentMutationResponse {
        val identity = currentIdentityProvider.current()
        requireAuthor(identity)
        return transition(
            contentId = contentId,
            actor = workflowActor(identity, WorkflowActorRole.TEACHER),
            command = ContentWorkflowCommand.SUBMIT_FOR_REVIEW,
            reason = null,
            validate = true,
        )
    }

    @Transactional
    fun startReview(contentId: ContentId): ContentMutationResponse {
        val identity = currentIdentityProvider.current()
        requireReviewer(identity)
        return transition(
            contentId = contentId,
            actor = workflowActor(identity, WorkflowActorRole.REVIEWER),
            command = ContentWorkflowCommand.START_REVIEW,
            reason = null,
            validate = false,
        )
    }

    @Transactional
    fun addFeedback(
        contentId: ContentId,
        reason: String?,
    ): ContentMutationResponse {
        val identity = currentIdentityProvider.current()
        requireReviewer(identity)
        return transition(
            contentId = contentId,
            actor = workflowActor(identity, WorkflowActorRole.REVIEWER),
            command = ContentWorkflowCommand.ADD_FEEDBACK,
            reason = reason,
            validate = false,
        )
    }

    @Transactional
    fun approve(contentId: ContentId): ContentMutationResponse {
        val identity = currentIdentityProvider.current()
        requireReviewer(identity)
        return transition(
            contentId = contentId,
            actor = workflowActor(identity, WorkflowActorRole.REVIEWER),
            command = ContentWorkflowCommand.APPROVE,
            reason = null,
            validate = true,
        )
    }

    @Transactional
    fun reject(
        contentId: ContentId,
        reason: String?,
    ): ContentMutationResponse {
        val identity = currentIdentityProvider.current()
        requireReviewer(identity)
        return transition(
            contentId = contentId,
            actor = workflowActor(identity, WorkflowActorRole.REVIEWER),
            command = ContentWorkflowCommand.REJECT,
            reason = reason,
            validate = false,
        )
    }

    @Transactional
    fun publish(contentId: ContentId): ContentMutationResponse {
        val identity = currentIdentityProvider.current()
        requireReviewer(identity)
        val response =
            transition(
                contentId = contentId,
                actor = workflowActor(identity, WorkflowActorRole.REVIEWER),
                command = ContentWorkflowCommand.PUBLISH,
                reason = null,
                validate = true,
            )
        val version =
            repository.publishSnapshot(
                contentId = contentId,
                publishedBy = identity.subject,
                publishedAt = now(),
            )

        return response.copy(version = version)
    }

    @Transactional
    fun archive(
        contentId: ContentId,
        reason: String?,
    ): ContentMutationResponse {
        val identity = currentIdentityProvider.current()
        requireReviewer(identity)
        return transition(
            contentId = contentId,
            actor = workflowActor(identity, WorkflowActorRole.REVIEWER),
            command = ContentWorkflowCommand.ARCHIVE,
            reason = reason,
            validate = false,
        )
    }

    @Transactional(readOnly = true)
    fun reviewHistory(contentId: ContentId): List<ReviewHistoryResponse> {
        val identity = currentIdentityProvider.current()
        requireAuthorOrReviewer(identity)
        requireNode(contentId)

        return repository.reviewHistory(contentId).map { stored ->
            ReviewHistoryResponse(
                id = stored.id.toString(),
                action = stored.event.action.name,
                fromState = stored.event.fromState,
                toState = stored.event.toState,
                actorSubject = stored.event.actorSubject,
                reason = stored.event.reason,
                validationOutcome = stored.event.validationOutcome,
                occurredAt = stored.event.occurredAt.toJavaOffsetDateTime(),
            )
        }
    }

    private fun transition(
        contentId: ContentId,
        actor: ContentWorkflowActor,
        command: ContentWorkflowCommand,
        reason: String?,
        validate: Boolean,
    ): ContentMutationResponse {
        val stored = requireNode(contentId)
        if (stored.node.kind != ContentKind.LESSON) {
            throw ContentWorkflowViolation("Content workflow is only available for lesson nodes.")
        }

        val validation =
            if (validate) {
                validateLesson(contentId)
            } else {
                ReviewResult(emptyList())
            }

        val transition =
            try {
                ContentWorkflow.transition(
                    state = stored.node.state,
                    actor = actor,
                    command = command,
                    validation = validation,
                    reason = reason,
                    occurredAt = now().toKotlinInstant(),
                )
            } catch (ex: ContentWorkflowViolation) {
                throw ex
            }

        val updated =
            repository.updateState(
                contentId = contentId,
                expected = stored.node.state,
                next = transition.newState,
                updatedAt = now(),
            )
        if (!updated) {
            throw ContentWorkflowViolation(
                "Content '${contentId.value}' changed concurrently; retry the operation.",
            )
        }

        repository.insertWorkflowEvent(contentId, transition.event)

        return ContentMutationResponse(
            id = contentId.value,
            state = transition.newState,
        )
    }

    private fun validateLesson(contentId: ContentId): ReviewResult {
        val input = repository.validationInput(contentId)
        if (input.nodes.isEmpty()) throw ContentNotFoundException(contentId)
        val result =
            validator.validateLesson(
                LessonValidationInput(
                    lessonId = contentId,
                    nodes = input.nodes,
                    sourceRevisions = input.sourceRevisions,
                    localizationRevisions = input.localizations,
                ),
            )
        if (!result.canSubmit) throw ContentValidationException(result)
        return result
    }

    private fun requireNode(contentId: ContentId): StoredContentNode =
        repository.findNode(contentId) ?: throw ContentNotFoundException(contentId)

    private fun requireDraft(state: ContentState) {
        if (state != ContentState.DRAFT) {
            throw ContentWorkflowViolation("Content can only be edited while it is in DRAFT.")
        }
    }

    private fun requirePayloadMatchesKind(
        kind: ContentKind,
        payload: ContentPayload,
    ) {
        val valid =
            when (kind) {
                ContentKind.COURSE,
                ContentKind.LESSON,
                ContentKind.EXAMPLE,
                -> {
                    payload is ContentPayload.Text
                }

                ContentKind.VOCABULARY -> {
                    payload is ContentPayload.Vocabulary
                }

                ContentKind.MEDIA_REFERENCE -> {
                    payload is ContentPayload.MediaReference
                }
            }
        if (!valid) {
            throw IllegalArgumentException("Payload type does not match content kind '$kind'.")
        }
    }

    private fun requireAuthor(identity: AuthenticatedIdentity) {
        checkRole(identity, Role.TEACHER, Role.ADMINISTRATOR)
    }

    private fun requireReviewer(identity: AuthenticatedIdentity) {
        checkRole(identity, Role.REVIEWER, Role.ADMINISTRATOR)
    }

    private fun requireAuthorOrReviewer(identity: AuthenticatedIdentity) {
        checkRole(identity, Role.TEACHER, Role.REVIEWER, Role.ADMINISTRATOR)
    }

    private fun checkRole(
        identity: AuthenticatedIdentity,
        vararg roles: Role,
    ) {
        if (identity.roles.none { it in roles }) {
            throw ContentWorkflowViolation("The current identity is not authorized for this content operation.")
        }
    }

    private fun workflowActor(
        identity: AuthenticatedIdentity,
        preferred: WorkflowActorRole,
    ): ContentWorkflowActor {
        val role =
            when {
                Role.ADMINISTRATOR in identity.roles -> WorkflowActorRole.ADMINISTRATOR
                preferred == WorkflowActorRole.TEACHER && Role.TEACHER in identity.roles -> WorkflowActorRole.TEACHER
                preferred == WorkflowActorRole.REVIEWER && Role.REVIEWER in identity.roles -> WorkflowActorRole.REVIEWER
                else -> throw ContentWorkflowViolation("The current identity cannot perform this content workflow action.")
            }
        return ContentWorkflowActor(identity.subject, role)
    }

    private fun now(): OffsetDateTime = OffsetDateTime.now(ZoneOffset.UTC)
}

private fun PayloadRequest.toDomain(): ContentPayload =
    when (type) {
        PayloadType.TEXT -> {
            ContentPayload.Text(requireNotNull(value) { "Text payload requires value." })
        }

        PayloadType.VOCABULARY -> {
            ContentPayload.Vocabulary(
                term = requireNotNull(term) { "Vocabulary payload requires term." },
                gloss = requireNotNull(gloss) { "Vocabulary payload requires gloss." },
            )
        }

        PayloadType.MEDIA_REFERENCE -> {
            ContentPayload.MediaReference(
                uri = requireNotNull(uri) { "Media payload requires uri." },
                mediaType = mediaType,
            )
        }
    }

private fun ProvenanceRequest.toDomain(): Provenance =
    Provenance(
        source = SourceReference(sourceTitle, sourceLocator),
        author = authorName?.takeIf { it.isNotBlank() }?.let(::PersonReference),
        speaker = speakerName?.takeIf { it.isNotBlank() }?.let(::PersonReference),
        dialect = dialect?.takeIf { it.isNotBlank() }?.let(::LanguageTag),
        variant = variant?.takeIf { it.isNotBlank() }?.uppercase()?.let(ContentVariantType::valueOf),
        license =
            licenseIdentifier
                ?.takeIf { it.isNotBlank() }
                ?.let { LicenseReference(it, licenseUrl) },
        copyrightStatus = copyrightStatus,
        reviewer = null,
        verifiedAt = null,
    )

private fun AuthenticatedIdentity.asPersonReference() = PersonReference(displayName = subject, externalId = subject)

private fun kotlin.time.Instant.toJavaOffsetDateTime() =
    java.time.Instant
        .ofEpochSecond(epochSeconds, nanosecondsOfSecond.toLong())
        .atOffset(ZoneOffset.UTC)

private fun OffsetDateTime.toKotlinInstant() = kotlin.time.Instant.fromEpochMilliseconds(toInstant().toEpochMilli())
