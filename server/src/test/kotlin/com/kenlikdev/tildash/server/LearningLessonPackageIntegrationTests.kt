package com.kenlikdev.tildash.server

import com.kenlikdev.tildash.content.model.ContentId
import java.sql.Timestamp
import java.time.Instant
import java.util.UUID
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.http.MediaType
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@SpringBootTest
@org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
@org.springframework.context.annotation.Import(PostgresTestConfiguration::class)
class LearningLessonPackageIntegrationTests {
    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var jdbcTemplate: JdbcTemplate

    @BeforeEach
    fun clearFixtures() {
        jdbcTemplate.execute("TRUNCATE tildash.content_nodes CASCADE")
    }

    @Test
    fun learnerReceivesImmutablePublishedLessonPackage() {
        val courseId = UUID.fromString("10000000-0000-4000-8000-000000000001")
        val lessonId = UUID.fromString("10000000-0000-4000-8000-000000000002")
        val exerciseContentId = UUID.fromString("10000000-0000-4000-8000-000000000003")

        insertNode(courseId, "COURSE", null, 0)
        insertNode(lessonId, "LESSON", courseId, 0)
        insertNode(exerciseContentId, "EXAMPLE", lessonId, 0)

        publish(courseId, 1, "Course")
        val lessonVersionId = publish(lessonId, 1, "Lesson")
        insertPublishedExercise(
            publishedVersionId = lessonVersionId,
            contentNodeId = lessonId,
            exerciseId = "exercise-1",
            prompt = "Translate hello.",
            expectedAnswers = """["merhaba"]""",
        )

        mockMvc
            .perform(
                get("/api/v1/learning/lessons/$lessonId")
                    .with(user("learner").roles("LEARNER"))
                    .accept(MediaType.APPLICATION_JSON),
            ).andExpect(status().isOk)
            .andExpect(jsonPath("$.course.id").value(courseId.toString()))
            .andExpect(jsonPath("$.lesson.id").value(lessonId.toString()))
            .andExpect(jsonPath("$.lesson.publishedVersion").value(1))
            .andExpect(jsonPath("$.exercises.length()").value(1))
            .andExpect(jsonPath("$.exercises[0].id").value("exercise-1"))
            .andExpect(jsonPath("$.exercises[0].type").value("MANUAL_INPUT"))
            .andExpect(jsonPath("$.exercises[0].prompt").value("Translate hello."))
            .andExpect(jsonPath("$.exercises[0].expectedAnswers[0]").value("merhaba"))

        jdbcTemplate.update(
            """
            insert into tildash.content_exercise_definitions (
                content_node_id, exercise_id, position, exercise_type, prompt, expected_answers
            )
            values (?::uuid, ?, 0, 'MANUAL_INPUT', ?, cast(? as jsonb))
            """.trimIndent(),
            lessonId,
            "exercise-1-draft",
            "Changed draft prompt",
            """["different"]""",
        )

        mockMvc
            .perform(
                get("/api/v1/learning/lessons/$lessonId")
                    .with(user("learner").roles("LEARNER"))
                    .accept(MediaType.APPLICATION_JSON),
            ).andExpect(status().isOk)
            .andExpect(jsonPath("$.exercises.length()").value(1))
            .andExpect(jsonPath("$.exercises[0].prompt").value("Translate hello."))
    }

    @Test
    fun lessonPackageRequiresLearnerRole() {
        val courseId = UUID.fromString("10000000-0000-4000-8000-000000000011")
        val lessonId = UUID.fromString("10000000-0000-4000-8000-000000000012")

        insertNode(courseId, "COURSE", null, 0)
        insertNode(lessonId, "LESSON", courseId, 0)
        publish(courseId, 1, "Course")
        val versionId = publish(lessonId, 1, "Lesson")
        insertPublishedExercise(
            publishedVersionId = versionId,
            contentNodeId = lessonId,
            exerciseId = "exercise-1",
            prompt = "Translate hello.",
            expectedAnswers = """["merhaba"]""",
        )

        mockMvc
            .perform(
                get("/api/v1/learning/lessons/$lessonId")
                    .accept(MediaType.APPLICATION_PROBLEM_JSON),
            ).andExpect(status().isUnauthorized())

        mockMvc
            .perform(
                get("/api/v1/learning/lessons/$lessonId")
                    .with(user("teacher").roles("TEACHER"))
                    .accept(MediaType.APPLICATION_PROBLEM_JSON),
            ).andExpect(status().isForbidden())
    }

