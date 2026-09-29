package com.kenlikdev.tildash.learning.client

import kotlin.time.Instant
import com.kenlikdev.tildash.content.model.ContentId
import com.kenlikdev.tildash.learning.AnswerEvaluation
import com.kenlikdev.tildash.learning.LearnerResponse
import com.kenlikdev.tildash.learning.LessonSession
import com.kenlikdev.tildash.learning.LessonSessionState
import com.kenlikdev.tildash.learning.LearningAttempt
import com.kenlikdev.tildash.storage.DownloadedLesson
import com.kenlikdev.tildash.storage.DownloadedLessonStore
import com.kenlikdev.tildash.storage.LearningProgressStore

data class LearnerLessonState(
    val lesson: DownloadedLesson,
    val session: LessonSession,
    val lastEvaluation: AnswerEvaluation? = null,
) {
    val isCompleted: Boolean
        get() = session.state == LessonSessionState.COMPLETED
}

class LearnerLessonCoordinator(
    private val downloadedLessonStore: DownloadedLessonStore,
    private val learningProgressStore: LearningProgressStore,
) {
    fun open(lessonId: ContentId): LearnerLessonState? {
        val downloadedLesson = downloadedLessonStore.loadLesson(lessonId) ?: return null
        val progress = learningProgressStore.loadProgress()

        return LearnerLessonState(
            lesson = downloadedLesson,
            session = LessonSession.start(downloadedLesson.plan, progress),
        )
    }

    fun submitText(
        state: LearnerLessonState,
        attemptId: String,
        value: String,
        occurredAt: Instant,
    ): LearnerLessonState {
        val exercise =
            state.session.nextExercise
                ?: throw IllegalStateException(
                    "Lesson ${state.lesson.lesson.id.value} is already complete.",
                )

        val response = LearnerResponse.Text(value)
        val submission =
            state.session.submit(
                attemptId = attemptId,
                response = response,
                occurredAt = occurredAt,
            )

        learningProgressStore.saveAttempt(
            LearningAttempt(
                attemptId = attemptId,
                exerciseId = exercise.id,
                response = response,
                outcome = submission.evaluation.outcome,
                occurredAt = occurredAt,
            ),
        )

        return state.copy(
            session = submission.session,
            lastEvaluation = submission.evaluation,
        )
    }
}
