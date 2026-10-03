package com.kenlikdev.tildash

import androidx.compose.ui.test.assertExists
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.kenlikdev.tildash.content.model.ContentId
import com.kenlikdev.tildash.content.model.LanguageTag
import com.kenlikdev.tildash.learning.AnswerOutcome
import com.kenlikdev.tildash.learning.LearnerCourseCatalog
import com.kenlikdev.tildash.learning.LearnerCourseSummary
import com.kenlikdev.tildash.learning.LearnerLessonPackage
import com.kenlikdev.tildash.learning.LearnerLessonSummary
import com.kenlikdev.tildash.learning.LearnerResponse
import com.kenlikdev.tildash.learning.LearningAttempt
import com.kenlikdev.tildash.learning.LearningPlan
import com.kenlikdev.tildash.learning.LearningProgress
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

class AppUiTest {
    @get:org.junit.Rule
    val rule = createComposeRule()

    @Test
    fun learnerCanCompleteLessonRemoveAndRedownload() {
        val downloadedLessonStore = InMemoryDownloadedLessonStore()
        val progressStore = InMemoryLearningProgressStore()
        val transport = FakeLearnerContentTransport()
        val clock =
            object : Clock {
                override fun now(): Instant = Instant.parse("2026-10-03T08:00:00Z")
            }

        val learnerApplication =
            LearnerLessonApplication(
                downloadedLessonStore = downloadedLessonStore,
                coordinator =
                    LearnerLessonCoordinator(
                        downloadedLessonStore = downloadedLessonStore,
                        learningProgressStore = progressStore,
                    ),
                clock = clock,
                attemptIdGenerator = {
                    "attempt-${progressStore.progress.attempts.size + 1}"
                },
            )
        val contentApplication =
            LearnerContentApplication(
                transport = transport,
                downloadedLessonStore = downloadedLessonStore,
                clock = clock,
            )

        rule.setContent {
            App(
                learnerApplication = learnerApplication,
                contentApplication = contentApplication,
            )
        }

        rule.waitForIdle()
        rule.onNodeWithText("Crimean Tatar basics").assertExists()
        rule.onNodeWithText("Greetings").assertExists()
        rule.onNodeWithText("Download").performClick()
        rule.waitForIdle()

        rule.onNodeWithText("Open").performClick()
        rule.onNodeWithTag("learner-answer-input").performTextInput("wrong")
        rule.onNodeWithText("Check answer").performClick()
        rule.waitForIdle()
        rule.onNodeWithText("Try again").assertExists()

        rule.onNodeWithTag("learner-answer-input").performTextInput("merhaba")
        rule.onNodeWithText("Check answer").performClick()
        rule.waitForIdle()
        rule.onNodeWithText("Lesson complete.").assertExists()

        rule.onNodeWithText("Back to lessons").performClick()
        rule.onNodeWithText("Downloaded (1)").assertExists()
        rule.onNodeWithText("Remove").performClick()
        rule.onNodeWithText("No downloaded lessons yet. Open Catalog and download a published lesson.")
            .assertExists()

        rule.onNodeWithText("Catalog").performClick()
        rule.onNodeWithText("Download").performClick()
        rule.waitForIdle()
        rule.onNodeWithText("Open").performClick()
        rule.waitForIdle()
        rule.onNodeWithText("Lesson complete.").assertExists()
    }

    private class FakeLearnerContentTransport : LearnerContentTransport {
        private val courseId = ContentId("550e8400-e29b-41d4-a716-446655440010")
        private val lessonId = ContentId("550e8400-e29b-41d4-a716-446655440011")

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

        private val lessonPackage =
            LearnerLessonPackage(
                course = course,
                lesson = lesson,
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

        override suspend fun loadCatalog(): LearnerCourseCatalog = LearnerCourseCatalog(listOf(course))

        override suspend fun loadLesson(lessonId: ContentId): LearnerLessonPackage {
            check(lessonId == this.lessonId)
            return lessonPackage
        }
    }

    private class InMemoryDownloadedLessonStore : DownloadedLessonStore {
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

    private class InMemoryLearningProgressStore : LearningProgressStore {
        var progress = LearningProgress.empty()
            private set

        override fun loadProgress(): LearningProgress = progress

        override fun saveAttempt(attempt: LearningAttempt) {
            progress = LearningProgress.fromPersistedAttempts(progress.attempts + attempt)
        }

        override fun loadPendingSyncAttempts(): List<LearningAttempt> = emptyList()

        override fun acknowledgeAttempt(attemptId: String) = Unit

        override fun pendingSyncCount(): Long = 0
    }
}
