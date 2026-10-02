package com.kenlikdev.tildash.storage

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.kenlikdev.tildash.content.model.ContentId
import com.kenlikdev.tildash.content.model.LanguageTag
import com.kenlikdev.tildash.learning.LearnerCourseSummary
import com.kenlikdev.tildash.learning.LearnerLessonSummary
import com.kenlikdev.tildash.learning.LearnerLocalizedText
import com.kenlikdev.tildash.learning.LearningPlan
import com.kenlikdev.tildash.learning.ManualInputExercise
import java.nio.file.Files
import java.util.Properties
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

class SqlDelightDownloadedLessonStoreTest {
    private val courseId = ContentId("550e8400-e29b-41d4-a716-446655440100")
    private val firstLessonId = ContentId("550e8400-e29b-41d4-a716-446655440101")
    private val secondLessonId = ContentId("550e8400-e29b-41d4-a716-446655440102")
    private val contentId = ContentId("550e8400-e29b-41d4-a716-446655440103")
    private val downloadedAt = Instant.parse("2026-09-29T08:00:00Z")

    @Test
    fun downloadedLessonSurvivesDatabaseReopen() {
        val databaseFile = Files.createTempFile("tildash-offline-", ".db")

        try {
            open(databaseFile.toString(), createSchema = true).use { store ->
                store.save(
                    downloadedLesson(
                        firstLessonId,
                        "Lesson one",
                        1,
                        courseLessons =
                            listOf(
                                lessonSummary(
                                    firstLessonId,
                                    "Lesson one",
                                    1,
                                    localizations =
                                        listOf(
                                            LearnerLocalizedText(
                                                LanguageTag("ru"),
                                                "Урок один",
                                            ),
                                        ),
                                ),
                                lessonSummary(secondLessonId, "Lesson two", 2),
                            ),
                    ),
                )
                assertEquals(1L, store.countLessons())

                val loaded = store.loadLesson(firstLessonId)

                assertEquals("Lesson one", loaded?.lesson?.title)
                assertEquals(listOf("exercise-1"), loaded?.plan?.exercises?.map { it.id })
                assertEquals(downloadedAt, loaded?.downloadedAt)
            }

            open(databaseFile.toString(), createSchema = false).use { store ->
                val loaded = store.loadLesson(firstLessonId)

                assertEquals(firstLessonId, loaded?.lesson?.id)
                assertEquals("Lesson one", loaded?.lesson?.title)
                assertEquals(
                    listOf(firstLessonId, secondLessonId),
                    loaded?.course?.lessons?.map { it.id },
                )
                assertEquals(
                    listOf("Урок один"),
                    loaded?.lesson?.localizations?.map { it.value },
                )
                val exercise = loaded?.plan?.exercises?.single() as ManualInputExercise
                assertEquals("Translate hello", exercise.prompt)
                assertEquals(listOf("hello"), exercise.expectedAnswers)
                assertEquals(1L, store.countLessons())
            }
        } finally {
            Files.deleteIfExists(databaseFile)
        }
    }

    @Test
    fun multipleLessonsRemainAvailableAsOneOfflineCourse() {
        val databaseFile = Files.createTempFile("tildash-offline-", ".db")

        try {
            open(databaseFile.toString(), createSchema = true).use { store ->
                val courseLessons =
                    listOf(
                        lessonSummary(firstLessonId, "Lesson one", 1),
                        lessonSummary(secondLessonId, "Lesson two", 2),
                    )
                store.save(downloadedLesson(firstLessonId, "Lesson one", 1, courseLessons))
                store.save(
                    downloadedLesson(
                        secondLessonId,
                        "Lesson two",
                        2,
                        courseLessons,
                        downloadedAt = Instant.parse("2026-09-29T09:00:00Z"),
                    ),
                )

                val firstLoaded = store.loadLesson(firstLessonId)
                val secondLoaded = store.loadLesson(secondLessonId)

                assertEquals(2L, store.countLessons())
                assertEquals(listOf(firstLessonId, secondLessonId), secondLoaded?.course?.lessons?.map { it.id })
                assertEquals(listOf("Lesson one", "Lesson two"), secondLoaded?.course?.lessons?.map { it.title })
                assertEquals(Instant.parse("2026-09-29T08:00:00Z"), firstLoaded?.downloadedAt)
                assertEquals(Instant.parse("2026-09-29T09:00:00Z"), secondLoaded?.downloadedAt)
            }
        } finally {
            Files.deleteIfExists(databaseFile)
        }
    }

