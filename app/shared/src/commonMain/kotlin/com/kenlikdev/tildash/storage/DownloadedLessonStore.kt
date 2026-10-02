package com.kenlikdev.tildash.storage

import app.cash.sqldelight.db.SqlDriver
import com.kenlikdev.tildash.content.model.ContentId
import com.kenlikdev.tildash.content.model.LanguageTag
import com.kenlikdev.tildash.learning.LearnerCourseSummary
import com.kenlikdev.tildash.learning.LearnerLessonSummary
import com.kenlikdev.tildash.learning.LearnerLocalizedText
import com.kenlikdev.tildash.learning.LearningExercise
import com.kenlikdev.tildash.learning.LearningPlan
import com.kenlikdev.tildash.learning.ManualInputExercise
import kotlin.time.Instant
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

data class DownloadedLesson(
    val course: LearnerCourseSummary,
    val lesson: LearnerLessonSummary,
    val plan: LearningPlan,
    val downloadedAt: Instant,
) {
    init {
        require(course.lessons.any { it.id == lesson.id }) {
            "Downloaded lesson must be listed in its course."
        }
        require(plan.lessonId == lesson.id) {
            "Learning plan lesson ID must match the downloaded lesson."
        }
    }
}

interface DownloadedLessonStore {
    fun save(downloadedLesson: DownloadedLesson)

    fun loadLesson(lessonId: ContentId): DownloadedLesson?

    fun listLessons(): List<DownloadedLesson>

    fun deleteLesson(lessonId: ContentId)

    fun countLessons(): Long
}

