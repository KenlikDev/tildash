package com.kenlikdev.tildash.server.content

import com.kenlikdev.tildash.content.model.ContentId
import com.kenlikdev.tildash.content.validation.ExerciseDefinition
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Repository
import tools.jackson.databind.ObjectMapper
import java.util.UUID

@Repository
class ContentExerciseRepository(
    private val jdbc: NamedParameterJdbcTemplate,
    private val objectMapper: ObjectMapper,
) {
    fun list(contentId: ContentId): List<StoredExerciseDefinition> =
        jdbc.query(
            """
            select content_node_id, exercise_id, position, prompt, expected_answers
            from tildash.content_exercise_definitions
            where content_node_id = :contentId
            order by position, exercise_id
            """.trimIndent(),
            MapSqlParameterSource(
                "contentId",
                UUID.fromString(contentId.value),
            ),
        ) { rs, _ ->
            val exerciseId = rs.getString("exercise_id")
            StoredExerciseDefinition(
                id = exerciseId,
                contentId = contentId,
                position = rs.getInt("position"),
                definition =
                    ExerciseDefinition(
                        id = exerciseId,
                        prompt = rs.getString("prompt"),
                        expectedAnswers =
                            buildList {
                                objectMapper.readTree(rs.getString("expected_answers")).forEach { add(it.asText()) }
                            },
                    ),
            )
        }

    fun insert(
        contentId: ContentId,
        id: String,
        position: Int,
        definition: ExerciseDefinition,
    ) {
        jdbc.update(
            """
            insert into tildash.content_exercise_definitions (
                content_node_id, exercise_id, position, exercise_type, prompt, expected_answers
            )
            values (
                :contentId, :exerciseId, :position, 'MANUAL_INPUT', :prompt, cast(:expectedAnswers as jsonb)
            )
            """.trimIndent(),
            MapSqlParameterSource()
                .addValue("contentId", UUID.fromString(contentId.value))
                .addValue("exerciseId", id)
                .addValue("position", position)
                .addValue("prompt", definition.prompt)
                .addValue("expectedAnswers", objectMapper.writeValueAsString(definition.expectedAnswers)),
        )
    }

    fun update(
        contentId: ContentId,
        id: String,
        position: Int,
        definition: ExerciseDefinition,
    ): Boolean =
        jdbc.update(
            """
            update tildash.content_exercise_definitions
            set position = :position,
                prompt = :prompt,
                expected_answers = cast(:expectedAnswers as jsonb),
                updated_at = current_timestamp
            where content_node_id = :contentId
              and exercise_id = :exerciseId
            """.trimIndent(),
            MapSqlParameterSource()
                .addValue("contentId", UUID.fromString(contentId.value))
                .addValue("exerciseId", id)
                .addValue("position", position)
                .addValue("prompt", definition.prompt)
                .addValue("expectedAnswers", objectMapper.writeValueAsString(definition.expectedAnswers)),
        ) == 1

    fun delete(
        contentId: ContentId,
        id: String,
    ): Boolean =
        jdbc.update(
            """
            delete from tildash.content_exercise_definitions
            where content_node_id = :contentId and exercise_id = :exerciseId
            """.trimIndent(),
            MapSqlParameterSource()
                .addValue("contentId", UUID.fromString(contentId.value))
                .addValue("exerciseId", id),
        ) == 1

    fun snapshotPublishedLesson(
        contentId: ContentId,
        version: Int,
    ) {
        val publishedVersionId =
            jdbc.queryForObject(
                """
                select id
                from tildash.content_published_versions
                where content_node_id = :contentId and version_no = :version
                """.trimIndent(),
                MapSqlParameterSource()
                    .addValue("contentId", UUID.fromString(contentId.value))
                    .addValue("version", version),
                UUID::class.java,
            ) ?: throw IllegalStateException(
                "Published content '" + contentId.value + "' version " + version + " was not found.",
            )

        jdbc.update(
            """
            with recursive descendants as (
                select id
                from tildash.content_nodes
                where id = :lessonId
                union all
                select child.id
                from tildash.content_nodes child
                join descendants parent on child.parent_id = parent.id
            )
            insert into tildash.content_published_exercises (
                published_version_id, content_node_id, exercise_id, position,
                exercise_type, prompt, expected_answers
            )
            select
                :publishedVersionId,
                e.content_node_id,
                e.exercise_id,
                e.position,
                e.exercise_type,
                e.prompt,
                e.expected_answers
            from tildash.content_exercise_definitions e
            join descendants d on d.id = e.content_node_id
            order by e.position, e.exercise_id
            """.trimIndent(),
            MapSqlParameterSource()
                .addValue("lessonId", UUID.fromString(contentId.value))
                .addValue("publishedVersionId", publishedVersionId),
        )

        jdbc.update(
            """
            with recursive descendants as (
                select id, kind, source_locale, position,
                       array[position]::integer[] as sort_path
                from tildash.content_nodes
                where id = :lessonId
                union all
                select child.id, child.kind, child.source_locale, child.position,
                       parent.sort_path || child.position
                from tildash.content_nodes child
                join descendants parent on child.parent_id = parent.id
            ),
            materials as (
                select d.id, d.kind, d.source_locale, d.sort_path, latest.payload
                from descendants d
                join lateral (
                    select revision.payload
                    from tildash.content_source_revisions revision
                    where revision.content_node_id = d.id
                    order by revision.revision_no desc
                    limit 1
                ) latest on true
                where d.id <> :lessonId
                  and d.kind in ('EXPLANATION', 'EXAMPLE', 'VOCABULARY')
            )
            insert into tildash.content_published_lesson_materials (
                published_version_id, lesson_content_node_id, material_content_node_id,
                kind, position, source_locale, payload
            )
            select
                :publishedVersionId,
                :lessonId,
                materials.id,
                materials.kind,
                (row_number() over (order by materials.sort_path, materials.id) - 1)::integer,
                materials.source_locale,
                materials.payload
            from materials
            order by materials.sort_path, materials.id
            """.trimIndent(),
            MapSqlParameterSource()
                .addValue("lessonId", UUID.fromString(contentId.value))
                .addValue("publishedVersionId", publishedVersionId),
        )
    }
}

data class StoredExerciseDefinition(
    val id: String,
    val contentId: ContentId,
    val position: Int,
    val definition: ExerciseDefinition,
)
