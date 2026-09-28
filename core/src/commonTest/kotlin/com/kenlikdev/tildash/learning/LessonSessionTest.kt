package com.kenlikdev.tildash.learning

import com.kenlikdev.tildash.content.model.ContentId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

class LessonSessionTest {
    private val occurredAt = Instant.parse("2026-09-28T08:00:00Z")
    private val later = Instant.parse("2026-09-29T08:00:00Z")
    private val lessonId = ContentId("550e8400-e29b-41d4-a716-446655440000")
    private val first = manualExercise("exercise-1", "Hello")
    private val second = manualExercise("exercise-2", "World")
    private val third = manualExercise("exercise-3", "Goodbye")
    private val plan = LearningPlan(lessonId, listOf(first, second, third))

    @Test
    fun newSessionStartsWithFirstExercise() {
        val session = LessonSession.start(plan)

        assertEquals(LessonSessionState.ACTIVE, session.state)
        assertEquals(first.id, session.nextExercise?.id)
    }

    @Test
    fun completedExercisesAreSkippedInPlanOrder() {
        var progress = LearningProgress.empty()

        progress =
            LearningEngine
                .submit(
                    progress = progress,
                exercise = first,
                attemptId = "attempt-1",
                response = LearnerResponse.Text("hello"),
                occurredAt = occurredAt,
                    )
                .progress

        val session = LessonSession.start(plan, progress)

        assertEquals(second.id, session.nextExercise?.id)
    }

    @Test
    fun sessionSubmissionDelegatesToLearningEngine() {
        val session = LessonSession.start(plan)

        val submission =
            session
                .submit(
                    attemptId = "attempt-1",
                response = LearnerResponse.Text("hello"),
                occurredAt = occurredAt,
            )

        assertEquals(AnswerOutcome.CORRECT, submission.evaluation.outcome)
        assertEquals(second.id, submission.session.nextExercise?.id)
        assertEquals(1, submission.session.progress.attempts.size)
    }

    @Test
    fun failedAttemptKeepsTheSameExercisePending() {
        val session = LessonSession.start(plan)

        val submission =
            session
                .submit(
                    attemptId = "attempt-1",
                response = LearnerResponse.Text("wrong"),
                occurredAt = occurredAt,
            )

        assertEquals(AnswerOutcome.INCORRECT, submission.evaluation.outcome)
        assertEquals(first.id, submission.session.nextExercise?.id)
        assertEquals(1, submission.session.progress.mistakeCount(first.id))
    }

    @Test
    fun repeatedAttemptIdRemainsIdempotentThroughSession() {
        val session = LessonSession.start(plan)

        val firstSubmission =
            session
                .submit(
                    attemptId = "attempt-1",
                response = LearnerResponse.Text("hello"),
                occurredAt = occurredAt,
            )
        val resumedSession = LessonSession.start(plan, firstSubmission.session.progress)
        val repeatedSubmission =
            resumedSession.submit(
                attemptId = "attempt-1",
                response = LearnerResponse.Text("hello"),
                occurredAt = occurredAt,
            )

        assertEquals(firstSubmission.session.progress, repeatedSubmission.session.progress)
        assertEquals(second.id, repeatedSubmission.session.nextExercise?.id)
    }

    @Test
    fun sessionResumesFromPersistedEquivalentProgress() {
        var progress = LearningProgress.empty()

        progress =
            LearningEngine
                .submit(
                    progress = progress,
                exercise = first,
                attemptId = "attempt-1",
                response = LearnerResponse.Text("hello"),
                occurredAt = occurredAt,
                    )
                .progress
        progress =
            LearningEngine
                .submit(
                    progress = progress,
                exercise = second,
                attemptId = "attempt-2",
                response = LearnerResponse.Text("world"),
                occurredAt = later,
                    )
                .progress

        val resumed = LessonSession.start(plan, progress)

        assertEquals(third.id, resumed.nextExercise?.id)
        assertEquals(2, resumed.progress.attempts.size)
        assertTrue(resumed.progress.isLessonComplete(LearningPlan(lessonId, listOf(first, second))))
    }

    @Test
    fun finalSuccessfulSubmissionProducesCompletedSession() {
        var session = LessonSession.start(plan)

        session =
            session
                .submit(
                    attemptId = "attempt-1",
                response = LearnerResponse.Text("hello"),
                occurredAt = occurredAt,
                    )
                .session
        session =
            session
                .submit(
                    attemptId = "attempt-2",
                response = LearnerResponse.Text("world"),
                occurredAt = later,
                    )
                .session
        session =
            session
                .submit(
                    attemptId = "attempt-3",
                response = LearnerResponse.Text("goodbye"),
                occurredAt = later,
                    )
                .session

        assertEquals(LessonSessionState.COMPLETED, session.state)
        assertNull(session.nextExercise)
    }

    @Test
    fun completedSessionCannotAcceptAnotherSubmission() {
        var session = LessonSession.start(plan)

        session =
            session
                .submit(
                    "attempt-1",
                LearnerResponse.Text("hello"),
                occurredAt,
                    )
                .session
        session =
            session
                .submit(
                    "attempt-2",
                LearnerResponse.Text("world"),
                later,
                    )
                .session
        session =
            session
                .submit(
                    "attempt-3",
                LearnerResponse.Text("goodbye"),
                later,
                    )
                .session

        assertFailsWith<LessonSessionCompletedException> {
            session.submit("attempt-4", LearnerResponse.Text("hello"), later)
        }
    }

    private fun manualExercise(
        id: String,
        answer: String,
    ): ManualInputExercise =
        ManualInputExercise(
            id = id,
            contentId = lessonId,
            prompt = "Translate",
            expectedAnswers = listOf(answer),
        )
}
