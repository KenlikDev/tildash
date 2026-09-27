package com.kenlikdev.tildash.content.workflow

import com.kenlikdev.tildash.content.model.ContentState
import com.kenlikdev.tildash.content.validation.ReviewOutcome
import com.kenlikdev.tildash.content.validation.ReviewResult
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.Test
import kotlin.time.Instant

class ContentWorkflowTest {
    private val actor = ContentWorkflowActor("teacher-1", WorkflowActorRole.TEACHER)
    private val reviewer = ContentWorkflowActor("reviewer-1", WorkflowActorRole.REVIEWER)
    private val now = Instant.parse("2026-09-27T00:00:00Z")

    @Test
    fun teacherCanSubmitDraftForReview() {
        val transition =
            ContentWorkflow.transition(
                state = ContentState.DRAFT,
                actor = actor,
                command = ContentWorkflowCommand.SUBMIT_FOR_REVIEW,
                validation = ReviewResult(emptyList()),
                reason = null,
                occurredAt = now,
            )

        assertEquals(ContentState.SUBMITTED, transition.newState)
        assertEquals(ContentWorkflowAction.SUBMIT, transition.event.action)
        assertEquals(actor.subject, transition.event.actorSubject)
    }

    @Test
    fun learnerCannotSubmitContent() {
        val learner = ContentWorkflowActor("learner-1", WorkflowActorRole.LEARNER)

        assertFailsWith<ContentWorkflowViolation> {
            ContentWorkflow.transition(
                state = ContentState.DRAFT,
                actor = learner,
                command = ContentWorkflowCommand.SUBMIT_FOR_REVIEW,
                validation = ReviewResult(emptyList()),
                reason = null,
                occurredAt = now,
            )
        }
    }

    @Test
    fun validationErrorsBlockSubmission() {
        val validation =
            ReviewResult(
                listOf(
                    com.kenlikdev.tildash.content.validation.ValidationIssue(
                        code = com.kenlikdev.tildash.content.validation.ValidationCode.MISSING_LICENSE,
                        severity = com.kenlikdev.tildash.content.validation.ValidationSeverity.ERROR,
                        message = "Missing license.",
                    ),
                ),
            )

        assertFailsWith<ContentWorkflowViolation> {
            ContentWorkflow.transition(
                state = ContentState.DRAFT,
                actor = actor,
                command = ContentWorkflowCommand.SUBMIT_FOR_REVIEW,
                validation = validation,
                reason = null,
                occurredAt = now,
            )
        }
    }

    @Test
    fun reviewerMovesSubmittedContentIntoReview() {
        val transition =
            ContentWorkflow.transition(
                state = ContentState.SUBMITTED,
                actor = reviewer,
                command = ContentWorkflowCommand.START_REVIEW,
                validation = ReviewResult(emptyList()),
                reason = null,
                occurredAt = now,
            )

        assertEquals(ContentState.UNDER_REVIEW, transition.newState)
    }

    @Test
    fun reviewerMustProvideReasonWhenRejecting() {
        assertFailsWith<ContentWorkflowViolation> {
            ContentWorkflow.transition(
                state = ContentState.UNDER_REVIEW,
                actor = reviewer,
                command = ContentWorkflowCommand.REJECT,
                validation = ReviewResult(emptyList()),
                reason = " ",
                occurredAt = now,
            )
        }
    }

    @Test
    fun rejectionReturnsContentToDraft() {
        val transition =
            ContentWorkflow.transition(
                state = ContentState.UNDER_REVIEW,
                actor = reviewer,
                command = ContentWorkflowCommand.REJECT,
                validation = ReviewResult(emptyList()),
                reason = "Fix the source attribution.",
                occurredAt = now,
            )

        assertEquals(ContentState.DRAFT, transition.newState)
        assertEquals("Fix the source attribution.", transition.event.reason)
    }

    @Test
    fun warningsDoNotPermitPublication() {
        val validation = ReviewResult(
            listOf(
                com.kenlikdev.tildash.content.validation.ValidationIssue(
                    code = com.kenlikdev.tildash.content.validation.ValidationCode.UNKNOWN_COPYRIGHT_STATUS,
                    severity = com.kenlikdev.tildash.content.validation.ValidationSeverity.WARNING,
                    message = "Unknown copyright status.",
                ),
            ),
        )

        assertEquals(ReviewOutcome.PASS_WITH_WARNINGS, validation.outcome)
        assertFailsWith<ContentWorkflowViolation> {
            ContentWorkflow.transition(
                state = ContentState.APPROVED,
                actor = reviewer,
                command = ContentWorkflowCommand.PUBLISH,
                validation = validation,
                reason = null,
                occurredAt = now,
            )
        }
    }

    @Test
    fun cleanValidationAllowsPublication() {
        val transition =
            ContentWorkflow.transition(
                state = ContentState.APPROVED,
                actor = reviewer,
                command = ContentWorkflowCommand.PUBLISH,
                validation = ReviewResult(emptyList()),
                reason = null,
                occurredAt = now,
            )

        assertEquals(ContentState.PUBLISHED, transition.newState)
        assertEquals(ContentWorkflowAction.PUBLISH, transition.event.action)
    }

    @Test
    fun publishedContentCanOnlyBeArchived() {
        val transition =
            ContentWorkflow.transition(
                state = ContentState.PUBLISHED,
                actor = reviewer,
                command = ContentWorkflowCommand.ARCHIVE,
                validation = ReviewResult(emptyList()),
                reason = "Replaced by a revised release.",
                occurredAt = now,
            )

        assertEquals(ContentState.ARCHIVED, transition.newState)
    }

    @Test
    fun reviewerFeedbackIsAuditableWithoutChangingState() {
        val transition =
            ContentWorkflow.transition(
                state = ContentState.UNDER_REVIEW,
                actor = reviewer,
                command = ContentWorkflowCommand.ADD_FEEDBACK,
                validation = ReviewResult(emptyList()),
                reason = "Please improve the exercise wording.",
                occurredAt = now,
            )

        assertEquals(ContentState.UNDER_REVIEW, transition.newState)
        assertEquals(ContentWorkflowAction.FEEDBACK, transition.event.action)
        assertEquals("Please improve the exercise wording.", transition.event.reason)
    }
}
