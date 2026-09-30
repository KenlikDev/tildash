package com.kenlikdev.tildash.learning.client

import com.kenlikdev.tildash.content.model.ContentId
import com.kenlikdev.tildash.content.model.LanguageTag
import com.kenlikdev.tildash.learning.LearnerCourseCatalog
import com.kenlikdev.tildash.learning.LearnerCourseSummary
import com.kenlikdev.tildash.learning.LearnerLessonPackage
import com.kenlikdev.tildash.learning.LearnerLessonSummary
import com.kenlikdev.tildash.learning.LearningPlan
import com.kenlikdev.tildash.learning.ManualInputExercise
import com.kenlikdev.tildash.storage.DownloadedLesson
import com.kenlikdev.tildash.storage.DownloadedLessonStore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Instant

class LearnerContentApplicationTest {
    private val lessonId = ContentId("550e8400-e29b-41d4-a716-446655440000")

    @Test
    fun downloadPersistsServerPackageAndDeleteRemovesIt() =
        runTest {
            val store = FakeDownloadedLessonStore()
            val transport = FakeContentTransport()
            val application =
                LearnerContentApplication(
                    transport = transport,
                    downloadedLessonStore = store,
                    clock = object : kotlin.time.Clock {
                        override fun now(): Instant = Instant.parse("2026-10-01T08:00:00Z")
                    },
                )

            application.loadCatalog()
            application.downloadLesson(lessonId)

            assertTrue(application.isDownloaded(lessonId))
            assertEquals(1, application.listDownloadedLessons().size)
            assertEquals("Translate hello.", application.listDownloadedLessons().single().plan.exercises.single().prompt)

            application.deleteDownloadedLesson(lessonId)

            assertFalse(application.isDownloaded(lessonId))
            assertTrue(application.listDownloadedLessons().isEmpty())
            assertEquals(listOf(lessonId), transport.requestedLessonIds)
        }

    private class FakeContentTransport : LearnerContentTransport {
        val requestedLessonIds = mutableListOf<ContentId>()

        private val lesson =
            LearnerLessonSummary(
                id = lessonIdStatic,
                title = "Greetings",
                sourceLocale = LanguageTag("crh"),
                publishedVersion = 1,
                localizations = emptyList(),
            )

        override suspend fun loadCatalog(): LearnerCourseCatalog =
            LearnerCourseCatalog(
                listOf(
                    LearnerCourseSummary(
                        id = ContentId("550e8400-e29b-41d4-a716-446655440001"),
                        title = "Crimean Tatar basics",
                        sourceLocale = LanguageTag("crh"),
                        publishedVersion = 1,
                        lessons = listOf(lesson),
                    ),
                ),
            )

        override suspend fun loadLesson(lessonId: ContentId): LearnerLessonPackage {
            requestedLessonIds += lessonId
            val course =
                LearnerCourseSummary(
                    id = ContentId("550e8400-e29b-41d4-a716-446655440001"),
                    title = "Crimean Tatar basics",
                    sourceLocale = LanguageTag("crh"),
                    publishedVersion = 1,
                    lessons = listOf(lesson),
                )
            return LearnerLessonPackage(
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
        }

        private companion object {
            val lessonIdStatic = ContentId("550e8400-e29b-41d4-a716-446655440000")
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

    private fun runTest(block: suspend () -> Unit) {
        var failure: Throwable? = null
        block.startCoroutine(
            object : kotlin.coroutines.Continuation<Unit> {
                override val context = kotlin.coroutines.EmptyCoroutineContext

                override fun resumeWith(result: Result<Unit>) {
                    failure = result.exceptionOrNull()
                }
            },
        )
        failure?.let { throw it }
    }
}
