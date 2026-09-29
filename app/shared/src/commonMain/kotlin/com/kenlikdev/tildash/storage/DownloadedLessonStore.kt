package com.kenlikdev.tildash.storage

import app.cash.sqldelight.db.SqlDriver
import com.kenlikdev.tildash.content.model.ContentId
import com.kenlikdev.tildash.content.model.LanguageTag
import com.kenlikdev.tildash.learning.LearnerCourseSummary
import com.kenlikdev.tildash.learning.LearnerLessonSummary
import com.kenlikdev.tildash.learning.LearningExercise
import com.kenlikdev.tildash.learning.LearningPlan
import com.kenlikdev.tildash.learning.ManualInputExercise
import kotlin.time.Instant

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

            queries.upsertDownloadedCourse(
                course_id = downloadedLesson.course.id.value,
                title = downloadedLesson.course.title,
                source_locale = downloadedLesson.course.sourceLocale.value,
                published_version = downloadedLesson.course.publishedVersion.toLong(),
                downloaded_at_epoch_millis = downloadedLesson.downloadedAt.toEpochMilliseconds(),
            )

            queries.insertDownloadedLesson(
                lesson_id = lessonId,
                course_id = downloadedLesson.course.id.value,
                title = downloadedLesson.lesson.title,
                source_locale = downloadedLesson.lesson.sourceLocale.value,
                published_version = downloadedLesson.lesson.publishedVersion.toLong(),
                position = downloadedLesson.position().toLong(),
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

        val courseLessonRows =
            queries.selectDownloadedLessonsForCourse(course.course_id).executeAsList()

        val lessonSummaries =
            courseLessonRows.map { row ->
                LearnerLessonSummary(
                    id = ContentId(row.lesson_id),
                    title = row.title,
                    sourceLocale = LanguageTag(row.source_locale),
                    publishedVersion = row.published_version.toInt(),
                    localizations = emptyList(),
                )
            }

        val lessonSummary =
            lessonSummaries.first { it.id.value == lessonRow.lesson_id }

        val exercises =
            queries.selectDownloadedExercises(lessonRow.lesson_id).executeAsList().map { row ->
                val answers =
                    queries.selectDownloadedManualAnswers(row.lesson_id)
                        .executeAsList()
                        .filter { it.exercise_id == row.exercise_id }
                        .sortedBy { it.position }
                        .map { it.answer }

                when (row.exercise_type) {
                    MANUAL_INPUT ->
                        ManualInputExercise(
                            id = row.exercise_id,
                            contentId = ContentId(row.content_id),
                            prompt = row.prompt,
                            expectedAnswers = answers,
                        )

                    else ->
                        error(
                            "Unsupported downloaded exercise type '" +
                                row.exercise_type +
                                "' for lesson '" +
                                lessonId +
                                "'.",
                        )
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
            downloadedAt = Instant.fromEpochMilliseconds(course.downloaded_at_epoch_millis),
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

            else ->
                error(
                    "Unsupported learning exercise type '" +
                        exercise::class.simpleName +
                        "'.",
                )
        }
    }

    private fun DownloadedLesson.position(): Int =
        course.lessons.indexOfFirst { it.id == lesson.id }.also {
            check(it >= 0) {
                "Downloaded lesson must be present in its course."
            }
        }

    private companion object {
        const val MANUAL_INPUT = "MANUAL_INPUT"
    }
}
