package com.kenlikdev.tildash.storage

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.kenlikdev.tildash.content.model.ContentId
import com.kenlikdev.tildash.content.model.LanguageTag
import com.kenlikdev.tildash.learning.AnswerOutcome
import com.kenlikdev.tildash.learning.LearnerCourseSummary
import com.kenlikdev.tildash.learning.LearnerLessonSummary
import com.kenlikdev.tildash.learning.LearnerResponse
import com.kenlikdev.tildash.learning.LearningAttempt
import com.kenlikdev.tildash.learning.LearningPlan
import com.kenlikdev.tildash.learning.LessonSession
import com.kenlikdev.tildash.learning.LessonSessionState
import com.kenlikdev.tildash.learning.ManualInputExercise
import java.nio.file.Files
import kotlin.coroutines.Continuation
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.startCoroutine
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Instant

class OfflineLearningRecoveryTest {
    private val courseId = ContentId("550e8400-e29b-41d4-a716-446655440200")
    private val lessonId = ContentId("550e8400-e29b-41d4-a716-446655440201")
    private val contentId = ContentId("550e8400-e29b-41d4-a716-446655440202")
    private val occurredAt = Instant.parse("2026-09-29T09:00:00Z")

    @Test
    fun lessonProgressSurvivesRestartAndRecoversAfterTransientSyncFailure() {
        val databaseFile = Files.createTempFile("tildash-offline-recovery-", ".db")

        try {
            val attempt =
                LearningAttempt(
                    attemptId = "attempt-1",
                    lessonId = lessonId,
                    exerciseId = "exercise-1",
                    response = LearnerResponse.Text("hello"),
                    outcome = AnswerOutcome.CORRECT,
                    occurredAt = occurredAt,
                )

            open(databaseFile.toString(), createSchema = true).use { context ->
                context.downloads.save(downloadedLesson())

                val downloaded = requireNotNull(context.downloads.loadLesson(lessonId))
                val session = LessonSession.start(downloaded.plan)

                val submission =
                    session.submit(
                        attemptId = attempt.attemptId,
                        response = attempt.response,
                        occurredAt = attempt.occurredAt,
                    )

                assertEquals(AnswerOutcome.CORRECT, submission.evaluation.outcome)
                assertEquals(LessonSessionState.COMPLETED, submission.session.state)

                context.progress.saveAttempt(attempt)
                assertEquals(1L, context.progress.pendingSyncCount())
            }

            open(databaseFile.toString(), createSchema = false).use { context ->
                val downloaded = requireNotNull(context.downloads.loadLesson(lessonId))
                val progress = context.progress.loadProgress()
                val resumed = LessonSession.start(downloaded.plan, progress)

                assertEquals(LessonSessionState.COMPLETED, resumed.state)
                assertEquals(listOf(attempt), progress.attempts)

                var calls = 0
                val transport =
                    object : LearningSyncTransport {
                        override suspend fun synchronize(
                            batch: com.kenlikdev.tildash.learning.LearningProgressSyncBatch,
                        ): LearningSyncTransportResult {
                            calls += 1
                            if (calls == 1) {
                                throw TransientLearningSyncFailure("temporary outage")
                            }
                            return LearningSyncTransportResult.Succeeded(
                                acknowledgedAttemptIds = batch.attempts.map { it.attemptId },
                            )
                        }
                    }

                val report =
                    runSuspend {
                        LearningSyncCoordinator(
                            store = context.progress,
                            transport = transport,
                            retryPolicy =
                                LearningSyncRetryPolicy(
                                    maxAttempts = 2,
                                    initialDelayMillis = 1,
                                ),
                            delayBeforeRetry = {},
                        ).synchronize("device-a")
                    }

                assertEquals(2, calls)
                assertEquals(listOf("attempt-1"), report.acknowledgedAttemptIds)
                assertEquals(0L, context.progress.pendingSyncCount())
                assertEquals(listOf(attempt), context.progress.loadProgress().attempts)
            }
        } finally {
            Files.deleteIfExists(databaseFile)
        }
    }

    private fun open(
        path: String,
        createSchema: Boolean,
    ): TestContext {
        val driver = JdbcSqliteDriver("jdbc:sqlite:" + path)
        if (createSchema) {
            TildashDatabase.Schema.create(driver)
        }
        return TestContext(
            driver = driver,
            progress = SqlDelightLearningProgressStore(driver),
            downloads = SqlDelightDownloadedLessonStore(driver),
        )
    }

    private fun downloadedLesson() =
        DownloadedLesson(
            course =
                LearnerCourseSummary(
                    id = courseId,
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
                                contentId = contentId,
                                prompt = "Translate hello",
                                expectedAnswers = listOf("hello"),
                            ),
                        ),
                ),
            downloadedAt = Instant.parse("2026-09-29T08:00:00Z"),
        )

    private class TestContext(
        private val driver: JdbcSqliteDriver,
        val progress: SqlDelightLearningProgressStore,
        val downloads: SqlDelightDownloadedLessonStore,
    ) : AutoCloseable {
        override fun close() = driver.close()
    }

    private fun runSuspend(block: suspend () -> LearningSyncReport): LearningSyncReport {
        var result: Result<LearningSyncReport>? = null

        block.startCoroutine(
            object : Continuation<LearningSyncReport> {
                override val context = EmptyCoroutineContext

                override fun resumeWith(value: Result<LearningSyncReport>) {
                    result = value
                }
            },
        )

        return requireNotNull(result).getOrThrow()
    }
}
