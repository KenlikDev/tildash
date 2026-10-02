package com.kenlikdev.tildash.learning.client

import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.kenlikdev.tildash.App
import com.kenlikdev.tildash.content.model.ContentId
import com.kenlikdev.tildash.content.model.LanguageTag
import com.kenlikdev.tildash.learning.AnswerOutcome
import com.kenlikdev.tildash.learning.LearnerCourseCatalog
import com.kenlikdev.tildash.learning.LearnerCourseSummary
import com.kenlikdev.tildash.learning.LearnerLessonPackage
import com.kenlikdev.tildash.learning.LearnerLessonSummary
import com.kenlikdev.tildash.learning.LearningAttempt
import com.kenlikdev.tildash.learning.LearningPlan
import com.kenlikdev.tildash.learning.LearningProgress
import com.kenlikdev.tildash.learning.ManualInputExercise
import com.kenlikdev.tildash.storage.DownloadedLesson
import com.kenlikdev.tildash.storage.DownloadedLessonStore
import com.kenlikdev.tildash.storage.LearningProgressStore
import org.junit.Rule
import org.junit.Test

class AppUiTest {
    @get:Rule
    val rule = createComposeRule()

    private val lessonId = ContentId("550e8400-e29b-41d4-a716-446655440000")
    private val courseId = ContentId("550e8400-e29b-41d4-a716-446655440001")

    @Test
    fun learnerCanDownloadOpenAndCompleteLessonThroughDesktopUi() {
        val store = FakeDownloadedLessonStore()
        val progressStore = FakeLearningProgressStore()
        val lesson = lessonSummary()
        val packageData = lessonPackage(lesson)

        val contentApplication =
            LearnerContentApplication(
                transport =
                    object : LearnerContentTransport {
                        override suspend fun loadCatalog(): LearnerCourseCatalog =
                            LearnerCourseCatalog(
                                courses =
                                    listOf(
                                        LearnerCourseSummary(
                                            id = courseId,
                                            title = "Crimean Tatar basics",
                                            sourceLocale = LanguageTag("crh"),
                                            publishedVersion = 1,
                                            lessons = listOf(lesson),
                                        ),
                                    ),
                            )

                        override suspend fun loadLesson(lessonId: ContentId): LearnerLessonPackage {
                            check(lessonId == this@AppUiTest.lessonId)
                            return packageData
                        }
                    },
                downloadedLessonStore = store,
            )

        val learnerApplication =
            LearnerLessonApplication(
                downloadedLessonStore = store,
                coordinator =
                    LearnerLessonCoordinator(
                        downloadedLessonStore = store,
                        learningProgressStore = progressStore,
                    ),
                attemptIdGenerator = { "attempt-1" },
            )

        rule.setContent {
            App(
                learnerApplication = learnerApplication,
                contentApplication = contentApplication,
            )
        }

        rule.onNodeWithText("Crimean Tatar basics").assertTextEquals("Crimean Tatar basics")
        rule.onNodeWithText("Greetings").assertTextEquals("Greetings")
        rule.onNodeWithText("Download").performClick()
        rule.waitForIdle()

        rule.onNodeWithText("Open").performClick()
        rule.waitForIdle()
        rule.onNodeWithText("Translate hello.").assertTextEquals("Translate hello.")
        rule.onNodeWithTag("learner-answer-input").performTextInput("merhaba")
        rule.onNodeWithText("Check answer").performClick()
        rule.waitForIdle()

        rule.onNodeWithText("Correct").assertTextEquals("Correct")
        rule.onNodeWithText("Lesson complete.").assertTextEquals("Lesson complete.")
    }

    private fun lessonSummary() =
        LearnerLessonSummary(
            id = lessonId,
            title = "Greetings",
            sourceLocale = LanguageTag("crh"),
            publishedVersion = 1,
            localizations = emptyList(),
        )

    private fun lessonPackage(lesson: LearnerLessonSummary) =
        LearnerLessonPackage(
            course =
                LearnerCourseSummary(
                    id = courseId,
                    title = "Crimean Tatar basics",
                    sourceLocale = LanguageTag("crh"),
                    publishedVersion = 1,
                    lessons = listOf(lesson),
                ),
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

        override fun loadProgress(): LearningProgress = LearningProgress.fromPersistedAttempts(attempts)

        override fun saveAttempt(attempt: LearningAttempt) {
            check(attempt.outcome == AnswerOutcome.CORRECT)
            attempts.removeAll { it.attemptId == attempt.attemptId }
            attempts += attempt
        }

        override fun loadPendingSyncAttempts(): List<LearningAttempt> = attempts.toList()

        override fun acknowledgeAttempt(attemptId: String) {
            attempts.removeAll { it.attemptId == attemptId }
        }

        override fun pendingSyncCount(): Long = attempts.size.toLong()
    }
}
