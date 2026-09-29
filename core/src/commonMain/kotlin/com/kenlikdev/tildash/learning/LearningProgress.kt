package com.kenlikdev.tildash.learning

import com.kenlikdev.tildash.content.model.ContentId
import kotlin.time.Instant

interface LearningExercise {
    val id: String
    val contentId: ContentId
    val prompt: String
}

data class ManualInputExercise(
    override val id: String,
    override val contentId: ContentId,
    override val prompt: String,
    val expectedAnswers: List<String>,
) : LearningExercise {
    init {
        require(id.isNotBlank()) {
            "Exercise ID must not be blank."
        }
        require(prompt.isNotBlank()) {
            "Exercise prompt must not be blank."
        }
        require(expectedAnswers.isNotEmpty()) {
            "Exercise must define at least one expected answer."
        }
        require(expectedAnswers.map(::normalize).distinct().size == expectedAnswers.size) {
            "Exercise expected answers must be unique after normalization."
        }
    }

    private companion object {
        fun normalize(value: String): String = value.trim().lowercase()
    }
}

data class LearningPlan(
    val lessonId: ContentId,
    val exercises: List<LearningExercise>,
) {
    init {
        require(exercises.isNotEmpty()) {
            "Learning plan must contain at least one exercise."
        }
        require(exercises.map { it.id }.distinct().size == exercises.size) {
            "Learning plan exercise IDs must be unique."
        }
        require(exercises.all { it.prompt.isNotBlank() }) {
            "Learning plan exercises must have non-blank prompts."
        }
    }
}

sealed interface LearnerResponse {
    data class Text(
        val value: String,
    ) : LearnerResponse
}

enum class AnswerOutcome {
    CORRECT,
    INCORRECT,
}

data class AnswerEvaluation(
    val outcome: AnswerOutcome,
)

interface ExerciseEvaluator {
    fun supports(exercise: LearningExercise): Boolean

    fun evaluate(
        exercise: LearningExercise,
        response: LearnerResponse,
    ): AnswerEvaluation
}

object AnswerEvaluator {
    private val evaluators: List<ExerciseEvaluator> =
        listOf(ManualInputExerciseEvaluator)

    fun evaluate(
        exercise: LearningExercise,
        response: LearnerResponse,
    ): AnswerEvaluation =
        evaluators
            .firstOrNull { it.supports(exercise) }
            ?.evaluate(exercise, response)
            ?: throw UnsupportedLearningExerciseException(exercise.id)
}

private object ManualInputExerciseEvaluator : ExerciseEvaluator {
    override fun supports(exercise: LearningExercise): Boolean = exercise is ManualInputExercise

    override fun evaluate(
        exercise: LearningExercise,
        response: LearnerResponse,
    ): AnswerEvaluation {
        val manualExercise = exercise as ManualInputExercise
        val answer =
            (response as? LearnerResponse.Text)?.value
                ?: return AnswerEvaluation(AnswerOutcome.INCORRECT)

        val normalizedAnswer = normalize(answer)
        val correct =
            manualExercise.expectedAnswers.any { normalize(it) == normalizedAnswer }

        return AnswerEvaluation(
            outcome =
                if (correct) {
                    AnswerOutcome.CORRECT
                } else {
                    AnswerOutcome.INCORRECT
                },
        )
    }

    private fun normalize(value: String): String = value.trim().lowercase()
}

class UnsupportedLearningExerciseException(
    exerciseId: String,
) : IllegalArgumentException("No evaluator is registered for exercise $exerciseId.")

class AttemptIdConflict(
    attemptId: String,
) : IllegalArgumentException("Attempt $attemptId already exists with different data.")

data class LearningAttempt(
    val attemptId: String,
    val exerciseId: String,
    val lessonId: ContentId? = null,
    val response: LearnerResponse,
    val outcome: AnswerOutcome,
    val occurredAt: Instant,
) {
    init {
        require(attemptId.isNotBlank()) {
            "Attempt ID must not be blank."
        }
        require(exerciseId.isNotBlank()) {
            "Exercise ID must not be blank."
        }
    }
}

data class ReviewState(
    val stage: Int,
    val dueAt: Instant,
) {
    init {
        require(stage in 0..ReviewScheduler.MAX_STAGE) {
            "Review stage must be between 0 and ${ReviewScheduler.MAX_STAGE}."
        }
    }

    companion object {
        fun initial(at: Instant): ReviewState = ReviewState(stage = 0, dueAt = at)
    }
}

object ReviewScheduler {
    const val MAX_STAGE: Int = 5

    private const val MILLIS_PER_DAY = 86_400_000L
    private val INTERVAL_DAYS = intArrayOf(0, 1, 3, 7, 14, 30)

    fun schedule(
        current: ReviewState,
        outcome: AnswerOutcome,
        occurredAt: Instant,
    ): ReviewState =
        when (outcome) {
            AnswerOutcome.INCORRECT -> {
                ReviewState(
                    stage = 0,
                    dueAt = occurredAt,
                )
            }

            AnswerOutcome.CORRECT -> {
                val nextStage = (current.stage + 1).coerceAtMost(MAX_STAGE)
                ReviewState(
                    stage = nextStage,
                    dueAt = addDays(occurredAt, INTERVAL_DAYS[nextStage]),
                )
            }
        }

