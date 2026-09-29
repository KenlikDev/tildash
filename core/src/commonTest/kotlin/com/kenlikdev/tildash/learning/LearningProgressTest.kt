package com.kenlikdev.tildash.learning

import com.kenlikdev.tildash.content.model.ContentId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Instant

class LearningProgressTest {
    private val occurredAt = Instant.parse("2026-09-28T08:00:00Z")
    private val later = Instant.parse("2026-09-29T08:00:00Z")
    private val lessonId = ContentId("550e8400-e29b-41d4-a716-446655440000")
    private val firstExercise = manualExercise("exercise-1", expectedAnswers = listOf("Hello"))
    private val secondExercise = manualExercise("exercise-2", expectedAnswers = listOf("World"))
    private val plan =
        LearningPlan(
            lessonId = lessonId,
            exercises = listOf(firstExercise, secondExercise),
        )

    @Test
    fun exactNormalizedAnswerIsCorrect() {
        val evaluation =
            AnswerEvaluator.evaluate(
                firstExercise,
                LearnerResponse.Text("  hello "),
            )

        assertEquals(AnswerOutcome.CORRECT, evaluation.outcome)
    }

    @Test
    fun unmatchedAnswerIsIncorrect() {
        val evaluation =
            AnswerEvaluator.evaluate(
                firstExercise,
                LearnerResponse.Text("goodbye"),
            )

        assertEquals(AnswerOutcome.INCORRECT, evaluation.outcome)
    }

    @Test
    fun answerNormalizationIsCaseAndWhitespaceInsensitive() {
        val exercise =
            manualExercise(
                id = "exercise-1",
                expectedAnswers = listOf("Hello", "World"),
            )

        val evaluation =
            AnswerEvaluator.evaluate(
                exercise,
                LearnerResponse.Text("  hELLo "),
            )

        assertEquals(AnswerOutcome.CORRECT, evaluation.outcome)
    }

    @Test
    fun firstCorrectAttemptSchedulesNextReviewAfterOneDay() {
        val result =
            LearningEngine.submit(
                progress = LearningProgress.empty(),
                exercise = firstExercise,
                attemptId = "attempt-1",
                response = LearnerResponse.Text("hello"),
                occurredAt = occurredAt,
            )

        assertEquals(AnswerOutcome.CORRECT, result.evaluation.outcome)
        assertEquals(
            occurredAt.plusDays(1),
            result.progress.reviewState(firstExercise.id).dueAt,
        )
        assertEquals(1, result.progress.reviewState(firstExercise.id).stage)
    }

    @Test
    fun incorrectAttemptIsDueImmediatelyAndRecordedAsMistake() {
        val result =
            LearningEngine.submit(
                progress = LearningProgress.empty(),
                exercise = firstExercise,
                attemptId = "attempt-1",
                response = LearnerResponse.Text("wrong"),
                occurredAt = occurredAt,
            )

        val review = result.progress.reviewState(firstExercise.id)

        assertEquals(AnswerOutcome.INCORRECT, result.evaluation.outcome)
        assertEquals(0, review.stage)
        assertEquals(occurredAt, review.dueAt)
        assertEquals(1, result.progress.mistakeCount(firstExercise.id))
    }

    @Test
    fun laterCorrectAttemptPreservesPreviousMistakeHistory() {
        val first =
            LearningEngine.submit(
                progress = LearningProgress.empty(),
                exercise = firstExercise,
                attemptId = "attempt-1",
                response = LearnerResponse.Text("wrong"),
                occurredAt = occurredAt,
            )
        val second =
            LearningEngine.submit(
                progress = first.progress,
                exercise = firstExercise,
                attemptId = "attempt-2",
                response = LearnerResponse.Text("hello"),
                occurredAt = later,
            )

        assertEquals(1, second.progress.mistakeCount(firstExercise.id))
        assertEquals(AnswerOutcome.CORRECT, second.evaluation.outcome)
        assertEquals(1, second.progress.reviewState(firstExercise.id).stage)
    }

    @Test
    fun lessonCompletesOnlyAfterEveryRequiredExerciseHasCorrectAttempt() {
        val first =
            LearningEngine.submit(
                progress = LearningProgress.empty(),
                exercise = firstExercise,
                attemptId = "attempt-1",
                response = LearnerResponse.Text("hello"),
                occurredAt = occurredAt,
            )

        assertFalse(first.progress.isLessonComplete(plan))

        val second =
            LearningEngine.submit(
                progress = first.progress,
                exercise = secondExercise,
                attemptId = "attempt-2",
                response = LearnerResponse.Text("world"),
                occurredAt = later,
            )

        assertTrue(second.progress.isLessonComplete(plan))
        assertEquals(later, second.progress.lessonCompletedAt(plan))
    }

    @Test
    fun repeatedAttemptIdIsIdempotent() {
        val first =
            LearningEngine.submit(
                progress = LearningProgress.empty(),
                exercise = firstExercise,
                attemptId = "attempt-1",
                response = LearnerResponse.Text("hello"),
                occurredAt = occurredAt,
            )
        val repeated =
            LearningEngine.submit(
                progress = first.progress,
                exercise = firstExercise,
                attemptId = "attempt-1",
                response = LearnerResponse.Text("hello"),
                occurredAt = occurredAt,
            )

        assertEquals(first.progress, repeated.progress)
        assertEquals(first.progress.attempts, repeated.progress.attempts)
    }

