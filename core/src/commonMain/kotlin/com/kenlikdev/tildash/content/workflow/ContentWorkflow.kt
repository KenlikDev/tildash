package com.kenlikdev.tildash.content.workflow

import com.kenlikdev.tildash.content.model.ContentState
import com.kenlikdev.tildash.content.validation.ReviewResult
import kotlin.time.Instant

enum class WorkflowActorRole {
    LEARNER,
    TEACHER,
    REVIEWER,
    ADMINISTRATOR,
}

data class ContentWorkflowActor(
    val subject: String,
    val role: WorkflowActorRole,
) {
    init {
        require(subject.isNotBlank()) {
            "Workflow actor subject must not be blank."
        }
    }
}

enum class ContentWorkflowCommand {
    SUBMIT_FOR_REVIEW,
    START_REVIEW,
    ADD_FEEDBACK,
    APPROVE,
    REJECT,
    PUBLISH,
    ARCHIVE,
}

enum class ContentWorkflowAction {
    SUBMIT,
    START_REVIEW,
    FEEDBACK,
    APPROVE,
    REJECT,
    PUBLISH,
    ARCHIVE,
}

data class ContentWorkflowEvent(
    val action: ContentWorkflowAction,
    val actorSubject: String,
    val fromState: ContentState,
    val toState: ContentState,
    val reason: String?,
    val validationOutcome: String?,
    val occurredAt: Instant,
)

data class ContentWorkflowTransition(
    val newState: ContentState,
    val event: ContentWorkflowEvent,
)

class ContentWorkflowViolation(
    message: String,
) : IllegalStateException(message)

object ContentWorkflow {
    fun transition(
        state: ContentState,
        actor: ContentWorkflowActor,
        command: ContentWorkflowCommand,
        validation: ReviewResult,
        reason: String?,
        occurredAt: Instant,
    ): ContentWorkflowTransition =
        when (command) {
            ContentWorkflowCommand.SUBMIT_FOR_REVIEW -> {
                requireRole(actor, WorkflowActorRole.TEACHER, WorkflowActorRole.ADMINISTRATOR)
                requireState(state, ContentState.DRAFT, command)
                requireSubmitValidation(validation)
                transitionTo(
                    ContentWorkflowAction.SUBMIT,
                    actor,
                    state,
                    ContentState.SUBMITTED,
                    reason,
                    validation,
                    occurredAt,
                )
            }

            ContentWorkflowCommand.START_REVIEW -> {
                requireReviewer(actor)
                requireState(state, ContentState.SUBMITTED, command)
                transitionTo(
                    ContentWorkflowAction.START_REVIEW,
                    actor,
                    state,
                    ContentState.UNDER_REVIEW,
                    reason,
                    validation,
                    occurredAt,
                )
            }

            ContentWorkflowCommand.ADD_FEEDBACK -> {
                requireReviewer(actor)
                requireState(state, ContentState.UNDER_REVIEW, command)
                val normalizedReason = requireReason(command, reason)
                transitionTo(
                    ContentWorkflowAction.FEEDBACK,
                    actor,
                    state,
                    state,
                    normalizedReason,
                    validation,
                    occurredAt,
                )
            }

            ContentWorkflowCommand.APPROVE -> {
                requireReviewer(actor)
                requireState(state, ContentState.UNDER_REVIEW, command)
                requireCleanValidation(validation, command)
                transitionTo(
                    ContentWorkflowAction.APPROVE,
                    actor,
                    state,
                    ContentState.APPROVED,
                    reason,
                    validation,
                    occurredAt,
                )
            }

            ContentWorkflowCommand.REJECT -> {
                requireReviewer(actor)
                requireState(state, ContentState.UNDER_REVIEW, command)
                val normalizedReason = requireReason(command, reason)
                transitionTo(
                    ContentWorkflowAction.REJECT,
                    actor,
                    state,
                    ContentState.DRAFT,
                    normalizedReason,
                    validation,
                    occurredAt,
                )
            }

            ContentWorkflowCommand.PUBLISH -> {
                requireReviewer(actor)
                requireState(state, ContentState.APPROVED, command)
                requireCleanValidation(validation, command)
                transitionTo(
                    ContentWorkflowAction.PUBLISH,
                    actor,
                    state,
                    ContentState.PUBLISHED,
                    reason,
                    validation,
                    occurredAt,
                )
            }

            ContentWorkflowCommand.ARCHIVE -> {
                requireReviewer(actor)
                requireState(state, ContentState.PUBLISHED, command)
                transitionTo(
                    ContentWorkflowAction.ARCHIVE,
                    actor,
                    state,
                    ContentState.ARCHIVED,
                    reason,
                    validation,
                    occurredAt,
                )
            }
        }
    }

    private fun transitionTo(
        action: ContentWorkflowAction,
        actor: ContentWorkflowActor,
        fromState: ContentState,
        toState: ContentState,
        reason: String?,
        validation: ReviewResult,
        occurredAt: Instant,
    ) = ContentWorkflowTransition(
        newState = toState,
        event =
            ContentWorkflowEvent(
                action = action,
                actorSubject = actor.subject,
                fromState = fromState,
                toState = toState,
                reason = reason?.trim()?.takeIf { it.isNotEmpty() },
                validationOutcome = validation.outcome.name,
                occurredAt = occurredAt,
            ),
    )

    private fun requireReviewer(actor: ContentWorkflowActor) {
        requireRole(actor, WorkflowActorRole.REVIEWER, WorkflowActorRole.ADMINISTRATOR)
    }

    private fun requireRole(
        actor: ContentWorkflowActor,
        vararg allowedRoles: WorkflowActorRole,
    ) {
        if (actor.role !in allowedRoles) {
            throw ContentWorkflowViolation(
                "Role '${actor.role}' cannot perform the requested content workflow action.",
            )
        }
    }

    private fun requireState(
        state: ContentState,
        expected: ContentState,
        command: ContentWorkflowCommand,
    ) {
        if (state != expected) {
            throw ContentWorkflowViolation(
                "Cannot perform $command while content is in state $state; expected $expected.",
            )
        }
    }

    private fun requireReason(
        command: ContentWorkflowCommand,
        reason: String?,
    ): String =
        reason?.trim()?.takeIf { it.isNotEmpty() }
            ?: throw ContentWorkflowViolation(
                "$command requires a non-blank reason.",
            )

    private fun requireSubmitValidation(validation: ReviewResult) {
        if (!validation.canSubmit) {
            throw ContentWorkflowViolation(
                "Cannot submit content while deterministic validation reports errors.",
            )
        }
    }

    private fun requireCleanValidation(
        validation: ReviewResult,
        command: ContentWorkflowCommand,
    ) {
        if (!validation.canPublish) {
            throw ContentWorkflowViolation(
                "Cannot perform $command unless deterministic validation returns PASS.",
            )
        }
    }
}