    @Test
    fun unpublishedLessonIsNotVisible() {
        val courseId = UUID.fromString("10000000-0000-4000-8000-000000000021")
        val lessonId = UUID.fromString("10000000-0000-4000-8000-000000000022")

        insertNode(courseId, "COURSE", null, 0)
        insertNode(lessonId, "LESSON", courseId, 0)
        publish(courseId, 1, "Course")

        mockMvc
            .perform(
                get("/api/v1/learning/lessons/$lessonId")
                    .with(user("learner").roles("LEARNER"))
                    .accept(MediaType.APPLICATION_PROBLEM_JSON),
            ).andExpect(status().isNotFound())
    }

    private fun insertNode(
        id: UUID,
        kind: String,
        parentId: UUID?,
        position: Int,
    ) {
        jdbcTemplate.update(
            """
            insert into tildash.content_nodes (
                id, kind, parent_id, source_locale, state, position
            )
            values (?::uuid, ?, ?::uuid, ?, 'PUBLISHED', ?)
            """.trimIndent(),
            id,
            kind,
            parentId,
            "crh",
            position,
        )
    }

    private fun publish(
        contentId: UUID,
        version: Int,
        title: String,
    ): UUID {
        val provenanceId = UUID.randomUUID()
        val sourceRevisionId = UUID.randomUUID()
        val publishedVersionId = UUID.randomUUID()
        val now = Timestamp.from(Instant.parse("2026-10-01T08:00:00Z"))

        jdbcTemplate.update(
            """
            insert into tildash.content_provenance (
                id, source_title, copyright_status
            )
            values (?::uuid, ?, 'PUBLIC_DOMAIN')
            """.trimIndent(),
            provenanceId,
            "Published package test source",
        )

        jdbcTemplate.update(
            """
            insert into tildash.content_source_revisions (
                id, content_node_id, revision_no, payload, provenance_id, created_by, created_at
            )
            values (?::uuid, ?::uuid, ?, cast(? as jsonb), ?::uuid, ?, ?)
            """.trimIndent(),
            sourceRevisionId,
            contentId,
            version,
            """{"value":"$title"}""",
            provenanceId,
            "integration-test",
            now,
        )

        jdbcTemplate.update(
            """
            insert into tildash.content_published_versions (
                id, content_node_id, version_no, source_revision_id, provenance_id, published_by, published_at
            )
            values (?::uuid, ?::uuid, ?, ?::uuid, ?::uuid, ?, ?)
            """.trimIndent(),
            publishedVersionId,
            contentId,
            version,
            sourceRevisionId,
            provenanceId,
            "integration-reviewer",
            now,
        )

        return publishedVersionId
    }

    private fun insertPublishedExercise(
        publishedVersionId: UUID,
        contentNodeId: UUID,
        exerciseId: String,
        prompt: String,
        expectedAnswers: String,
    ) {
        jdbcTemplate.update(
            """
            insert into tildash.content_published_exercises (
                published_version_id, content_node_id, exercise_id, position,
                exercise_type, prompt, expected_answers
            )
            values (?::uuid, ?::uuid, ?, 0, 'MANUAL_INPUT', ?, cast(? as jsonb))
            """.trimIndent(),
            publishedVersionId,
            contentNodeId,
            exerciseId,
            prompt,
            expectedAnswers,
        )
    }
}
