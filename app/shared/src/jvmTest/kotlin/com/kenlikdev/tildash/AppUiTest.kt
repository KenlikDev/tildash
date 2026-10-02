package com.kenlikdev.tildash

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertExists
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.v2.runComposeUiTest
import com.kenlikdev.tildash.content.model.ContentId
import com.kenlikdev.tildash.content.model.LanguageTag
import com.kenlikdev.tildash.learning.AnswerOutcome
import com.kenlikdev.tildash.learning.LearnerCourseCatalog
import com.kenlikdev.tildash.learning.LearnerCourseSummary
import com.kenlikdev.tildash.learning.LearnerLessonPackage
import com.kenlikdev.tildash.learning.LearnerLessonSummary
import com.kenlikdev.tildash.learning.LearningPlan
import com.kenlikdev.tildash.learning.LearningProgress
import com.kenlikdev.tildash.learning.ManualInputExercise
import com.kenlikdev.tildash.learning.client.KtorLearnerContentTransport
import com.kenlikdev.tildash.learning.client.LearnerContentApplication
import com.kenlikdev.tildash.learning.client.LearnerLessonApplication
import com.kenlikdev.tildash.learning.client.LearnerLessonCoordinator
import com.kenlikdev.tildash.storage.DownloadedLesson
import com.kenlikdev.tildash.storage.DownloadedLessonStore
import com.kenlikdev.tildash.storage.LearningProgressStore
import kotlin.test.Test
import kotlin.time.Clock
import kotlin.time.Instant

class AppUiTest {
    @OptIn(ExperimentalTestApi::class)
    @Test
    fun learnerCanDownloadOpenAnswerRemoveAndRedownloadLesson() =
        runComposeUiTest {
            val fixture = Fixture()

            setContent {
                App(
                    learnerApplication = fixture.learnerApplication(),
                    contentApplication = fixture.contentApplication(),
                )
            }

            waitForIdle()
            onNodeWithText("Tildash Learning").assertExists()
            onNodeWithText("Crimean Tatar basics").assertExists()
            onNodeWithText("Greetings").assertExists()
            onNodeWithText("Download").performClick()

            waitForIdle()
            onNodeWithText("Downloaded (1)").performClick()
            onNodeWithText("Open").performClick()

            waitForIdle()
            onNodeWithText("Translate hello.").assertExists()
            onNodeWithText("Your answer").assertExists()
            onNodeWithText("Check answer").assertExists()
            onNode(hasSetTextAction()).performTextInput("merhaba")
            onNodeWithText("Check answer").performClick()

            waitForIdle()
            onNodeWithText("Lesson complete.").assertExists()
            kotlin.test.assertEquals(AnswerOutcome.CORRECT, fixture.progressStore.loadProgress().attempts.single().outcome)

            onNodeWithText("Back to lessons").performClick()
            waitForIdle()
            onNodeWithText("Downloaded (1)").assertExists()
            onNodeWithText("Remove").performClick()

            waitForIdle()
            onNodeWithText("Downloaded (0)").assertExists()
            onNodeWithText("Catalog").performClick()
            waitForIdle()
            onNodeWithText("Download").performClick()

            waitForIdle()
            onNodeWithText("Downloaded (1)").assertExists()

            val restartedFixture = fixture.learnerApplication()
            setContent {
                App(
                    learnerApplication = restartedFixture,
                    contentApplication = fixture.contentApplication(),
                )
            }

            waitForIdle()
            onNodeWithText("Downloaded (1)").performClick()
            onNodeWithText("Open").performClick()
            waitForIdle()
            onNodeWithText("Lesson complete.").assertExists()
        }

    private class Fixture {
        val lessonId = ContentId("550e8400-e29b-41d4-a716-446655440000")
        private val courseId = ContentId("550e8400-e29b-41d4-a716-446655440001")
        private val exerciseId = "exercise-1"
        private val fixedInstant = Instant.parse("2026-10-01T08:00:00Z")
        val store = FakeDownloadedLessonStore()
        val progressStore = FakeLearningProgressStore()

        private val lesson =
            LearnerLessonSummary(
                id = lessonId,
                title = "Greetings",
                sourceLocale = LanguageTag("crh"),
                publishedVersion = 1,
                localizations = emptyList(),
            )

        private val course =
            LearnerCourseSummary(
                id = courseId,
                title = "Crimean Tatar basics",
                sourceLocale = LanguageTag("crh"),
                publishedVersion = 1,
                lessons = listOf(lesson),
            )

        private val packageData =
            LearnerLessonPackage(
                course = course,
                lesson = lesson,
                plan =
                    LearningPlan(
                        lessonId = lessonId,
                        exercises =
                            listOf(
                                ManualInputExercise(
                                    id = exerciseId,
                                    contentId = lessonId,
                                    prompt = "Translate hello.",
                                    expectedAnswers = listOf("merhaba"),
                                ),
                            ),
                    ),
            )

        private val transport =
            object : com.kenlikdev.tildash.learning.client.LearnerContentTransport {
                override suspend fun loadCatalog(): LearnerCourseCatalog = LearnerCourseCatalog(listOf(course))

                override suspend fun loadLesson(lessonId: ContentId): LearnerLessonPackage {
                    kotlin.test.assertEquals(this@Fixture.lessonId, lessonId)
                    return packageData
                }
            }

        fun contentApplication() =
            LearnerContentApplication(
                transport = transport,
                downloadedLessonStore = store,
                clock = fixedClock(),
            )

        fun learnerApplication() =
            LearnerLessonApplication(
                downloadedLessonStore = store,
                coordinator =
                    LearnerLessonCoordinator(
                        downloadedLessonStore = store,
                        learningProgressStore = progressStore,
                    ),
                clock = fixedClock(),
                attemptIdGenerator = { "attempt-1" },
            )

        private fun fixedClock() =
            object : Clock {
                override fun now(): Instant = fixedInstant
            }
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
        private var progress = LearningProgress.empty()

        override fun loadProgress(): LearningProgress = progress

        override fun saveAttempt(attempt: com.kenlikdev.tildash.learning.LearningAttempt) {
            progress = LearningProgress.fromPersistedAttempts(progress.attempts + attempt)
        }

        override fun loadPendingSyncAttempts(): List<com.kenlikdev.tildash.learning.LearningAttempt> = emptyList()

        override fun acknowledgeAttempt(attemptId: String) = Unit

        override fun pendingSyncCount(): Long = 0L
    }
}