    @Test
    fun sameExerciseIdInDifferentLessonsDoesNotTransferCompletion() {
        val firstLesson = ContentId("550e8400-e29b-41d4-a716-446655440000")
        val secondLesson = ContentId("550e8400-e29b-41d4-a716-446655440001")
        val firstExercise =
            ManualInputExercise(
                id = "exercise-shared",
                contentId = firstLesson,
                prompt = "First lesson",
                expectedAnswers = listOf("hello"),
            )
        val secondExercise =
            ManualInputExercise(
                id = "exercise-shared",
                contentId = secondLesson,
                prompt = "Second lesson",
                expectedAnswers = listOf("hello"),
            )

        val firstProgress =
            LearningEngine
                .submit(
                progress = LearningProgress.empty(),
                lessonId = firstLesson,
                exercise = firstExercise,
                attemptId = "attempt-first-lesson",
                response = LearnerResponse.Text("hello"),
                occurredAt = occurredAt,
            ).progress

        val secondPlan = LearningPlan(secondLesson, listOf(secondExercise))

        assertFalse(firstProgress.isLessonComplete(secondPlan))
        assertEquals(
            firstExercise.id,
            secondPlan.exercises.first().id,
        )
        assertEquals(0, firstProgress.mistakeCount(firstExercise.id, secondLesson))
    }

    @Test
    fun reusedAttemptIdWithDifferentPayloadIsRejected() {
        val first =
            LearningEngine.submit(
                progress = LearningProgress.empty(),
                exercise = firstExercise,
                attemptId = "attempt-1",
                response = LearnerResponse.Text("hello"),
                occurredAt = occurredAt,
            )

        assertFailsWith<AttemptIdConflict> {
            LearningEngine.submit(
                progress = first.progress,
                exercise = firstExercise,
                attemptId = "attempt-1",
                response = LearnerResponse.Text("wrong"),
                occurredAt = occurredAt,
            )
        }
    }

    @Test
    fun attemptsAreCanonicallyOrderedByTimeThenId() {
        val laterFirst =
            LearningEngine.submit(
                progress = LearningProgress.empty(),
                exercise = firstExercise,
                attemptId = "b",
                response = LearnerResponse.Text("wrong"),
                occurredAt = later,
            )
        val merged =
            LearningEngine.submit(
                progress = laterFirst.progress,
                exercise = firstExercise,
                attemptId = "a",
                response = LearnerResponse.Text("hello"),
                occurredAt = occurredAt,
            )

        assertEquals(listOf("a", "b"), merged.progress.attempts.map { it.attemptId })
    }

    @Test
    fun sameTimestampUsesAttemptIdAsDeterministicTieBreaker() {
        val first =
            LearningEngine.submit(
                progress = LearningProgress.empty(),
                exercise = firstExercise,
                attemptId = "z",
                response = LearnerResponse.Text("hello"),
                occurredAt = occurredAt,
            )
        val second =
            LearningEngine.submit(
                progress = first.progress,
                exercise = secondExercise,
                attemptId = "a",
                response = LearnerResponse.Text("world"),
                occurredAt = occurredAt,
            )

        assertEquals(listOf("a", "z"), second.progress.attempts.map { it.attemptId })
    }

    @Test
    fun reviewSchedulerIsDeterministicForRepeatedSequence() {
        var state = ReviewState.initial(occurredAt)

        repeat(4) {
            state = ReviewScheduler.schedule(state, AnswerOutcome.CORRECT, occurredAt)
        }

        assertEquals(4, state.stage)
        assertEquals(occurredAt.plusDays(14), state.dueAt)
    }

    @Test
    fun persistedAttemptsAreRehydratedInCanonicalOrder() {
        val laterAttempt =
            LearningAttempt(
                attemptId = "b",
                exerciseId = firstExercise.id,
                response = LearnerResponse.Text("wrong"),
                outcome = AnswerOutcome.INCORRECT,
                occurredAt = later,
            )
        val earlierAttempt =
            LearningAttempt(
                attemptId = "a",
                exerciseId = firstExercise.id,
                response = LearnerResponse.Text("hello"),
                outcome = AnswerOutcome.CORRECT,
                occurredAt = occurredAt,
            )

        val progress =
            LearningProgress.fromPersistedAttempts(
                listOf(laterAttempt, earlierAttempt),
            )

        assertEquals(listOf("a", "b"), progress.attempts.map { it.attemptId })
    }

    @Test
    fun duplicatePersistedAttemptIdsAreRejected() {
        val attempt =
            LearningAttempt(
                attemptId = "attempt-1",
                exerciseId = firstExercise.id,
                response = LearnerResponse.Text("hello"),
                outcome = AnswerOutcome.CORRECT,
                occurredAt = occurredAt,
            )

        assertFailsWith<IllegalArgumentException> {
            LearningProgress.fromPersistedAttempts(listOf(attempt, attempt))
        }
    }

    @Test
    fun emptyPlanIsRejected() {
        assertFailsWith<IllegalArgumentException> {
            LearningPlan(lessonId, emptyList())
        }
    }

    private fun manualExercise(
        id: String,
        expectedAnswers: List<String>,
    ): ManualInputExercise =
        ManualInputExercise(
            id = id,
            contentId = lessonId,
            prompt = "Translate",
            expectedAnswers = expectedAnswers,
        )
}

private fun Instant.plusDays(days: Int): Instant =
    Instant.fromEpochMilliseconds(
        toEpochMilliseconds() + days * 24L * 60L * 60L * 1000L,
    )
