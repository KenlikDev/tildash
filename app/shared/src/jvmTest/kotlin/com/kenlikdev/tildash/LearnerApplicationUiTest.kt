package com.kenlikdev.tildash

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.onNode
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.runComposeUiTest
import com.kenlikdev.tildash.content.model.ContentId
import com.kenlikdev.tildash.content.model.LanguageTag
import com.kenlikdev.tildash.learning.LearnerCourseCatalog
import com.kenlikdev.tildash.learning.LearnerCourseSummary
import com.kenlikdev.tildash.learning.LearnerLessonPackage
import com.kenlikdev.tildash.learning.LearnerLessonSummary
import com.kenlikdev.tildash.learning.LearningProgress
import com.kenlikdev.tildash.learning.LearningAttempt
import com.kenlikdev.tildash.learning.LearningPlan
import com.kenlikdev.tildash.learning.ManualInputExercise
import com.kenlikdev.tildash.learning.client.LearnerContentApplication
import com.kenlikdev.tildash.learning.client.LearnerContentTransport
import com.kenlikdev.tildash.learning.client.LearnerLessonApplication
import com.kenlikdev.tildash.learning.client.LearnerLessonCoordinator
import com.kenlikdev.tildash.storage.DownloadedLesson
import com.kenlikdev.tildash.storage.DownloadedLessonStore
import com.kenlikdev.tildash.storage.LearningProgressStore
import kotlin.test.Test
import kotlin.time.Clock
import kotlin.time.Instant

class LearnerApplicationUiTest {
    private val courseId = ContentId("550e8400-e29b-41d4-a716-446655440001")
    private val lessonId = ContentId("550e8400-e29b-41d4-a716-446655440002")

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun learnerCanDownloadOpenAndCompleteLessonThroughUi() =
        runComposeUiTest {
            val downloadedLessonStore = FakeDownloadedLessonStore()
            val contentApplication =
                LearnerContentApplication(
                    transport = FakeContentTransport(),
                    downloadedLessonStore = downloadedLessonStore,
                    clock = fixedClock(),
                )
            val lessonApplication =
                LearnerLessonApplication(
                    downloadedLessonStore = downloadedLessonStore,
                    coordinator =
                        LearnerLessonCoordinator(
                            downloadedLessonStore = downloadedLessonStore,
                            learningProgressStore = FakeLearningProgressStore(),
                        ),
                    clock = fixedClock(),
                    attemptIdGenerator = { "attempt-1" },
                )

            setContent {
                App(
                    learnerApplication = lessonApplication,
                    contentApplication = contentApplication,
                )
            }

            onNodeWithText("Tildash Learning").assertIsDisplayed()
            waitForIdle()
            onNodeWithText("Greetings").assertIsDisplayed()

            onNodeWithText("Download").performClick()
            waitForIdle()
            onNodeWithText("Downloaded (1)").assertIsDisplayed()
            onNodeWithText("Open").performClick()
            waitForIdle()

            onNodeWithText("Translate hello.").assertIsDisplayed()
            onNode(hasSetTextAction()).performTextInput("wrong")
            onNodeWithText("Check answer").performClick()
            waitForIdle()
            onNodeWithText("Try again").assertIsDisplayed()

            onNode(hasSetTextAction()).performTextInput("merhaba")
            onNodeWithText("Check answer").performClick()
            waitForIdle()

            onNodeWithText("Correct").assertIsDisplayed()
            onNodeWithText("Lesson complete.").assertIsDisplayed()
        }

    private fun fixedClock(): Clock =
        object : Clock {
            override fun now(): Instant = Instant.parse("2026-10-01T08:00:00Z")
        }

    private class FakeContentTransport :
        LearnerContentTransport {
        override suspend fun loadCatalog(): LearnerCourseCatalog =
            LearnerCourseCatalog(listOf(course()))

        override suspend fun loadLesson(lessonId: ContentId): LearnerLessonPackage =
            LearnerLessonPackage(
                course = course(),
                lesson = lesson(),
                plan =
                    LearningPlan(
                        lessonId = lessonId,
                        exercises =
                            listOf(
                                ManualInputExercise(
                                    id = "exercise-1",
                                    contentId = lessonId,
                                    prompt = "Translate hello.",
                                    expectedAnswers = listOf("merhaba"),
                                ),
                            ),
                    ),
            )

        private fun course(): LearnerCourseSummary =
            LearnerCourseSummary(
                id = courseId,
                title = "Crimean Tatar basics",
                sourceLocale = LanguageTag("crh"),
                publishedVersion = 1,
                lessons = listOf(lesson()),
            )

        private fun lesson(): LearnerLessonSummary =
            LearnerLessonSummary(
                id = lessonId,
                title = "Greetings",
                sourceLocale = LanguageTag("crh"),
                publishedVersion = 1,
                localizations = emptyList(),
            )
    }

    private class FakeDownloadedLessonStore : DownloadedLessonStore {
        private val lessons = linkedMapOf<ContentId, DownloadedLesson>()

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
        private val attempts = mutableListOf<LearningAttempt>()

        override fun loadProgress() =
            LearningProgress.fromPersistedAttempts(attempts)

        override fun saveAttempt(attempt: LearningAttempt) {
            attempts += attempt
        }

        override fun loadPendingSyncAttempts(): List<LearningAttempt> = attempts.toList()

        override fun acknowledgeAttempt(attemptId: String) {
            attempts.removeIf { it.attemptId == attemptId }
        }

        override fun pendingSyncCount(): Long = attempts.size.toLong()
    }
}