    private fun addDays(
        instant: Instant,
        days: Int,
    ): Instant =
        Instant.fromEpochMilliseconds(
            instant.toEpochMilliseconds() + days * MILLIS_PER_DAY,
        )
}

data class LearningProgress private constructor(
    val attempts: List<LearningAttempt>,
) {
    companion object {
        private val attemptComparator =
            compareBy<LearningAttempt> { it.occurredAt }
                .thenBy { it.attemptId }

        fun empty(): LearningProgress = LearningProgress(emptyList())

        fun fromPersistedAttempts(attempts: List<LearningAttempt>): LearningProgress {
            require(attempts.map { it.attemptId }.distinct().size == attempts.size) {
                "Persisted learning attempts must have unique attempt IDs."
            }
            return LearningProgress(attempts.sortedWith(attemptComparator))
        }

        internal fun fromCanonicalAttempts(attempts: List<LearningAttempt>): LearningProgress = LearningProgress(attempts)
    }

    fun reviewState(
        exerciseId: String,
        lessonId: ContentId? = null,
    ): ReviewState {
        val exerciseAttempts =
            attempts
                .filter {
                    it.exerciseId == exerciseId &&
                        (lessonId == null || it.lessonId == lessonId)
                }
                .sortedWith(attemptComparator)

        require(exerciseAttempts.isNotEmpty()) {
            "Exercise $exerciseId has no attempts."
        }

        var state = ReviewState.initial(exerciseAttempts.first().occurredAt)
        exerciseAttempts.forEach { attempt ->
            state = ReviewScheduler.schedule(state, attempt.outcome, attempt.occurredAt)
        }
        return state
    }

    fun mistakeCount(
        exerciseId: String,
        lessonId: ContentId? = null,
    ): Int =
        attempts.count {
            it.exerciseId == exerciseId &&
                (lessonId == null || it.lessonId == lessonId) &&
                it.outcome == AnswerOutcome.INCORRECT
        }

    fun isLessonComplete(plan: LearningPlan): Boolean =
        plan.exercises.all { exercise ->
            attempts.any {
                it.lessonId == plan.lessonId &&
                    it.exerciseId == exercise.id &&
                    it.outcome == AnswerOutcome.CORRECT
            }
        }

    fun lessonCompletedAt(plan: LearningPlan): Instant? {
        if (!isLessonComplete(plan)) return null

        val requiredIds = plan.exercises.mapTo(mutableSetOf()) { it.id }
        val completedExercises = mutableSetOf<String>()

        for (attempt in attempts.sortedWith(attemptComparator)) {
            if (
                attempt.lessonId == plan.lessonId &&
                attempt.outcome == AnswerOutcome.CORRECT &&
                attempt.exerciseId in requiredIds
            ) {
                completedExercises += attempt.exerciseId
                if (completedExercises.size == requiredIds.size) {
                    return attempt.occurredAt
                }
            }
        }

        return null
    }
}

data class LearningSubmission(
    val evaluation: AnswerEvaluation,
    val progress: LearningProgress,
)

object LearningEngine {
    fun submit(
        progress: LearningProgress,
        exercise: LearningExercise,
        attemptId: String,
        response: LearnerResponse,
        occurredAt: Instant,
    ): LearningSubmission =
        submit(
            progress = progress,
            exercise = exercise,
            lessonId = null,
            attemptId = attemptId,
            response = response,
            occurredAt = occurredAt,
        )

    fun submit(
        progress: LearningProgress,
        exercise: LearningExercise,
        lessonId: ContentId?,
        attemptId: String,
        attemptId: String,
        response: LearnerResponse,
        occurredAt: Instant,
    ): LearningSubmission {
        require(attemptId.isNotBlank()) {
            "Attempt ID must not be blank."
        }

        val existing = progress.attempts.firstOrNull { it.attemptId == attemptId }
        if (existing != null) {
            if (
                existing.lessonId != lessonId ||
                existing.exerciseId != exercise.id ||
                existing.response != response ||
                existing.occurredAt != occurredAt
            ) {
                throw AttemptIdConflict(attemptId)
            }

            return LearningSubmission(
                evaluation = AnswerEvaluation(existing.outcome),
                progress = progress,
            )
        }

        val evaluation = AnswerEvaluator.evaluate(exercise, response)
        val attempt =
            LearningAttempt(
                attemptId = attemptId,
                exerciseId = exercise.id,
                lessonId = lessonId,
                response = response,
                outcome = evaluation.outcome,
                occurredAt = occurredAt,
            )

        val canonicalAttempts =
            (progress.attempts + attempt)
                .sortedWith(
                    compareBy<LearningAttempt> { it.occurredAt }
                        .thenBy { it.attemptId },
                )

        return LearningSubmission(
            evaluation = evaluation,
            progress = LearningProgress.fromCanonicalAttempts(canonicalAttempts),
        )
    }
}
