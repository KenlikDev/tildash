package com.kenlikdev.tildash

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.runComposeUiTest
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.kenlikdev.tildash.content.model.ContentId
import com.kenlikdev.tildash.content.model.LanguageTag
import com.kenlikdev.tildash.learning.LearnerCourseCatalog
import com.kenlikdev.tildash.learning.LearnerCourseSummary
import com.kenlikdev.tildash.learning.LearnerLessonPackage
import com.kenlikdev.tildash.learning.LearnerLessonSummary
import com.kenlikdev.tildash.learning.LearningPlan
import com.kenlikdev.tildash.learning.ManualInputExercise
import com.kenlikdev.tildash.learning.client.LearnerContentApplication
import com.kenlikdev.tildash.learning.client.LearnerContentTransport
import com.kenlikdev.tildash.learning.client.LearnerLessonApplication
import com.kenlikdev.tildash.learning.client.LearnerLessonCoordinator
import com.kenlikdev.tildash.storage.DownloadedLesson
import com.kenlikdev.tildash.storage.DownloadedLessonStore
import com.kenlikdev.tildash.storage.LearningProgressStore
import com.kenlikdev.tildash.storage.SqlDelightDownloadedLessonStore
import com.kenlikdev.tildash.storage.SqlDelightLearningProgressStore
import com.kenlikdev.tildash.storage.TildashDatabase
import java.nio.file.Files
import java.util.Properties
import kotlin.test.Test
import kotlin.time.Clock
import kotlin.time.Instant

class AppUiTest {
    private val lessonId = ContentId("550e8400-e29b-41d4-a716-446655440000")
    private val courseId = ContentId("550e8400-e29b-41d4-a716-446655440001")

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun learnerCanCompleteDownloadAnswerRestartRemoveAndRedownloadFlow() =
        runComposeUiTest {
            val databaseFile = Files.createTempFile("tildash-ui-", ".db")

            try {
                openDatabase(databaseFile.toString(), createSchema = true).use { database ->
                    val downloadedLessonStore = SqlDelightDownloadedLessonStore(database.driver)
                    val progressStore = SqlDelightLearningProgressStore(database.driver)
                    val learnerApplication = createLearnerApplication(downloadedLessonStore, progressStore)
                    val contentApplication =
                        LearnerContentApplication(
                            transport = FakeContentTransport(),
                            downloadedLessonStore = downloadedLessonStore,
                            clock = TestClock,
                        )

                    setContent {
                        App(
                            learnerApplication = learnerApplication,
                            contentApplication = contentApplication,
                        )
                    }
                    waitForIdle()

                    onNodeWithText("Crimean Tatar basics").assertExists()
                    onNodeWithText("Greetings").assertExists()
                    onNodeWithText("Download").performClick()
                    waitForIdle()
                    onNodeWithText("Downloaded (1)").assertExists()
                    onNodeWithText("Open").performClick()
                    waitForIdle()

                    onNodeWithText("Translate hello.").assertExists()
                    onNodeWithText("Check answer").assertExists()
                    onNodeWithText(hasSetTextAction()).performTextInput("wrong")
                    onNodeWithText("Check answer").performClick()
                    waitForIdle()
                    onNodeWithText("Try again").assertExists()

                    onNodeWithText(hasSetTextAction()).performTextInput("merhaba")
                    onNodeWithText("Check answer").performClick()
                    waitForIdle()
                    onNodeWithText("Lesson complete.").assertExists()
                }

                openDatabase(databaseFile.toString(), createSchema = false).use { database ->
                    val downloadedLessonStore = SqlDelightDownloadedLessonStore(database.driver)
                    val progressStore = SqlDelightLearningProgressStore(database.driver)
                    val learnerApplication = createLearnerApplication(downloadedLessonStore, progressStore)
                    val contentApplication =
                        LearnerContentApplication(
                            transport = FakeContentTransport(),
                            downloadedLessonStore = downloadedLessonStore,
                            clock = TestClock,
                        )

                    setContent {
                        App(
                            learnerApplication = learnerApplication,
                            contentApplication = contentApplication,
                        )
                    }
                    waitForIdle()

                    onNodeWithText("Downloaded (1)").performClick()
                    onNodeWithText("Open").performClick()
                    waitForIdle()
                    onNodeWithText("Lesson complete.").assertExists()

                    onNodeWithText("Back to lessons").performClick()
                    waitForIdle()
                    onNodeWithText("Remove").performClick()
                    waitForIdle()
                    onNodeWithText("No downloaded lessons yet. Open Catalog and download a published lesson.").assertExists()

                    onNodeWithText("Catalog").performClick()
                    waitForIdle()
                    onNodeWithText("Download").performClick()
                    waitForIdle()
                    onNodeWithText("Downloaded (1)").assertExists()
                }
            } finally {
                Files.deleteIfExists(databaseFile)
            }
        }

    private fun openDatabase(
        path: String,
        createSchema: Boolean,
    ): TestDatabase =
        TestDatabase(
            driver =
                JdbcSqliteDriver(
                    url = "jdbc:sqlite:" + path,
                    properties =
                        Properties().apply {
                            put("foreign_keys", "true")
                        },
                ),
            createSchema = createSchema,
        )

    private fun createLearnerApplication(
        downloadedLessonStore: DownloadedLessonStore,
        progressStore: LearningProgressStore,
    ): LearnerLessonApplication =
        LearnerLessonApplication(
            downloadedLessonStore = downloadedLessonStore,
            coordinator =
                LearnerLessonCoordinator(
                    downloadedLessonStore = downloadedLessonStore,
                    learningProgressStore = progressStore,
                ),
            clock = TestClock,
            attemptIdGenerator = { "ui-test-attempt-" + progressStore.loadProgress().attempts.size },
        )

    private inner class FakeContentTransport : LearnerContentTransport {
        private val lesson =
            LearnerLessonSummary(
                id = lessonId,
                title = "Greetings",
                sourceLocale = LanguageTag("crh"),
                publishedVersion = 1,
                localizations = emptyList(),
            )

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

        override suspend fun loadLesson(lessonId: ContentId): LearnerLessonPackage =
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
    }

    private class TestDatabase(
        val driver: JdbcSqliteDriver,
        createSchema: Boolean,
    ) : AutoCloseable {
        init {
            if (createSchema) {
                TildashDatabase.Schema.create(driver)
            }
        }

        override fun close() = driver.close()
    }

    private object TestClock : Clock {
        override fun now(): Instant = Instant.parse("2026-10-02T12:00:00Z")
    }
}