class SqlDelightDownloadedLessonStore(
    driver: SqlDriver,
) : DownloadedLessonStore {
    private val database = TildashDatabase(driver)
    private val queries = database.learningStorageQueries

    override fun save(downloadedLesson: DownloadedLesson) {
        database.transaction {
            val lessonId = downloadedLesson.lesson.id.value
            val existing = queries.selectDownloadedLesson(lessonId).executeAsOneOrNull()

            if (existing != null) {
                queries.deleteDownloadedLesson(lessonId)
                queries.deleteDownloadedCourseIfOrphaned(existing.course_id)
            }

            val courseId = downloadedLesson.course.id.value
            if (queries.selectDownloadedCourse(courseId).executeAsOneOrNull() == null) {
                queries.insertDownloadedCourse(
                    course_id = courseId,
                    title = downloadedLesson.course.title,
                    source_locale = downloadedLesson.course.sourceLocale.value,
                    published_version = downloadedLesson.course.publishedVersion.toLong(),
                    downloaded_at_epoch_millis = downloadedLesson.downloadedAt.toEpochMilliseconds(),
                )
            } else {
                queries.updateDownloadedCourse(
                    title = downloadedLesson.course.title,
                    source_locale = downloadedLesson.course.sourceLocale.value,
                    published_version = downloadedLesson.course.publishedVersion.toLong(),
                    downloaded_at_epoch_millis = downloadedLesson.downloadedAt.toEpochMilliseconds(),
                    course_id = courseId,
                )
            }

            queries.insertDownloadedLesson(
                lesson_id = lessonId,
                course_id = downloadedLesson.course.id.value,
                title = downloadedLesson.lesson.title,
                source_locale = downloadedLesson.lesson.sourceLocale.value,
                published_version = downloadedLesson.lesson.publishedVersion.toLong(),
                position = downloadedLesson.position().toLong(),
                downloaded_at_epoch_millis = downloadedLesson.downloadedAt.toEpochMilliseconds(),
                course_lessons_json = encodeCourseLessons(downloadedLesson.course.lessons),
            )

            downloadedLesson.plan.exercises.forEachIndexed { index, exercise ->
                insertExercise(
                    lessonId = lessonId,
                    exercise = exercise,
                    position = index,
                )
            }
        }
    }

    override fun loadLesson(lessonId: ContentId): DownloadedLesson? {
        val lesson = queries.selectDownloadedLesson(lessonId.value).executeAsOneOrNull() ?: return null
        return reconstructLesson(lesson.lesson_id)
    }

    override fun listLessons(): List<DownloadedLesson> =
        queries.selectDownloadedLessons().executeAsList().map { row ->
            reconstructLesson(row.lesson_id)
        }

    override fun deleteLesson(lessonId: ContentId) {
        database.transaction {
            val existing = queries.selectDownloadedLesson(lessonId.value).executeAsOneOrNull()
            if (existing != null) {
                queries.deleteDownloadedLesson(lessonId.value)
                queries.deleteDownloadedCourseIfOrphaned(existing.course_id)
            }
        }
    }

    override fun countLessons(): Long = queries.countDownloadedLessons().executeAsOne()

    private fun reconstructLesson(lessonId: String): DownloadedLesson {
        val lessonRow =
            queries.selectDownloadedLesson(lessonId).executeAsOneOrNull()
                ?: error("Downloaded lesson '" + lessonId + "' does not exist.")

        val course =
            queries.selectDownloadedCourse(lessonRow.course_id).executeAsOneOrNull()
                ?: error("Downloaded lesson '" + lessonId + "' has no downloaded course.")

        val lessonSummaries =
            decodeCourseLessons(lessonRow.course_lessons_json).ifEmpty {
                listOf(
                    LearnerLessonSummary(
                        id = ContentId(lessonRow.lesson_id),
                        title = lessonRow.title,
                        sourceLocale = LanguageTag(lessonRow.source_locale),
                        publishedVersion = lessonRow.published_version.toInt(),
                        localizations = emptyList(),
                    ),
                )
            }

        val lessonSummary =
            lessonSummaries.firstOrNull { it.id.value == lessonRow.lesson_id }
                ?: error(
                    "Downloaded lesson '${lessonId}' is missing from its persisted course snapshot.",
                )

        val exercises =
            queries.selectDownloadedExercises(lessonRow.lesson_id).executeAsList().map { row ->
                val answers =
                    queries
                        .selectDownloadedManualAnswers(row.lesson_id)
                        .executeAsList()
                        .filter { it.exercise_id == row.exercise_id }
                        .sortedBy { it.position }
                        .map { it.answer }

                when (row.exercise_type) {
                    MANUAL_INPUT -> {
                        ManualInputExercise(
                            id = row.exercise_id,
                            contentId = ContentId(row.content_id),
                            prompt = row.prompt,
                            expectedAnswers = answers,
                        )
                    }

                    else -> {
                        error(
                            "Unsupported downloaded exercise type '" +
                                row.exercise_type +
                                "' for lesson '" +
                                lessonId +
                                "'.",
                        )
                    }
                }
            }

        return DownloadedLesson(
            course =
                LearnerCourseSummary(
                    id = ContentId(course.course_id),
                    title = course.title,
                    sourceLocale = LanguageTag(course.source_locale),
                    publishedVersion = course.published_version.toInt(),
                    lessons = lessonSummaries,
                ),
            lesson = lessonSummary,
            plan =
                LearningPlan(
                    lessonId = lessonSummary.id,
                    exercises = exercises,
                ),
            downloadedAt = Instant.fromEpochMilliseconds(lessonRow.downloaded_at_epoch_millis),
        )
    }

    private fun insertExercise(
        lessonId: String,
        exercise: LearningExercise,
        position: Int,
    ) {
        when (exercise) {
            is ManualInputExercise -> {
                queries.insertDownloadedExercise(
                    lesson_id = lessonId,
                    exercise_id = exercise.id,
                    content_id = exercise.contentId.value,
                    position = position.toLong(),
                    exercise_type = MANUAL_INPUT,
                    prompt = exercise.prompt,
                )
                exercise.expectedAnswers.forEachIndexed { answerPosition, answer ->
                    queries.insertDownloadedManualAnswer(
                        lesson_id = lessonId,
                        exercise_id = exercise.id,
                        position = answerPosition.toLong(),
                        answer = answer,
                    )
                }
            }

            else -> {
                error(
                    "Unsupported learning exercise type '" +
                        exercise::class.simpleName +
                        "'.",
                )
            }
        }
    }

    private fun encodeCourseLessons(lessons: List<LearnerLessonSummary>): String =
        buildJsonArray {
            lessons.forEach { lesson ->
                add(
                    buildJsonObject {
                        put("id", JsonPrimitive(lesson.id.value))
                        put("title", JsonPrimitive(lesson.title))
                        put("sourceLocale", JsonPrimitive(lesson.sourceLocale.value))
                        put("publishedVersion", JsonPrimitive(lesson.publishedVersion))
                        put(
                            "localizations",
                            buildJsonArray {
                                lesson.localizations.forEach { localization ->
                                    add(
                                        buildJsonObject {
                                            put("locale", JsonPrimitive(localization.locale.value))
                                            put("value", JsonPrimitive(localization.value))
                                        },
                                    )
                                }
                            },
                        )
                    },
                )
            }
        }.toString()

    private fun decodeCourseLessons(serialized: String): List<LearnerLessonSummary> =
        storageJson.parseToJsonElement(serialized).jsonArray.map { element ->
            val lesson = element.jsonObject
            LearnerLessonSummary(
                id = ContentId(lesson.requiredText("id")),
                title = lesson.requiredText("title"),
                sourceLocale = LanguageTag(lesson.requiredText("sourceLocale")),
                publishedVersion = lesson.requiredText("publishedVersion").toIntOrNull()
                    ?: error("Downloaded lesson published version is not an integer."),
                localizations =
                    lesson["localizations"]?.jsonArray?.map { localizationElement ->
                        val localization = localizationElement.jsonObject
                        LearnerLocalizedText(
                            locale = LanguageTag(localization.requiredText("locale")),
                            value = localization.requiredText("value"),
                        )
                    }.orEmpty(),
            )
        }

    private fun JsonObject.requiredText(name: String): String =
        get(name)?.jsonPrimitive?.content?.takeIf { it.isNotBlank() }
            ?: error("Downloaded lesson metadata is missing '$name'.")

    private fun DownloadedLesson.position(): Int =
        course.lessons.indexOfFirst { it.id == lesson.id }.also {
            check(it >= 0) {
                "Downloaded lesson must be present in its course."
            }
        }

    private companion object {
        const val MANUAL_INPUT = "MANUAL_INPUT"
        val storageJson = Json { ignoreUnknownKeys = false }
    }
}