    @Test
    fun replacingDownloadedLessonIsAtomicAndReplacesExercises() {
        val databaseFile = Files.createTempFile("tildash-offline-", ".db")

        try {
            open(databaseFile.toString(), createSchema = true).use { store ->
                store.save(downloadedLesson(firstLessonId, "Old lesson", 1))
                store.save(
                    downloadedLesson(
                        firstLessonId,
                        "New lesson",
                        2,
                        exercises =
                            listOf(
                                ManualInputExercise(
                                    id = "exercise-2",
                                    contentId = contentId,
                                    prompt = "Translate goodbye",
                                    expectedAnswers = listOf("goodbye"),
                                ),
                            ),
                    ),
                )

                val loaded = store.loadLesson(firstLessonId)

                assertEquals("New lesson", loaded?.lesson?.title)
                assertEquals(2, loaded?.lesson?.publishedVersion)
                assertEquals(listOf("exercise-2"), loaded?.plan?.exercises?.map { it.id })
                assertEquals(1L, store.countLessons())
            }
        } finally {
            Files.deleteIfExists(databaseFile)
        }
    }

    @Test
    fun deletingLastLessonRemovesItsCourseButKeepsOtherCourses() {
        val databaseFile = Files.createTempFile("tildash-offline-", ".db")

        try {
            open(databaseFile.toString(), createSchema = true).use { store ->
                store.save(downloadedLesson(firstLessonId, "Lesson one", 1))
                store.deleteLesson(firstLessonId)

                assertNull(store.loadLesson(firstLessonId))
                assertEquals(0L, store.countLessons())
                assertTrue(store.listLessons().isEmpty())
            }
        } finally {
            Files.deleteIfExists(databaseFile)
        }
    }

    private fun open(
        path: String,
        createSchema: Boolean,
    ): TestStore {
        val driver =
            JdbcSqliteDriver(
                url = "jdbc:sqlite:" + path,
                properties =
                    Properties().apply {
                        put("foreign_keys", "true")
                    },
            )
        if (createSchema) {
            TildashDatabase.Schema.create(driver)
        }
        return TestStore(driver, SqlDelightDownloadedLessonStore(driver))
    }

    private fun downloadedLesson(
        lessonId: ContentId,
        title: String,
        version: Int,
        courseLessons: List<LearnerLessonSummary> = listOf(lessonSummary(lessonId, title, version)),
        downloadedAt: Instant = this.downloadedAt,
        exercises: List<ManualInputExercise> =
            listOf(
                ManualInputExercise(
                    id = "exercise-1",
                    contentId = contentId,
                    prompt = "Translate hello",
                    expectedAnswers = listOf("hello"),
                ),
            ),
    ) = DownloadedLesson(
        course =
            LearnerCourseSummary(
                id = courseId,
                title = "Crimean Tatar basics",
                sourceLocale = LanguageTag("crh"),
                publishedVersion = 1,
                lessons = courseLessons,
            ),
        lesson = courseLessons.first { it.id == lessonId },
        plan = LearningPlan(lessonId = lessonId, exercises = exercises),
        downloadedAt = downloadedAt,
    )

    private fun lessonSummary(
        lessonId: ContentId,
        title: String,
        version: Int,
        localizations: List<LearnerLocalizedText> = emptyList(),
    ) = LearnerLessonSummary(
        id = lessonId,
        title = title,
        sourceLocale = LanguageTag("crh"),
        publishedVersion = version,
        localizations = localizations,
    )

    private class TestStore(
        private val driver: JdbcSqliteDriver,
        private val delegate: SqlDelightDownloadedLessonStore,
    ) : DownloadedLessonStore by delegate,
        AutoCloseable {
        override fun close() = driver.close()
    }
}
