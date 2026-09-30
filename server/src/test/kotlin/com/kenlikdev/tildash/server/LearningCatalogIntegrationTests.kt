package com.kenlikdev.tildash.server

import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.sql.Timestamp
import java.time.Instant
import java.util.UUID

@SpringBootTest
@org.springframework.context.annotation.Import(PostgresTestConfiguration::class)
class LearningCatalogIntegrationTests {
    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var jdbcTemplate: JdbcTemplate

    @Test
    fun learnerReceivesPublishedCatalogWithStableProjection() {
        val courseId = uuid("00000000-0000-4000-8000-000000000001")
        val lessonA = uuid("00000000-0000-4000-8000-000000000002")
        val lessonB = uuid("00000000-0000-4000-8000-000000000003")
        val draftLesson = uuid("00000000-0000-4000-8000-000000000004")

        insertNode(courseId, "COURSE", null, 0)
        insertNode(lessonB, "LESSON", courseId, 1)
        insertNode(lessonA, "LESSON", courseId, 0)
        insertNode(draftLesson, "LESSON", courseId, 2)

        publish(courseId, 1, "Course")
        publish(lessonB, 1, "Lesson B")
        publish(lessonA, 1, "Old lesson A")
        publish(lessonA, 2, "Lesson A")

        insertPublishedLocalization(
            publishedVersionId = latestPublishedVersionId(lessonA),
            locale = "ru",
            variant = "LITERARY",
            revision = 1,
            payload = """{"value":"Урок A"}""",
        )

        mockMvc
            .perform(
                get("/api/v1/learning/catalog")
                    .with(user("learner").roles("LEARNER"))
                    .accept(MediaType.APPLICATION_JSON),
            ).andExpect(status().isOk)
            .andExpect(jsonPath("$.courses.length()").value(1))
            .andExpect(jsonPath("$.courses[0].id").value(courseId.toString()))
            .andExpect(jsonPath("$.courses[0].title").value("Course"))
            .andExpect(jsonPath("$.courses[0].lessons.length()").value(2))
            .andExpect(jsonPath("$.courses[0].lessons[0].id").value(lessonA.toString()))
            .andExpect(jsonPath("$.courses[0].lessons[0].title").value("Lesson A"))
            .andExpect(jsonPath("$.courses[0].lessons[0].publishedVersion").value(2))
            .andExpect(jsonPath("$.courses[0].lessons[0].localizations[0].locale").value("ru"))
            .andExpect(jsonPath("$.courses[0].lessons[0].localizations[0].value").value("Урок A"))
            .andExpect(jsonPath("$.courses[0].lessons[1].id").value(lessonB.toString()))
            .andExpect(jsonPath("$.courses[0].lessons[1].title").value("Lesson B"))
    }

    @Test
    fun catalogRequiresLearnerRole() {
        mockMvc
            .perform(
                get("/api/v1/learning/catalog")
                    .accept(MediaType.APPLICATION_PROBLEM_JSON),
            ).andExpect(status().isUnauthorized())

        mockMvc
            .perform(
                get("/api/v1/learning/catalog")
                    .with(user("teacher").roles("TEACHER"))
                    .accept(MediaType.APPLICATION_PROBLEM_JSON),
            ).andExpect(status().isForbidden())
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
    ) {
        val provenanceId = UUID.randomUUID()
        val sourceRevisionId = UUID.randomUUID()
        val publishedVersionId = UUID.randomUUID()
        val now = Timestamp.from(Instant.parse("2026-09-30T10:05:00Z"))

        jdbcTemplate.update(
            """
            insert into tildash.content_provenance (
                id, source_title, copyright_status
            )
            values (?::uuid, ?, 'PUBLIC_DOMAIN')
            """.trimIndent(),
            provenanceId,
            "Published integration test source",
        )

        jdbcTemplate.update(
            """
            insert into tildash.content_source_revisions (
                id, content_node_id, revision_no, payload, provenance_id, created_by, created_at
            )
            values (?::uuid, ?::uuid, ?, CAST(? AS jsonb), ?::uuid, ?, ?)
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
    }

    private fun insertPublishedLocalization(
        publishedVersionId: UUID,
        locale: String,
        variant: String,
        revision: Int,
        payload: String,
    ) {
        val localizationId = UUID.randomUUID()
        val localizationRevisionId = UUID.randomUUID()
        val contentId =
            jdbcTemplate.queryForObject(
                "select content_node_id from tildash.content_published_versions where id = ?::uuid",
                UUID::class.java,
                publishedVersionId,
            )
        jdbcTemplate.update(
            """
            insert into tildash.content_localization_revisions (
                id, content_node_id, locale, revision_no, variant_type, payload,
                derived_from_source_revision, state, created_by, created_at
            )
            values (?::uuid, ?::uuid, ?, ?, ?, CAST(? AS jsonb), 1, 'PUBLISHED', ?, ?)
            """.trimIndent(),
            localizationRevisionId,
            contentId,
            locale,
            revision,
            variant,
            payload,
            "integration-test",
            Timestamp.from(Instant.parse("2026-09-30T10:06:00Z")),
        )

        jdbcTemplate.update(
            """
            insert into tildash.content_published_localizations (
                id, published_version_id, locale, variant_type, revision_no, payload, localization_revision_id
            )
            values (?::uuid, ?::uuid, ?, ?, ?, CAST(? AS jsonb), ?::uuid)
            """.trimIndent(),
            localizationId,
            publishedVersionId,
            locale,
            variant,
            revision,
            payload,
            localizationRevisionId,
        )
    }

    private fun latestPublishedVersionId(contentId: UUID): UUID =
        requireNotNull(
            jdbcTemplate.queryForObject(
                """
                select id
                from tildash.content_published_versions
                where content_node_id = ?::uuid
                order by version_no desc
                limit 1
                """.trimIndent(),
                UUID::class.java,
                contentId,
            ),
        )

    private fun uuid(value: String): UUID = UUID.fromString(value)
}
