package com.kenlikdev.tildash.learning

import com.kenlikdev.tildash.content.model.ContentId
import kotlin.time.Instant

enum class LessonSessionState {
    ACTIVE,
    COMPLETED,
}

data class LessonSession(
    val plan: LearningPlan,
    val progress: LearningProgress,
) {
    val state: LessonSessionState
        get() =
            if (progress.isLessonComplete(plan)) {
                LessonSessionState.COMPLETED
            } else {
                LessonSessionState.ACTIVE
            }

    val nextExercise: LearningExercise?
        get() =
            plan.exercises.firstOrNull { exercise ->
                progress.attempts.none {
                    it.exerciseId == exercise.id && it.outcome == AnswerOutcome.CORRECT
                }
            }

    init {
        if (state == LessonSessionState.ACTIVE) {
            checkNotNull(nextExercise) {
                "An active lesson session must have a next exercise."
            }
        }
    }

    fun submit(
        attemptId: String,
        response: LearnerResponse,
        occurredAt: Instant,
    ): LessonSessionSubmission {
        val exercise =
            nextExercise
                ?: throw LessonSessionCompletedException(plan.lessonId)

        val submission =
            LearningEngine.submit(
                progress = progress,
                exercise = exercise,
                attemptId = attemptId,
                response = response,
                occurredAt = occurredAt,
            )

        return LessonSessionSubmission(
            evaluation = submission.evaluation,
            session = LessonSession(plan, submission.progress),
        )
    }

    companion object {
        fun start(
            plan: LearningPlan,
            progress: LearningProgress = LearningProgress.empty(),
        ): LessonSession = LessonSession(plan, progress)
    }
}

data class LessonSessionSubmission(
    val evaluation: AnswerEvaluation,
    val session: LessonSession,
)

class LessonSessionCompletedException(
    lessonId: ContentId,
) : IllegalStateException("Lesson ${lessonId.value} is already complete.")
