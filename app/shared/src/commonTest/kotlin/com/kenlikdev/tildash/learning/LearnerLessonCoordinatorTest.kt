package com.kenlikdev.tildash.learning

import com.kenlikdev.tildash.content.model.ContentId
import com.kenlikdev.tildash.content.model.LanguageTag
import com.kenlikdev.tildash.storage.DownloadedLesson
import com.kenlikdev.tildash.storage.DownloadedLessonStore
import com.kenlikdev.tildash.storage.LearningProgressStore
import com.kenlikdev.tildash.learning.client.LearnerLessonCoordinator
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

class LearnerLessonCoordinatorTest {
    private val lessonId = ContentId("550e8400-e29b-41d4-a716-446655440001")
    private val secondContentId = ContentId("550e8400-e29b-41d4-a716-446655440002")
    private val downloadedAt = Instant.parse("2026-09-29T08:00:00Z")
    private val firstAttemptAt = Instant.parse("2026-09-29T08:01:00Z")
    private val secondAttemptAt = Instant.parse("2026-09-29T08:02:00Z")
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
                            ManualInputExercise(
                                id = "exercise-2",
                                contentId = secondContentId,
                                prompt = "Translate: sağ ol",
                                expectedAnswers = listOf("thank you"),
                            ),
                        ),
                ),
            downloadedAt = downloadedAt,
        )

    @Test
    fun openLoadsDownloadedLessonAndPersistedProgress() {
        val storedProgress =
            LearningProgress.fromPersistedAttempts(
                listOf(
                    LearningAttempt(
                        attemptId = "existing",
                        exerciseId = "exercise-1",
                        response = LearnerResponse.Text("hello"),
                        outcome = AnswerOutcome.CORRECT,
                        occurredAt = firstAttemptAt,
                    ),
                ),
            )
        val progressStore = FakeLearningProgressStore(storedProgress)
        val coordinator =
            LearnerLessonCoordinator(
                downloadedLessonStore = FakeDownloadedLessonStore(lesson),
                learningProgressStore = progressStore,
            )

        val state = coordinator.open(lessonId)

        assertNotNull(state)
        assertEquals(lessonId, state.lesson.lesson.id)
        assertEquals("exercise-2", state.session.nextExercise?.id)
        assertEquals(LessonSessionState.ACTIVE, state.session.state)
        assertEquals(1, progressStore.saveCount)
    }

    @Test
    fun correctSubmissionPersistsAttemptAndAdvancesSession() {
        val progressStore = FakeLearningProgressStore()
        val coordinator =
            LearnerLessonCoordinator(
                downloadedLessonStore = FakeDownloadedLessonStore(lesson),
                learningProgressStore = progressStore,
            )
        var state = checkNotNull(coordinator.open(lessonId))

        state =
            coordinator.submitText(
                state = state,
                attemptId = "attempt-1",
                value = "hello",
                occurredAt = firstAttemptAt,
            )

        assertEquals(AnswerOutcome.CORRECT, state.lastEvaluation?.outcome)
        assertEquals("exercise-2", state.session.nextExercise?.id)
        assertEquals(1, progressStore.savedAttempts.size)
        assertEquals("attempt-1", progressStore.savedAttempts.single().attemptId)
        assertEquals(1, progressStore.pendingSyncCount())
    }

    @Test
    fun incorrectSubmissionPersistsAttemptAndKeepsCurrentExercise() {
        val progressStore = FakeLearningProgressStore()
        val coordinator =
            LearnerLessonCoordinator(
                downloadedLessonStore = FakeDownloadedLessonStore(lesson),
                learningProgressStore = progressStore,
            )
        var state = checkNotNull(coordinator.open(lessonId))

        state =
            coordinator.submitText(
                state = state,
                attemptId = "attempt-1",
                value = "goodbye",
                occurredAt = firstAttemptAt,
            )

        assertEquals(AnswerOutcome.INCORRECT, state.lastEvaluation?.outcome)
        assertEquals("exercise-1", state.session.nextExercise?.id)
        assertEquals(1, progressStore.savedAttempts.size)
        assertEquals(1, progressStore.pendingSyncCount())
    }

    @Test
    fun completedSessionPersistsEveryAttemptAndCanBeResumed() {
        val progressStore = FakeLearningProgressStore()
        val coordinator =
            LearnerLessonCoordinator(
                downloadedLessonStore = FakeDownloadedLessonStore(lesson),
                learningProgressStore = progressStore,
            )
        var state = checkNotNull(coordinator.open(lessonId))

        state =
            coordinator.submitText(
                state = state,
                attemptId = "attempt-1",
                value = "hello",
                occurredAt = firstAttemptAt,
            )
        state =
            coordinator.submitText(
                state = state,
                attemptId = "attempt-2",
                value = "thank you",
                occurredAt = secondAttemptAt,
            )

        assertEquals(LessonSessionState.COMPLETED, state.session.state)
        assertNull(state.session.nextExercise)
        assertEquals(2, progressStore.savedAttempts.size)

        val resumed = checkNotNull(coordinator.open(lessonId))

        assertEquals(LessonSessionState.COMPLETED, resumed.session.state)
        assertNull(resumed.session.nextExercise)
        assertTrue(resumed.session.progress.isLessonComplete(lesson.plan))
    }

    private class FakeDownloadedLessonStore(
        private val lesson: DownloadedLesson,
    ) : DownloadedLessonStore {
        override fun save(downloadedLesson: DownloadedLesson) {
            error("Not used in this test.")
        }

        override fun loadLesson(lessonId: ContentId): DownloadedLesson? =
            lesson.takeIf { it.lesson.id == lessonId }

        override fun listLessons(): List<DownloadedLesson> = listOf(lesson)

        override fun deleteLesson(lessonId: ContentId) {
            error("Not used in this test.")
        }

        override fun countLessons(): Long = 1
    }

    private class FakeLearningProgressStore(
        private var progress: LearningProgress = LearningProgress.empty(),
    ) : LearningProgressStore {
        val savedAttempts = mutableListOf<LearningAttempt>()
        var saveCount: Int = 0
            private set

        override fun loadProgress(): LearningProgress = progress

        override fun saveAttempt(attempt: LearningAttempt) {
            saveCount += 1
            savedAttempts += attempt
            progress =
                LearningProgress.fromPersistedAttempts(
                    progress.attempts + attempt,
                )
        }

        override fun loadPendingSyncAttempts(): List<LearningAttempt> = savedAttempts.toList()

        override fun acknowledgeAttempt(attemptId: String) {
            savedAttempts.removeAll { it.attemptId == attemptId }
        }

        override fun pendingSyncCount(): Long = savedAttempts.size.toLong()
    }
}
