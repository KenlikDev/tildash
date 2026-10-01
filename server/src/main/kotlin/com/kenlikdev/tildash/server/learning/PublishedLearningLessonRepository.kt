package com.kenlikdev.tildash.server.learning

import com.kenlikdev.tildash.content.model.ContentId
import com.kenlikdev.tildash.learning.LearnerLessonPackage
import com.kenlikdev.tildash.learning.LearningCatalogProjector
import com.kenlikdev.tildash.learning.LearningPlan
import com.kenlikdev.tildash.learning.ManualInputExercise
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Repository
import tools.jackson.databind.ObjectMapper
import java.util.UUID

class PublishedLearningLessonNotFoundException(
    lessonId: ContentId,
) : RuntimeException("Published learner lesson '${lessonId.value}' was not found.")

class PublishedLearningLessonInvalidException(
    cause: Throwable? = null,
) : RuntimeException("Published learner lesson data is invalid.", cause)

@Repository
class PublishedLearningLessonRepository(
    private val catalogRepository: PublishedLearningCatalogRepository,
    private val jdbc: NamedParameterJdbcTemplate,
    private val objectMapper: ObjectMapper,
) {
    fun load(lessonId: ContentId): LearnerLessonPackage {
        val publishedCatalog = catalogRepository.load()
        val catalog =
            LearningCatalogProjector.project(
                nodes = publishedCatalog.nodes,
                publishedVersions = publishedCatalog.publishedVersions,
            )

        val course =
            catalog.courses.firstNotNullOfOrNull { candidate ->
                candidate.lessons.firstOrNull { it.id == lessonId }?.let { candidate }
            } ?: throw PublishedLearningLessonNotFoundException(lessonId)

        val lesson = course.lessons.first { it.id == lessonId }
        val versionId =
            jdbc.queryForObject(
                """
                select id
                from tildash.content_published_versions
                where content_node_id = :lessonId
                order by version_no desc
                limit 1
                """.trimIndent(),
                MapSqlParameterSource("lessonId", UUID.fromString(lessonId.value)),
                UUID::class.java,
            ) ?: throw PublishedLearningLessonNotFoundException(lessonId)

        val exercises =
            jdbc.query(
                """
                select exercise_id, content_node_id, exercise_type, prompt, expected_answers
                from tildash.content_published_exercises
                where published_version_id = :versionId
                order by position, exercise_id
                """.trimIndent(),
                MapSqlParameterSource("versionId", versionId),
            ) { rs, _ ->
                val exerciseId = rs.getString("exercise_id")
                val contentId =
                    ContentId(
                        rs.getObject("content_node_id", UUID::class.java).toString(),
                    )
                val type = rs.getString("exercise_type")
                if (type != "MANUAL_INPUT") {
                    throw PublishedLearningLessonInvalidException()
                }

                ManualInputExercise(
                    id = exerciseId,
                    contentId = contentId,
                    prompt = rs.getString("prompt"),
                    expectedAnswers =
                        buildList {
                            objectMapper.readTree(rs.getString("expected_answers")).forEach { add(it.asText()) }
                        },
                )
            }

        if (exercises.isEmpty()) {
            throw PublishedLearningLessonInvalidException()
        }

        return LearnerLessonPackage(
            course = course,
            lesson = lesson,
            plan =
                LearningPlan(
                    lessonId = lessonId,
                    exercises = exercises,
                ),
        )
    }
}
