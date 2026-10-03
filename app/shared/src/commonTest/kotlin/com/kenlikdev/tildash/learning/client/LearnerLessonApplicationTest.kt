package com.kenlikdev.tildash.learning.client

import com.kenlikdev.tildash.content.model.ContentId
import com.kenlikdev.tildash.content.model.LanguageTag
import com.kenlikdev.tildash.learning.AnswerOutcome
import com.kenlikdev.tildash.learning.LearnerCourseSummary
import com.kenlikdev.tildash.learning.LearnerLessonSummary
import com.kenlikdev.tildash.learning.LearningAttempt
import com.kenlikdev.tildash.learning.LearningPlan
import com.kenlikdev.tildash.learning.ManualInputExercise
import com.kenlikdev.tildash.storage.DownloadedLesson
import com.kenlikdev.tildash.storage.DownloadedLessonStore
import com.kenlikdev.tildash.storage.LearningProgressStore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Instant

class LearnerLessonApplicationTest {
    private val courseId = ContentId("550e8400-e29b-41d4-a716-446655440001")
    private val lessonId = ContentId("550e8400-e29b-41d4-a716-446655440002")
    private val exerciseId = "exercise-1"

    @Test
    fun answeringAndReopeningLessonRestoresDurableProgress() {
        val downloadedLessonStore = FakeDownloadedLessonStore(createDownloadedLesson())
        val progressStore = FakeLearningProgressStore()
        val attemptIds = generateSequence(1) { it + 1 }.iterator()
        val application =
            createApplication(
                downloadedLessonStore = downloadedLessonStore,
                progressStore = progressStore,
                attemptIdGenerator = { "attempt-" + attemptIds.next() },
            )

        val opened = assertNotNull(application.openLesson(lessonId))

        val afterIncorrect = application.submitText(opened, "wrong")
        assertEquals(AnswerOutcome.INCORRECT, afterIncorrect.lastEvaluation?.outcome)
        assertEquals(exerciseId, afterIncorrect.session.nextExercise?.id)
        assertEquals(1, progressStore.attempts.size)

        val afterCorrect = application.submitText(afterIncorrect, "merhaba")
        assertEquals(AnswerOutcome.CORRECT, afterCorrect.lastEvaluation?.outcome)
        assertTrue(afterCorrect.isCompleted)
        assertEquals(2, progressStore.attempts.size)

        val reopened = assertNotNull(application.openLesson(lessonId))
        assertTrue(reopened.isCompleted)
        assertEquals(2, reopened.session.progress.attempts.size)
        assertEquals(
            listOf(AnswerOutcome.INCORRECT, AnswerOutcome.CORRECT),
            reopened
                .session
                .progress
                .attempts
                .map { it.outcome },
        )
    }

    private fun createApplication(
        downloadedLessonStore: DownloadedLessonStore,
        progressStore: LearningProgressStore,
        attemptIdGenerator: () -> String,
    ) = LearnerLessonApplication(
        downloadedLessonStore = downloadedLessonStore,
        coordinator =
            LearnerLessonCoordinator(
                downloadedLessonStore = downloadedLessonStore,
                learningProgressStore = progressStore,
            ),
        clock =
            object : Clock {
                override fun now(): Instant = Instant.parse("2026-10-01T08:00:00Z")
            },
        attemptIdGenerator = attemptIdGenerator,
    )

    private fun createDownloadedLesson(): DownloadedLesson {
        val lesson =
            LearnerLessonSummary(
                id = lessonId,
                title = "Greetings",
                sourceLocale = LanguageTag("crh"),
                publishedVersion = 1,
                localizations = emptyList(),
            )
        val course =
            LearnerCourseSummary(
                id = courseId,
                title = "Crimean Tatar basics",
                sourceLocale = LanguageTag("crh"),
                publishedVersion = 1,
                lessons = listOf(lesson),
            )
        val exercise =
            ManualInputExercise(
                id = exerciseId,
                contentId = lessonId,
                prompt = "Translate hello.",
                expectedAnswers = listOf("merhaba"),
            )

        return DownloadedLesson(
            course = course,
            lesson = lesson,
            plan =
                LearningPlan(
                    lessonId = lessonId,
                    exercises = listOf(exercise),
                ),
            downloadedAt = Instant.parse("2026-10-01T07:00:00Z"),
        )
    }

    private class FakeDownloadedLessonStore(
        initialLesson: DownloadedLesson,
    ) : DownloadedLessonStore {
        private val lessons = linkedMapOf(initialLesson.lesson.id to initialLesson)

        override fun save(downloadedLesson: DownloadedLesson) {
            lessons[downloadedLesson.lesson.id] = downloadedLesson
        }

        override fun loadLesson(lessonId: ContentId): DownloadedLesson? = lessons[lessonId]

        override fun listLessons(): List<DownloadedLesson> = lessons.values.toList()

        override fun deleteLesson(lessonId: ContentId) {
            lessons.remove(lessonId)
        }

        override fun countLessons(): Long = lessons.size.toLong()
    }

    private class FakeLearningProgressStore : LearningProgressStore {
        val attempts = mutableListOf<LearningAttempt>()

        override fun loadProgress() = com.kenlikdev.tildash.learning.LearningProgress.fromPersistedAttempts(attempts)

        override fun saveAttempt(attempt: LearningAttempt) {
            attempts += attempt
        }

        override fun loadPendingSyncAttempts(): List<LearningAttempt> = attempts.toList()

        override fun acknowledgeAttempt(attemptId: String) {
            error("Not needed for application acceptance test.")
        }

        override fun pendingSyncCount(): Long = attempts.size.toLong()
    }
}
