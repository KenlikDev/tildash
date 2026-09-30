package com.kenlikdev.tildash.learning

import com.kenlikdev.tildash.content.model.ContentId
import com.kenlikdev.tildash.content.model.LanguageTag
import com.kenlikdev.tildash.learning.client.LearnerLessonApplication
import com.kenlikdev.tildash.learning.client.LearnerLessonCoordinator
import com.kenlikdev.tildash.storage.DownloadedLesson
import com.kenlikdev.tildash.storage.DownloadedLessonStore
import com.kenlikdev.tildash.storage.LearningProgressStore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Clock
import kotlin.time.Instant

class LearnerLessonApplicationTest {
    private val lessonId = ContentId("550e8400-e29b-41d4-a716-446655440001")
    private val attemptAt = Instant.parse("2026-09-30T05:50:00Z")
    private val lesson =
        DownloadedLesson(
            course =
                LearnerCourseSummary(
                    id = ContentId("550e8400-e29b-41d4-a716-446655440000"),
                    title = "Crimean Tatar basics",
                    sourceLocale = LanguageTag("crh"),
                    publishedVersion = 1,
                    lessons =
                        listOf(
                            LearnerLessonSummary(
                                id = lessonId,
                                title = "Greetings",
                                sourceLocale = LanguageTag("crh"),
                                publishedVersion = 1,
                                localizations = emptyList(),
                            ),
                        ),
                ),
            lesson =
                LearnerLessonSummary(
                    id = lessonId,
                    title = "Greetings",
                    sourceLocale = LanguageTag("crh"),
                    publishedVersion = 1,
                    localizations = emptyList(),
                ),
            plan =
                LearningPlan(
                    lessonId = lessonId,
                    exercises =
                        listOf(
                            ManualInputExercise(
                                id = "exercise-1",
                                contentId = lessonId,
                                prompt = "Translate: merhaba",
                                expectedAnswers = listOf("hello"),
                            ),
                        ),
                ),
            downloadedAt = attemptAt,
        )

    @Test
    fun listsDownloadedLessonsAndOpensSelectedLesson() {
        val store = FakeDownloadedLessonStore(lesson)
        val application =
            LearnerLessonApplication(
                downloadedLessonStore = store,
                coordinator =
                    LearnerLessonCoordinator(
                        downloadedLessonStore = store,
                        learningProgressStore = FakeLearningProgressStore(),
                    ),
            )

        assertEquals(listOf(lesson), application.listDownloadedLessons())
        assertEquals(
            lessonId,
            application
                .openLesson(lessonId)
                ?.lesson
                ?.lesson
                ?.id,
        )
    }

    @Test
    fun submitTextGeneratesAttemptMetadataAndPersistsThroughCoordinator() {
        val progressStore = FakeLearningProgressStore()
        val downloadedStore = FakeDownloadedLessonStore(lesson)
        val application =
            LearnerLessonApplication(
                downloadedLessonStore = downloadedStore,
                coordinator =
                    LearnerLessonCoordinator(
                        downloadedLessonStore = downloadedStore,
                        learningProgressStore = progressStore,
                    ),
                clock = FixedClock(attemptAt),
                attemptIdGenerator = { "generated-attempt" },
            )
        val initial = checkNotNull(application.openLesson(lessonId))

        val updated = application.submitText(initial, "hello")
        val savedAttempt = progressStore.savedAttempts.single()

        assertEquals(LessonSessionState.COMPLETED, updated.session.state)
        assertEquals("generated-attempt", savedAttempt.attemptId)
        assertEquals(lessonId, savedAttempt.lessonId)
        assertEquals(attemptAt, savedAttempt.occurredAt)
        assertEquals(AnswerOutcome.CORRECT, savedAttempt.outcome)
    }

    private class FixedClock(
        private val instant: Instant,
    ) : Clock {
        override fun now(): Instant = instant
    }

    private class FakeDownloadedLessonStore(
        private val lesson: DownloadedLesson,
    ) : DownloadedLessonStore {
        override fun save(downloadedLesson: DownloadedLesson): Unit =
            Unit

        override fun loadLesson(lessonId: ContentId): DownloadedLesson? =
            lesson.takeIf { it.lesson.id == lessonId }

        override fun listLessons(): List<DownloadedLesson> =
            listOf(lesson)

        override fun deleteLesson(lessonId: ContentId): Unit =
            Unit

        override fun countLessons(): Long =
            1
    }

    private class FakeLearningProgressStore(
        private var progress: LearningProgress = LearningProgress.empty(),
    ) : LearningProgressStore {
        val savedAttempts = mutableListOf<LearningAttempt>()

        override fun loadProgress(): LearningProgress = progress

        override fun saveAttempt(attempt: LearningAttempt) {
            savedAttempts += attempt
            progress =
                LearningProgress.fromPersistedAttempts(
                    progress.attempts + attempt,
                )
        }

        override fun loadPendingSyncAttempts(): List<LearningAttempt> =
            savedAttempts.toList()

        override fun acknowledgeAttempt(attemptId: String) {
            savedAttempts.removeAll { it.attemptId == attemptId }
        }

        override fun pendingSyncCount(): Long =
            savedAttempts.size.toLong()
    }
}
